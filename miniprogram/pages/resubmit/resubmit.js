const { get, post, upload, BASE } = require('../../utils/request')

Page({
  data: { BASE, rejectReason: '', description: '', images: [] },

  onLoad(options) {
    this.orderId = options.id
    this.load()
  },

  async load() {
    const detail = await get('/orders/' + this.orderId)
    this.setData({ description: detail.description })
    const logs = await get(`/orders/${this.orderId}/logs`)
    const rejectLog = logs.slice().reverse().find(l => l.action === '驳回')
    this.setData({ rejectReason: rejectLog ? rejectLog.remark : '' })
  },

  onDesc(e) { this.setData({ description: e.detail.value }) },

  chooseImage() {
    const remain = 3 - this.data.images.length
    wx.chooseMedia({
      count: remain, mediaType: ['image'], sizeType: ['compressed'],
      success: async res => {
        wx.showLoading({ title: '上传中' })
        try {
          for (const f of res.tempFiles) {
            const url = await upload(f.tempFilePath)
            this.setData({ images: this.data.images.concat(url) })
          }
        } finally { wx.hideLoading() }
      },
    })
  },

  deleteImage(e) {
    const images = this.data.images.slice()
    images.splice(e.currentTarget.dataset.idx, 1)
    this.setData({ images })
  },

  noop() {},

  async onSubmit() {
    const { description, images } = this.data
    if (description.trim().length < 5) { wx.showToast({ title: '描述至少 5 个字', icon: 'none' }); return }
    if (!images.length) { wx.showToast({ title: '请至少上传 1 张图片', icon: 'none' }); return }
    try {
      await post(`/orders/${this.orderId}/resubmit`, { description: description.trim(), imageUrls: images })
      wx.showToast({ title: '已重新提交审核', icon: 'success' })
      setTimeout(() => wx.navigateBack(), 600)
    } catch (e) { /* 已提示 */ }
  },
})
