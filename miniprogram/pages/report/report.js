const { get, post, upload, BASE } = require('../../utils/request')

Page({
  data: {
    BASE,
    greeting: '',
    userName: '',
    campusList: [],
    campusIndex: 0,
    campusCode: '',
    campusName: '',
    locationDetail: '',
    skillNames: [],
    mapPick: null,
    faultType: '',
    urgency: 'NORMAL',
    desc: '',
    images: [],
    submitting: false,
  },

  onShow() {
    const tb = this.getTabBar && this.getTabBar()
    if (tb) { tb.init(); tb.setSelected('/pages/report/report') }
    const user = wx.getStorageSync('user') || {}
    const h = new Date().getHours()
    const greeting = h < 6 ? '夜深了' : h < 12 ? '早上好' : h < 14 ? '中午好' : h < 18 ? '下午好' : '晚上好'
    this.setData({ userName: user.name || '', greeting })
    this.loadBase()
  },

  /** 校区为唯一受控选项（后端枚举），其余位置由报修人自由填写 */
  async loadBase() {
    if (this.data.campusList.length) return
    const [campuses, skills] = await Promise.all([get('/campuses'), get('/skills')])
    const first = campuses[0] || null
    this.setData({
      campusList: campuses,
      campusIndex: 0,
      campusCode: first ? first.code : '',
      campusName: first ? first.name : '',
      skillNames: skills.map(s => s.name),
    })
  },

  onCampusChange(e) {
    const i = Number(e.detail.value)
    const c = this.data.campusList[i]
    if (c) this.setData({ campusIndex: i, campusCode: c.code, campusName: c.name })
  },

  onLocationDetail(e) {
    this.setData({ locationDetail: e.detail.value })
  },

  onFaultType(e) {
    this.setData({ faultType: this.data.skillNames[e.detail.value] })
  },

  /** 地图选点补充定位（选填，wx.chooseLocation 内置选点地图） */
  chooseMapPoint() {
    wx.chooseLocation({
      success: res => {
        if (!res.latitude || !res.longitude) return
        this.setData({
          mapPick: {
            name: res.name || res.address || '地图选点',
            address: res.address || '',
            lng: res.longitude,
            lat: res.latitude,
          },
        })
      },
      fail: () => { /* 用户取消或未授权，静默 */ },
    })
  },

  clearMapPoint() {
    this.setData({ mapPick: null })
  },

  setUrgency(e) {
    this.setData({ urgency: e.currentTarget.dataset.v })
  },

  onDesc(e) {
    this.setData({ desc: e.detail.value })
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
    const idx = e.currentTarget.dataset.idx
    const images = this.data.images.slice()
    images.splice(idx, 1)
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
    const { campusCode, locationDetail, faultType, urgency, desc, images } = this.data
    if (!campusCode) { wx.showToast({ title: '请选择校区', icon: 'none' }); return }
    if (!locationDetail.trim()) { wx.showToast({ title: '请填写详细位置', icon: 'none' }); return }
    if (!faultType) { wx.showToast({ title: '请选择故障类型', icon: 'none' }); return }
    if (desc.trim().length < 5) { wx.showToast({ title: '故障描述至少 5 个字', icon: 'none' }); return }

    this.setData({ submitting: true })
    try {
      const data = await post('/orders', {
        campus: campusCode,
        locationDetail: locationDetail.trim(),
        faultType,
        urgency,
        description: desc.trim(),
        imageUrls: images,
        mapName: this.data.mapPick ? this.data.mapPick.name : null,
        mapLng: this.data.mapPick ? this.data.mapPick.lng : null,
        mapLat: this.data.mapPick ? this.data.mapPick.lat : null,
        clientToken: 'mp-' + Date.now() + '-' + Math.floor(Math.random() * 100000),
      })
      wx.showModal({
        title: '报修成功',
        content: '工单号 ' + data.orderNo + '，可在「我的报修」中查看进度',
        showCancel: false,
        confirmColor: '#0E4A54',
        success: () => {
          this.setData({ faultType: '', desc: '', images: [], mapPick: null, locationDetail: '' })
          wx.switchTab({ url: '/pages/my-orders/my-orders' })
        },
      })
    } catch (e) { /* 拦截器已提示 */ }
    finally { this.setData({ submitting: false }) }
  },
})
