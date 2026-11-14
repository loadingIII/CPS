const { post } = require('../../utils/request')

const HINTS = ['非常不满意', '不满意', '一般', '满意', '非常满意']
const TAGS = ['速度快', '态度好', '专业', '需改进']

Page({
  data: { rating: 0, content: '', hints: HINTS, tags: TAGS, picked: [] },

  onLoad(options) {
    this.orderId = options.id
  },

  setRating(e) {
    this.setData({ rating: Number(e.currentTarget.dataset.n) })
  },

  onContent(e) {
    this.setData({ content: e.detail.value })
  },

  /** R2-F7-2：评价标签，最多选 4 个，可再次点击取消 */
  toggleTag(e) {
    const tag = e.currentTarget.dataset.tag
    const picked = this.data.picked.slice()
    const idx = picked.indexOf(tag)
    if (idx >= 0) {
      picked.splice(idx, 1)
    } else {
      if (picked.length >= 4) {
        wx.showToast({ title: '最多选 4 个标签', icon: 'none' })
        return
      }
      picked.push(tag)
    }
    this.setData({ picked })
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
        tags: this.data.picked,
      })
      wx.showToast({ title: '感谢您的评价', icon: 'success' })
      setTimeout(() => wx.navigateBack(), 600)
    } catch (e) { /* 已提示 */ }
  },
})
