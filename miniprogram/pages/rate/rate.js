const { post } = require('../../utils/request')

const HINTS = ['非常不满意', '不满意', '一般', '满意', '非常满意']

Page({
  data: { rating: 0, content: '', hints: HINTS },

  onLoad(options) {
    this.orderId = options.id
  },

  setRating(e) {
    this.setData({ rating: Number(e.currentTarget.dataset.n) })
  },

  onContent(e) {
    this.setData({ content: e.detail.value })
  },

  async onSubmit() {
    if (!this.data.rating) {
      wx.showToast({ title: '请先点击星星评分', icon: 'none' })
      return
    }
    try {
      await post(`/orders/${this.orderId}/evaluation`, {
        rating: this.data.rating,
        content: this.data.content.trim() || null,
      })
      wx.showToast({ title: '感谢您的评价', icon: 'success' })
      setTimeout(() => wx.navigateBack(), 600)
    } catch (e) { /* 已提示 */ }
  },
})
