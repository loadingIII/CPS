const { get } = require('../../utils/request')
const { STATUS_LABEL, STATUS_TAG, role, user } = require('../../utils/util')

function getGreeting() {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
}

function pad2(n) {
  return n < 10 ? '0' + n : '' + n
}

/** iOS 只认 yyyy/MM/dd HH:mm:ss，需要先把 ISO 串规范化 */
function toDate(iso) {
  if (!iso) return null
  const d = new Date(String(iso).replace(/-/g, '/').replace('T', ' '))
  return Number.isNaN(d.getTime()) ? null : d
}

/** 提交时间：今天 09:36 / 昨天 19:14 / 11-08 19:14 */
function submitText(iso) {
  const d = toDate(iso)
  if (!d) return ''
  const now = new Date()
  const hm = pad2(d.getHours()) + ':' + pad2(d.getMinutes())
  if (d.toDateString() === now.toDateString()) return '今天 ' + hm
  const yesterday = new Date(now.getTime() - 86400000)
  if (d.toDateString() === yesterday.toDateString()) return '昨天 ' + hm
  return pad2(d.getMonth() + 1) + '-' + pad2(d.getDate()) + ' ' + hm
}

/** 剩余时间：接单剩 25 分钟 / 处理剩 3 小时 / 处理已超时 */
function remainText(iso, label) {
  const d = toDate(iso)
  if (!d) return ''
  const diff = d.getTime() - Date.now()
  if (diff <= 0) return label + '已超时'
  const minutes = Math.floor(diff / 60000)
  const text = minutes >= 60 ? Math.floor(minutes / 60) + ' 小时' : Math.max(1, minutes) + ' 分钟'
  return label + '剩 ' + text
}

function decorateOrder(order) {
  const pending = order.status === 'PENDING'
  const dueText = pending ? remainText(order.assignExpiresAt, '接单') : remainText(order.dueAt, '处理')
  return Object.assign({}, order, {
    statusLabel: pending ? '待接单' : (STATUS_LABEL[order.status] || '处理中'),
    statusClass: STATUS_TAG[order.status] || 'tag-info',
    urgent: order.urgency === 'URGENT',
    overdue: !!order.isOverdue,
    timeText: submitText(order.submittedAt),
    dueText,
    dueWarn: dueText.indexOf('超时') >= 0 || (!!order.isOverdue && !pending),
  })
}

function buildProfile(me) {
  if (!me) return null
  const load = Number(me.currentLoad || 0)
  const limit = Number(me.loadLimit || 0)
  const skills = me.skills || []
  return {
    jobNo: me.jobNo || '',
    onDuty: !!me.onDuty,
    score: me.score == null ? '—' : Number(me.score).toFixed(1),
    load,
    limit,
    loadPercent: limit > 0 ? Math.min(100, Math.round((load / limit) * 100)) : 0,
    loadFull: limit > 0 && load >= limit,
    skills: skills.slice(0, 3),
    moreSkills: Math.max(0, skills.length - 3),
  }
}

function getSkillIcon(name) {
  const label = String(name || '')
  if (label.includes('水电')) return 'utility'
  if (/(网络|多媒体|通信)/.test(label)) return 'network'
  if (/(空调|家电|制冷|暖通)/.test(label)) return 'climate'
  if (/(电梯|升降)/.test(label)) return 'elevator'
  if (/(木工|门窗|锁具|家具)/.test(label)) return 'carpentry'
  if (/(实验|仪器|设备)/.test(label)) return 'equipment'
  if (/(水暖|给排水|供水)/.test(label)) return 'water'
  if (/(电路|电气|照明|电力)/.test(label)) return 'power'
  return 'repair'
}

Page({
  data: {
    role: 'REPORTER',
    userName: '',
    avatarChar: '修',
    greeting: '',
    loading: true,
    loadError: false,
    skillsLoading: false,
    skills: [],
    recent: [],
    profile: null,
    unread: 0,
    skeletons: [1, 2, 3],
    summary: { total: 0, pending: 0, processing: 0, completed: 0 },
  },

  onShow() {
    const tb = this.getTabBar && this.getTabBar()
    if (tb) { tb.init(); tb.setSelected('/pages/home/home') }

    if (!wx.getStorageSync('token')) {
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }

    this.stopCountdown()
    const currentRole = role() === 'REPAIRER' ? 'REPAIRER' : 'REPORTER'
    const currentUser = user()
    const name = currentUser.name || '同学'
    this.setData({
      role: currentRole,
      userName: name,
      avatarChar: String(name).slice(0, 1),
      greeting: getGreeting(),
      loading: true,
      loadError: false,
      skillsLoading: currentRole === 'REPORTER',
      profile: currentRole === 'REPAIRER' ? this.data.profile : null,
    })
    if (currentRole === 'REPAIRER') {
      this.loadRepairerHome()
      this.loadUnread()
      this.startCountdown()
    } else {
      this.loadReporterHome()
      this.loadUnread()
    }
  },

  onHide() {
    this.stopCountdown()
  },

  onUnload() {
    this.stopCountdown()
  },

  /** 页面停留时每分钟重算一次剩余时间，避免倒计时冻结 */
  startCountdown() {
    this.stopCountdown()
    this._timer = setInterval(() => {
      if (!this._rawRecent || !this._rawRecent.length) return
      this.setData({ recent: this._rawRecent.map(decorateOrder) })
    }, 60000)
  },

  stopCountdown() {
    if (this._timer) {
      clearInterval(this._timer)
      this._timer = null
    }
  },

  onPullDownRefresh() {
    this.setData({ loadError: false })
    const task = this.data.role === 'REPAIRER'
      ? Promise.all([this.loadRepairerHome(), this.loadUnread()])
      : Promise.all([this.loadReporterHome(), this.loadUnread()])
    Promise.resolve(task).finally(() => wx.stopPullDownRefresh())
  },

  /** 未读通知数：接口不可用时静默，不显示角标 */
  async loadUnread() {
    try {
      const n = await get('/notifications/unread-count')
      this.setData({ unread: Number(n) || 0 })
    } catch (e) {
      this.setData({ unread: 0 })
    }
  },

  async loadReporterHome() {
    try {
      const skills = await get('/skills')
      const tones = ['teal', 'butter', 'sky', 'blush', 'teal', 'paper']
      this.setData({
        skills: (skills || []).slice(0, 6).map((item, index) => Object.assign({}, item, {
          tone: tones[index % tones.length],
          icon: getSkillIcon(item.name),
        })),
        skillsLoading: false,
        loading: false,
      })
    } catch (e) {
      this.setData({ skills: [], skillsLoading: false, loading: false, loadError: true })
    }
  },

  async loadRepairerHome() {
    try {
      const [me, pending, processing, completed] = await Promise.all([
        get('/workers/me').catch(() => null),
        get('/orders/mine-assigned?status=PENDING&page=1&pageSize=3'),
        get('/orders/mine-assigned?status=PROCESSING&page=1&pageSize=3'),
        get('/orders/mine-assigned?status=COMPLETED&page=1&pageSize=1'),
      ])
      const merged = (pending.list || []).concat(processing.list || [])
      // 加急优先，其次按提交时间先来先做
      merged.sort((a, b) => {
        const ua = a.urgency === 'URGENT' ? 0 : 1
        const ub = b.urgency === 'URGENT' ? 0 : 1
        if (ua !== ub) return ua - ub
        return String(a.submittedAt || '').localeCompare(String(b.submittedAt || ''))
      })
      this._rawRecent = merged.slice(0, 3)
      this.setData({
        profile: buildProfile(me),
        summary: {
          total: 0,
          pending: pending.total || 0,
          processing: processing.total || 0,
          completed: completed.total || 0,
        },
        recent: this._rawRecent.map(decorateOrder),
        loading: false,
      })
    } catch (e) {
      this.setData({ loading: false, loadError: true })
    }
  },

  goReport() {
    getApp().globalData.reportView = 'form'
    wx.switchTab({ url: '/pages/report/report' })
  },

  onSkill(e) {
    const item = this.data.skills[e.currentTarget.dataset.index]
    if (!item) return this.goReport()
    getApp().globalData.homeFaultType = item.name
    this.goReport()
  },

  goAll() {
    getApp().globalData.reportView = 'orders'
    wx.switchTab({ url: '/pages/report/report' })
  },

  goWorkerOrders(e) {
    const status = e && e.currentTarget && e.currentTarget.dataset.status
    if (status) getApp().globalData.homeWorkerStatus = status
    wx.switchTab({ url: '/pages/worker-orders/worker-orders' })
  },

  goMap() {
    wx.switchTab({ url: '/pages/worker-map/worker-map' })
  },

  goMessages() {
    wx.navigateTo({ url: '/pages/messages/messages' })
  },

  goTicket(e) {
    const id = e.currentTarget.dataset.id
    const path = this.data.role === 'REPAIRER' ? 'worker-detail' : 'order-detail'
    wx.navigateTo({ url: `/pages/${path}/${path}?id=${id}` })
  },

  onRetry() {
    this.setData({ loading: true, loadError: false })
    if (this.data.role === 'REPAIRER') this.loadRepairerHome()
    else this.loadReporterHome()
  },
})
