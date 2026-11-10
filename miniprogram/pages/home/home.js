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

function decorateOrder(order, repairer) {
  const statusLabel = repairer && order.status === 'PENDING'
    ? '待接单'
    : (STATUS_LABEL[order.status] || '处理中')
  return Object.assign({}, order, {
    statusLabel,
    statusClass: STATUS_TAG[order.status] || 'tag-info',
  })
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
    greeting: '',
    loading: true,
    loadError: false,
    skillsLoading: false,
    skills: [],
    recent: [],
    skeletons: [1, 2, 3],
    worker: null,
    summary: { total: 0, pending: 0, processing: 0, grab: 0 },
  },

  onShow() {
    const tb = this.getTabBar && this.getTabBar()
    if (tb) { tb.init(); tb.setSelected('/pages/home/home') }

    if (!wx.getStorageSync('token')) {
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }

    const currentRole = role() === 'REPAIRER' ? 'REPAIRER' : 'REPORTER'
    const currentUser = user()
    const name = currentUser.name || '同学'
    this.setData({
      role: currentRole,
      userName: name,
      greeting: getGreeting(),
      loading: true,
      loadError: false,
      skillsLoading: currentRole === 'REPORTER',
    })
    if (currentRole === 'REPAIRER') this.loadRepairerHome()
    else this.loadReporterHome()
  },

  onPullDownRefresh() {
    this.setData({ loadError: false })
    const task = this.data.role === 'REPAIRER' ? this.loadRepairerHome() : this.loadReporterHome()
    Promise.resolve(task).finally(() => wx.stopPullDownRefresh())
  },

  async loadReporterHome() {
    try {
      const [all, audit, pending, processing, recent] = await Promise.all([
        get('/orders/my?page=1&pageSize=1'),
        get('/orders/my?status=PENDING_AUDIT&page=1&pageSize=1'),
        get('/orders/my?status=PENDING&page=1&pageSize=1'),
        get('/orders/my?status=PROCESSING&page=1&pageSize=1'),
        get('/orders/my?page=1&pageSize=3'),
      ])
      this.setData({
        summary: {
          total: all.total || 0,
          pending: (audit.total || 0) + (pending.total || 0),
          processing: processing.total || 0,
          grab: 0,
        },
        recent: (recent.list || []).map(decorateOrder),
        loading: false,
      })
    } catch (e) {
      this.setData({ loading: false, loadError: true })
    }

    try {
      const skills = await get('/skills')
      const tones = ['teal', 'butter', 'sky', 'blush', 'teal', 'paper']
      this.setData({
        skills: (skills || []).slice(0, 6).map((item, index) => Object.assign({}, item, {
          tone: tones[index % tones.length],
          icon: getSkillIcon(item.name),
        })),
        skillsLoading: false,
      })
    } catch (e) {
      this.setData({ skills: [], skillsLoading: false })
    }
  },

  async loadRepairerHome() {
    try {
      const [worker, pending, processing, grab] = await Promise.all([
        get('/workers/me'),
        get('/orders/mine-assigned?status=PENDING&page=1&pageSize=3'),
        get('/orders/mine-assigned?status=PROCESSING&page=1&pageSize=3'),
        get('/orders/grab-pool?page=1&pageSize=1'),
      ])
      const recent = (pending.list || []).concat(processing.list || [])
        .sort((a, b) => String(b.submittedAt || '').localeCompare(String(a.submittedAt || '')))
        .slice(0, 3)
        .map(item => decorateOrder(item, true))
      const limit = Number(worker.loadLimit) || 0
      const currentLoad = Number(worker.currentLoad) || 0
      this.setData({
        worker: {
          name: worker.name || this.data.userName,
          score: worker.score == null ? '—' : worker.score,
          currentLoad,
          loadLimit: limit,
          loadPercent: limit > 0 ? Math.min(100, Math.round(currentLoad / limit * 100)) : 0,
          skillText: (worker.skills || []).slice(0, 3).join(' / ') || '待配置',
        },
        summary: {
          total: 0,
          pending: pending.total || 0,
          processing: processing.total || 0,
          grab: grab.total || 0,
        },
        recent,
        loading: false,
      })
    } catch (e) {
      this.setData({ loading: false, loadError: true, worker: null })
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
