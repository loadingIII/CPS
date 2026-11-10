const { get, post } = require('../../utils/request')

Page({
  data: { status: 'PENDING', list: [], header: null },

  onShow() {
    const tb = this.getTabBar && this.getTabBar()
    if (tb) { tb.init(); tb.setSelected('/pages/worker-orders/worker-orders') }
    const app = getApp()
    const homeStatus = app.globalData.homeWorkerStatus || ''
    const currentStatus = homeStatus || this.data.status
    if (homeStatus) {
      app.globalData.homeWorkerStatus = ''
      this.setData({ status: homeStatus, list: [] })
    }
    this.loadHeader()
    if (currentStatus === 'GRAB') this.loadGrab()
    else this.load()
  },

  onPullDownRefresh() {
    const p = this.data.status === 'GRAB' ? this.loadGrab() : this.load()
    Promise.all([this.loadHeader(), p]).finally(() => wx.stopPullDownRefresh())
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
    if (v === 'GRAB') {
      this.loadGrab()
    } else {
      this.load()
    }
  },

  async load() {
    const d = await get(`/orders/mine-assigned?status=${this.data.status}&page=1&pageSize=20`)
    this.setData({ list: d.list })
  },

  /** 抢单大厅：经硬约束过滤后、我有资格承接的未派单工单 */
  async loadGrab() {
    const d = await get('/orders/grab-pool?page=1&pageSize=50')
    this.setData({ list: d.list })
  },

  onGrab(e) {
    const id = e.currentTarget.dataset.id
    wx.showModal({
      title: '确认抢单',
      content: '抢单成功后工单将派给你，需在「待接单」中确认接单',
      confirmColor: '#D96A3B',
      success: async r => {
        if (!r.confirm) return
        try {
          await post(`/orders/${id}/claim`)
          wx.showToast({ title: '抢单成功', icon: 'success' })
          this.setData({ status: 'PENDING', list: [] })
          this.loadHeader()
          this.load()
        } catch (err) { /* 拦截器已提示（含"已被其他师傅抢走"） */ }
      },
    })
  },

  goDetail(e) {
    wx.navigateTo({ url: '/pages/worker-detail/worker-detail?id=' + e.currentTarget.dataset.id })
  },
})
