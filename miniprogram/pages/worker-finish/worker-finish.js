const { post, upload, BASE } = require('../../utils/request')

Page({
  data: { BASE, summary: '', images: [], parts: [] },

  onLoad(options) {
    this.orderId = options.id
  },

  onSummary(e) { this.setData({ summary: e.detail.value }) },

  /** R2-F5-5：配件登记（选填），完工前一并提交 */
  addPart() {
    this.setData({ parts: this.data.parts.concat({ name: '', qty: '1' }) })
  },

  removePart(e) {
    const parts = this.data.parts.slice()
    parts.splice(e.currentTarget.dataset.idx, 1)
    this.setData({ parts })
  },

  onPartName(e) {
    const parts = this.data.parts.slice()
    parts[e.currentTarget.dataset.idx].name = e.detail.value
    this.setData({ parts })
  },

  onPartQty(e) {
    const parts = this.data.parts.slice()
    parts[e.currentTarget.dataset.idx].qty = e.detail.value
    this.setData({ parts })
  },

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
    const { summary, images, parts } = this.data
    if (summary.trim().length < 10) { wx.showToast({ title: '维修说明至少 10 个字', icon: 'none' }); return }
    if (!images.length) { wx.showToast({ title: '请至少上传 1 张完工照片', icon: 'none' }); return }
    const validParts = parts.filter(p => p.name.trim())
    if (validParts.length !== parts.length) {
      wx.showToast({ title: '请填写配件名称或删除空行', icon: 'none' })
      return
    }
    try {
      // 配件与完工一次提交：后端同一事务落库，避免完工失败留下孤儿配件
      await post(`/orders/${this.orderId}/finish`, {
        summary: summary.trim(),
        finishImageUrls: images,
        parts: validParts.map(p => ({
          partName: p.name.trim(),
          quantity: Math.max(1, Math.min(99, Number(p.qty) || 1)),
        })),
      })
      wx.showToast({ title: '完工上报成功', icon: 'success' })
      setTimeout(() => wx.navigateBack(), 600)
    } catch (e) { /* 已提示 */ }
  },
})
