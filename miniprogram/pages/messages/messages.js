const { get, put } = require('../../utils/request')

Page({
  data: { list: [], unreadOnly: false, loading: false },

  onShow() {
    this.load()
  },

  onPullDownRefresh() {
    this.load().finally(() => wx.stopPullDownRefresh())
  },

  onTab(e) {
    const unreadOnly = e.currentTarget.dataset.unread === '1'
    this.setData({ unreadOnly, list: [] })
    this.load()
  },

  async load() {
    this.setData({ loading: true })
    try {
      const d = await get(`/notifications?page=1&pageSize=50&unread=${this.data.unreadOnly}`)
      const list = (d.list || []).map(n => Object.assign({}, n, {
        typeLabel: {
          AUDIT_PASS: '审核通过', AUDIT_REJECT: '驳回', ASSIGN: '新派单', ACCEPT: '已接单',
          REJECT: '拒单/转单', FINISH: '已完工', RATE: '收到评价', URGE: '催单',
          OVERDUE: '超时预警', REVISIT: '回访任务', MERGE: '合并通知',
        }[n.type] || '通知',
      }))
      this.setData({ list })
    } finally {
      this.setData({ loading: false })
    }
  },

  async onTap(e) {
    const item = this.data.list[e.currentTarget.dataset.idx]
    if (!item) return
    if (!item.isRead) {
      try {
        await put(`/notifications/${item.id}/read`)
        item.isRead = true
        this.setData({ list: this.data.list })
      } catch (err) { /* 已提示 */ }
    }
    if (!item.bizId) return
    const user = wx.getStorageSync('user') || {}
    const base = user.role === 'REPAIRER' ? '/pages/worker-detail/worker-detail?id=' : '/pages/order-detail/order-detail?id='
    wx.navigateTo({ url: base + item.bizId })
  },

  async readAll() {
    await put('/notifications/read-all')
    this.setData({ list: this.data.list.map(n => Object.assign({}, n, { isRead: true })) })
    wx.showToast({ title: '已全部已读', icon: 'none' })
  },
})
