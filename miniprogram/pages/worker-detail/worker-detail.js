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

  callReporter() {
    if (this.data.detail.reporterPhone) {
      wx.makePhoneCall({ phoneNumber: this.data.detail.reporterPhone })
    }
  },

  /** 唤起微信内置地图，可直接跳转导航 */
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

  onGrab() {
    wx.showModal({
      title: '确认抢单',
      content: '抢单成功即接单，工单直接进入「在办」，请尽快联系报修人上门',
      confirmColor: '#0F7B7E',
      success: async r => {
        if (!r.confirm) return
        try {
          await post(`/orders/${this.orderId}/claim`)
          wx.showToast({ title: '抢单成功', icon: 'success' })
          this.load()
        } catch (e) { /* 已提示（含手慢了/负载已满） */ }
      },
    })
  },

  onAccept() {
    wx.showModal({
      title: '确认接单',
      content: '接单后请尽快联系报修人上门处理',
      success: async r => {
        if (!r.confirm) return
        try {
          await post(`/orders/${this.orderId}/accept`)
          wx.showToast({ title: '接单成功', icon: 'success' })
          this.load()
        } catch (e) { /* 已提示（含已被接单冲突） */ }
      },
    })
  },

  onReject() {
    wx.showModal({
      title: '拒单（必填理由）',
      editable: true,
      placeholderText: '如：时间冲突 / 技能不符',
      success: async r => {
        if (!r.confirm) return
        if (!r.content || !r.content.trim()) {
          wx.showToast({ title: '拒单必须填写理由', icon: 'none' })
          return
        }
        try {
          await post(`/orders/${this.orderId}/reject-assign`, { reason: r.content.trim() })
          wx.showToast({ title: '已拒单，工单回池', icon: 'none' })
          setTimeout(() => wx.navigateBack(), 600)
        } catch (e) { /* 已提示 */ }
      },
    })
  },

  onTransfer() {
    wx.showModal({
      title: '转单（必填理由）',
      editable: true,
      placeholderText: '如：需专业设备处理',
      success: async r => {
        if (!r.confirm) return
        if (!r.content || !r.content.trim()) {
          wx.showToast({ title: '转单必须填写理由', icon: 'none' })
          return
        }
        try {
          await post(`/orders/${this.orderId}/transfer`, { reason: r.content.trim() })
          wx.showToast({ title: '已转单', icon: 'none' })
          setTimeout(() => wx.navigateBack(), 600)
        } catch (e) { /* 已提示 */ }
      },
    })
  },

  goFinish() {
    wx.navigateTo({ url: '/pages/worker-finish/worker-finish?id=' + this.orderId })
  },
})
