const { get, post, upload, BASE } = require('../../utils/request')

Page({
  data: {
    BASE,
    greeting: '',
    userName: '',
    tree: [],
    skillNames: [],
    columns: [[], [], [], []],
    colIdx: [0, 0, 0, 0],
    locationLabel: '',
    locationId: null,
    mapPick: null,
    faultType: '',
    urgency: 'NORMAL',
    desc: '',
    images: [],
    submitting: false,
  },

  // 内部索引数据（不参与渲染）
  campusList: [],
  areaList: [],
  buildingList: [],
  spotList: [],

  onShow() {
    const tb = this.getTabBar && this.getTabBar()
    if (tb) { tb.init(); tb.setSelected('/pages/report/report') }
    const user = wx.getStorageSync('user') || {}
    const h = new Date().getHours()
    const greeting = h < 6 ? '夜深了' : h < 12 ? '早上好' : h < 14 ? '中午好' : h < 18 ? '下午好' : '晚上好'
    this.setData({ userName: user.name || '', greeting })
    this.loadBase()
  },

  async loadBase() {
    if (!this.data.tree.length) {
      const tree = await get('/places/tree')
      const skills = await get('/skills')
      this.setData({
        tree,
        skillNames: skills.map(s => s.name),
      })
      this.campusList = tree
      this.refreshColumns()
    }
  },

  /** 四级级联列刷新：校区 → 片区 → 楼栋 → 房间（可停在楼栋级） */
  refreshColumns() {
    const ci = Math.min(this.data.colIdx[0], this.campusList.length - 1)
    this.areaList = (this.campusList[ci] && this.campusList[ci].children) || []
    const ai = Math.min(this.data.colIdx[1], this.areaList.length - 1)
    this.buildingList = (this.areaList[ai] && this.areaList[ai].children) || []
    const bi = Math.min(this.data.colIdx[2], this.buildingList.length - 1)
    this.spotList = (this.buildingList[bi] && this.buildingList[bi].children) || []

    this.setData({
      columns: [
        this.campusList.map(x => x.name),
        this.areaList.map(x => x.name),
        this.buildingList.map(x => x.name),
        ['（整栋楼/公共区域）'].concat(this.spotList.map(x => x.name)),
      ],
      colIdx: [ci, ai, bi, 0],
    })
    this.computeLocation()
  },

  onPickColumn(e) {
    const { column, value } = e.detail
    const idx = this.data.colIdx.slice()
    idx[column] = value
    this.setData({ colIdx: idx })
    this.refreshColumns()
  },

  onPick(e) {
    this.setData({ colIdx: e.detail.value.map(Number) })
    this.refreshColumns()
  },

  computeLocation() {
    const { colIdx } = this.data
    if (!this.buildingList.length) {
      this.setData({ locationId: null, locationLabel: '' })
      return
    }
    const b = this.buildingList[colIdx[2]]
    const spot = this.spotList[colIdx[3]]
    const labelParts = [
      this.campusList[colIdx[0]] && this.campusList[colIdx[0]].name,
      this.areaList[colIdx[1]] && this.areaList[colIdx[1]].name,
      b && b.name,
      colIdx[3] > 0 && spot ? spot.name : '',
    ].filter(Boolean)
    this.setData({
      locationId: colIdx[3] > 0 && spot ? spot.id : b.id,
      locationLabel: labelParts.join(' / '),
    })
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
    const { locationId, faultType, urgency, desc, images } = this.data
    if (!locationId) { wx.showToast({ title: '请选择报修位置', icon: 'none' }); return }
    if (!faultType) { wx.showToast({ title: '请选择故障类型', icon: 'none' }); return }
    if (desc.trim().length < 5) { wx.showToast({ title: '故障描述至少 5 个字', icon: 'none' }); return }

    this.setData({ submitting: true })
    try {
      const data = await post('/orders', {
        locationId,
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
          this.setData({ faultType: '', desc: '', images: [], mapPick: null, locationLabel: '', locationId: null })
          wx.switchTab({ url: '/pages/my-orders/my-orders' })
        },
      })
    } catch (e) { /* 拦截器已提示 */ }
    finally { this.setData({ submitting: false }) }
  },
})
