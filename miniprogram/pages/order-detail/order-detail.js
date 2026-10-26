const { get, post, BASE } = require('../../utils/request')
const { STATUS_LABEL, STATUS_TAG } = require('../../utils/util')

Page({
  data: { BASE, detail: null, logs: [], STATUS_LABEL, STATUS_TAG },

  onLoad(options) {
    this.orderId = options.id
  },

  onShow() {
    this.load()
  },

  async load() {
    const detail = await get('/orders/' + this.orderId)
    if (detail.evaluation) {
      const n = detail.evaluation.rating
      detail.starsText = '★'.repeat(n) + '☆'.repeat(5 - n)
    }
    const logs = await get(`/orders/${this.orderId}/logs`)
    this.setData({ detail, logs })
  },

  preview(e) {
    const { urls, idx } = e.currentTarget.dataset
    wx.previewImage({ current: BASE + urls[idx], urls: urls.map(u => BASE + u) })
  },

  callWorker() {
    if (this.data.detail.workerPhone) {
      wx.makePhoneCall({ phoneNumber: this.data.detail.workerPhone })
    }
  },

  /** 查看报修位置地图 */
  openMap() {
    const d = this.data.detail
    wx.openLocation({
      latitude: d.locationLat,
      longitude: d.locationLng,
      name: d.locationText,
      address: d.locationText,
      scale: 17,
    })
  },

  onCancel() {
    wx.showModal({
      title: '取消报修',
      content: '确认取消这张工单吗？',
      success: async r => {
        if (!r.confirm) return
        try {
          await post(`/orders/${this.orderId}/cancel`)
          wx.showToast({ title: '已取消', icon: 'none' })
          this.load()
        } catch (e) { /* 已提示 */ }
      },
    })
  },

  onAbandon() {
    wx.showModal({
      title: '放弃',
      content: '确认放弃这张已驳回的工单吗？放弃后不可恢复。',
      success: async r => {
        if (!r.confirm) return
        try {
          await post(`/orders/${this.orderId}/abandon`)
          wx.showToast({ title: '已放弃', icon: 'none' })
          this.load()
        } catch (e) { /* 已提示 */ }
      },
    })
  },

  goResubmit() {
    wx.navigateTo({ url: '/pages/resubmit/resubmit?id=' + this.orderId })
  },

  goRate() {
    wx.navigateTo({ url: '/pages/rate/rate?id=' + this.orderId })
  },
})
