const { get } = require('../../utils/request')

/** 接单剩余时间（分钟），超时返回空；iOS 需把 ISO 串转成 yyyy/MM/dd HH:mm:ss */
function remainText(iso) {
  if (!iso) return ''
  const normalized = String(iso).replace(/-/g, '/').replace('T', ' ')
  const diff = new Date(normalized).getTime() - Date.now()
  if (Number.isNaN(diff)) return ''
  if (diff <= 0) return '已超时'
  const m = Math.floor(diff / 60000)
  return m >= 60 ? `${Math.floor(m / 60)} 小时` : `${m} 分钟`
}

/** 处理剩余时间（处理 SLA），超时返回「已超时」 */
function dueText(iso) {
  if (!iso) return ''
  const normalized = String(iso).replace(/-/g, '/').replace('T', ' ')
  const diff = new Date(normalized).getTime() - Date.now()
  if (Number.isNaN(diff)) return ''
  if (diff <= 0) return '已超时'
  const h = Math.floor(diff / 3600000)
  if (h >= 1) return `剩 ${h} 小时`
  return `剩 ${Math.max(1, Math.floor(diff / 60000))} 分钟`
}

Page({
  data: { status: 'PENDING', list: [], header: null },

  onShow() {
    const tb = this.getTabBar && this.getTabBar()
    if (tb) { tb.init(); tb.setSelected('/pages/worker-orders/worker-orders') }
    const app = getApp()
    const homeStatus = app.globalData.homeWorkerStatus || ''
    if (homeStatus) {
      app.globalData.homeWorkerStatus = ''
      this.setData({ status: homeStatus, list: [] })
    }
    this.loadHeader()
    this.load()
    this.startCountdown()
  },

  onHide() {
    this.stopCountdown()
  },

  onUnload() {
    this.stopCountdown()
  },

  /** 页面停留时每分钟刷新一次剩余时间，避免倒计时冻结 */
  startCountdown() {
    this.stopCountdown()
    this._timer = setInterval(() => this.refreshCountdown(), 60000)
  },

  stopCountdown() {
    if (this._timer) {
      clearInterval(this._timer)
      this._timer = null
    }
  },

  refreshCountdown() {
    if (!this.data.list.length) return
    const list = this.data.list.map(item => Object.assign({}, item, {
      remainText: remainText(item.assignExpiresAt),
      dueText: this.data.status === 'PROCESSING' ? dueText(item.dueAt) : '',
    }))
    this.setData({ list })
  },

  onPullDownRefresh() {
    Promise.all([this.loadHeader(), this.load()]).finally(() => wx.stopPullDownRefresh())
  },

  /** 绩效头卡：本人档案 + 待接/在办数量（接口不可用时静默隐藏） */
  async loadHeader() {
    try {
      const me = await get('/workers/me')
      const [p, pr] = await Promise.all([
        get('/orders/mine-assigned?status=PENDING&page=1&pageSize=1'),
        get('/orders/mine-assigned?status=PROCESSING&page=1&pageSize=1'),
      ])
      this.setData({
        header: {
          name: me.name,
          score: me.score,
          load: me.currentLoad,
          limit: me.loadLimit,
          skills: me.skills || [],
          pending: p.total,
          processing: pr.total,
        },
      })
    } catch (e) {
      this.setData({ header: null })
    }
  },

  onTab(e) {
    const v = e.currentTarget.dataset.v
    this.setData({ status: v, list: [] })
    this.load()
  },

  async load() {
    const d = await get(`/orders/mine-assigned?status=${this.data.status}&page=1&pageSize=20`)
    const list = (d.list || []).map(item => Object.assign({}, item, {
      remainText: remainText(item.assignExpiresAt),
      dueText: this.data.status === 'PROCESSING' ? dueText(item.dueAt) : '',
    }))
    this.setData({ list })
  },

  goDetail(e) {
    wx.navigateTo({ url: '/pages/worker-detail/worker-detail?id=' + e.currentTarget.dataset.id })
  },
})
