const { get, post, upload, BASE } = require('../../utils/request')
const { STATUS_LABEL, STATUS_TAG } = require('../../utils/util')

const ORDER_TABS = [
  { value: '', label: '全部' },
  { value: 'PENDING_AUDIT', label: '待审核' },
  { value: 'PENDING', label: '待处理' },
  { value: 'PROCESSING', label: '处理中' },
  { value: 'COMPLETED', label: '待评价' },
  { value: 'EVALUATED', label: '已评价' },
  { value: 'REJECTED', label: '已驳回' },
]

Page({
  data: {
    BASE,
    greeting: '',
    userName: '',
    serviceView: 'form',
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
    orderTabs: ORDER_TABS,
    STATUS_LABEL,
    STATUS_TAG,
    orderStatus: '',
    orderList: [],
    orderPage: 1,
    orderTotal: 0,
    ordersLoading: false,
    ordersError: false,
  },

  onShow() {
    const tb = this.getTabBar && this.getTabBar()
    if (tb) { tb.init(); tb.setSelected('/pages/report/report') }
    const user = wx.getStorageSync('user') || {}
    const h = new Date().getHours()
    const greeting = h < 6 ? '夜深了' : h < 12 ? '早上好' : h < 14 ? '中午好' : h < 18 ? '下午好' : '晚上好'
    const app = getApp()
    const requestedView = app.globalData.reportView || this.data.serviceView || 'form'
    app.globalData.reportView = ''
    const homeFaultType = app.globalData.homeFaultType || ''
    if (homeFaultType) app.globalData.homeFaultType = ''
    this.setData({
      userName: user.name || '',
      greeting,
      serviceView: requestedView,
      faultType: homeFaultType || this.data.faultType,
    })
    this.loadBase()
    if (requestedView === 'orders') this.reloadOrders()
  },

  onPullDownRefresh() {
    const task = this.data.serviceView === 'orders' ? this.reloadOrders() : this.loadBase()
    Promise.resolve(task).finally(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    if (this.data.serviceView !== 'orders' || this.data.ordersLoading) return
    if (this.data.orderList.length < this.data.orderTotal) {
      this.loadOrders(this.data.orderPage + 1)
    }
  },

  onServiceView(e) {
    const serviceView = e.currentTarget.dataset.view
    if (!serviceView || serviceView === this.data.serviceView) return
    this.setData({ serviceView })
    if (serviceView === 'orders') this.reloadOrders()
  },

  onOrderStatus(e) {
    const orderStatus = e.currentTarget.dataset.value
    this.setData({ orderStatus }, () => this.reloadOrders())
  },

  reloadOrders() {
    const requestId = (this._ordersRequestId || 0) + 1
    this._ordersRequestId = requestId
    this.setData({
      orderPage: 1,
      orderList: [],
      orderTotal: 0,
      ordersLoading: true,
      ordersError: false,
    })
    return this.loadOrders(1, requestId)
  },

  async loadOrders(page, requestId) {
    if (!requestId && this.data.ordersLoading) return
    const currentRequest = requestId || ((this._ordersRequestId || 0) + 1)
    this._ordersRequestId = currentRequest
    const status = this.data.orderStatus
    this.setData({ ordersLoading: true, ordersError: false })
    try {
      const result = await get(`/orders/my?page=${page}&pageSize=10${status ? '&status=' + status : ''}`)
      if (currentRequest !== this._ordersRequestId) return
      const nextList = page === 1 ? (result.list || []) : this.data.orderList.concat(result.list || [])
      this.setData({
        orderList: nextList,
        orderPage: page,
        orderTotal: result.total || 0,
      })
    } catch (e) {
      if (currentRequest === this._ordersRequestId) this.setData({ ordersError: true })
    } finally {
      if (currentRequest === this._ordersRequestId) this.setData({ ordersLoading: false })
    }
  },

  goOrderDetail(e) {
    wx.navigateTo({ url: '/pages/order-detail/order-detail?id=' + e.currentTarget.dataset.id })
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
          this.setData({
            faultType: '', desc: '', images: [], mapPick: null, locationDetail: '',
            serviceView: 'orders', orderStatus: '',
          }, () => this.reloadOrders())
        },
      })
    } catch (e) { /* 拦截器已提示 */ }
    finally { this.setData({ submitting: false }) }
  },
})
