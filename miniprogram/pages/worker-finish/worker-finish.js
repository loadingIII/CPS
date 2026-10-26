const { post, upload, BASE } = require('../../utils/request')

Page({
  data: { BASE, summary: '', images: [] },

  onLoad(options) {
    this.orderId = options.id
  },

  onSummary(e) { this.setData({ summary: e.detail.value }) },

  chooseImage() {
    const remain = 3 - this.data.images.length
    wx.chooseMedia({
      count: remain, mediaType: ['image'], sizeType: ['compressed'],
      sourceType: ['camera', 'album'],
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

  async onSubmit() {
    const { summary, images } = this.data
    if (summary.trim().length < 10) { wx.showToast({ title: '维修说明至少 10 个字', icon: 'none' }); return }
    if (!images.length) { wx.showToast({ title: '请至少上传 1 张完工照片', icon: 'none' }); return }
    try {
      await post(`/orders/${this.orderId}/finish`, {
        summary: summary.trim(),
        finishImageUrls: images,
      })
      wx.showToast({ title: '完工上报成功', icon: 'success' })
      setTimeout(() => wx.navigateBack(), 600)
    } catch (e) { /* 已提示 */ }
  },
})
