const { get, post, upload, BASE } = require('../../utils/request')

Page({
  data: {
    BASE,
    rejectReason: '',
    campusList: [],
    campusIndex: 0,
    campusCode: '',
    campusName: '',
    campusAddress: '',
    locationDetail: '',
    skillNames: [],
    faultType: '',
    urgency: 'NORMAL',
    description: '',
    images: [],
    submitting: false,
  },

  onLoad(options) {
    this.orderId = options.id
    this.load()
  },

  async load() {
    const [detail, campuses, skills, logs] = await Promise.all([
      get('/orders/' + this.orderId),
      get('/campuses'),
      get('/skills'),
      get(`/orders/${this.orderId}/logs`),
    ])
    const campusList = campuses || []
    const idx = Math.max(0, campusList.findIndex(c => c.code === detail.campus))
    const campus = campusList[idx] || null
    const rejectLog = (logs || []).slice().reverse().find(l => l.action === '驳回')
    this.setData({
      rejectReason: rejectLog ? rejectLog.remark : '',
      campusList,
      campusIndex: idx,
      campusCode: detail.campus,
      campusName: campus ? campus.name : '',
      campusAddress: campus ? (campus.address || '') : '',
      locationDetail: (detail.locationText || '').split('·').pop().trim(),
      skillNames: (skills || []).map(s => s.name),
      faultType: detail.faultType,
      urgency: detail.urgency || 'NORMAL',
      description: detail.description,
      // 原报修图预填，用户可增删；不再强制重新上传
      images: detail.reportImages || [],
    })
  },

  onCampusChange(e) {
    const i = Number(e.detail.value)
    const c = this.data.campusList[i]
    if (c) {
      this.setData({
        campusIndex: i,
        campusCode: c.code,
        campusName: c.name,
        campusAddress: c.address || '',
      })
    }
  },

  onLocationDetail(e) {
    this.setData({ locationDetail: e.detail.value })
  },

  onFaultType(e) {
    const i = Number(e.detail.value)
    this.setData({ faultType: this.data.skillNames[i] || '' })
  },

  setUrgency(e) {
    this.setData({ urgency: e.currentTarget.dataset.v })
  },

  onDesc(e) {
    this.setData({ description: e.detail.value })
  },

  chooseImage() {
    const remain = 3 - this.data.images.length
    wx.chooseMedia({
      count: remain,
      mediaType: ['image'],
      sizeType: ['compressed'],
      success: async res => {
        wx.showLoading({ title: '上传中' })
        try {
          for (const f of res.tempFiles) {
            const url = await upload(f.tempFilePath)
            this.setData({ images: this.data.images.concat(url) })
          }
        } finally {
          wx.hideLoading()
        }
      },
    })
  },

  deleteImage(e) {
    const images = this.data.images.slice()
    images.splice(e.currentTarget.dataset.idx, 1)
    this.setData({ images })
  },

  previewImage(e) {
    const idx = e.currentTarget.dataset.idx
    wx.previewImage({
      current: BASE + this.data.images[idx],
      urls: this.data.images.map(u => BASE + u),
    })
  },

  async onSubmit() {
    const { campusCode, locationDetail, faultType, urgency, description, images } = this.data
    if (!campusCode) { wx.showToast({ title: '请选择校区', icon: 'none' }); return }
    if (!locationDetail.trim()) { wx.showToast({ title: '请填写详细位置', icon: 'none' }); return }
    if (!faultType) { wx.showToast({ title: '请选择故障类型', icon: 'none' }); return }
    if (description.trim().length < 5) { wx.showToast({ title: '描述至少 5 个字', icon: 'none' }); return }

    this.setData({ submitting: true })
    try {
      await post(`/orders/${this.orderId}/resubmit`, {
        campus: campusCode,
        locationDetail: locationDetail.trim(),
        faultType,
        urgency,
        description: description.trim(),
        imageUrls: images,
      })
      wx.showToast({ title: '已重新提交审核', icon: 'success' })
      setTimeout(() => wx.navigateBack(), 600)
    } catch (e) { /* 已拦截提示 */ }
    finally { this.setData({ submitting: false }) }
  },
})
