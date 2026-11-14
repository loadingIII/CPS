const { get } = require('../../utils/request')

// 标记尺寸随地图缩放级别动态调整：size = scale × 1.4，限制在 [14, 34]
const SIZE_RATIO = 1.4
const SIZE_MIN = 14
const SIZE_MAX = 34

Page({
  data: {
    center: { lat: 30.5, lng: 114.4 },
    markers: [],
    orders: [],
    markerSize: 21,
    // 原生 map 组件对 vh/% 高度支持不稳定（会算成 0 高度导致整块透明），改用 px 固定高度
    mapHeight: 360,
    mapVisible: true,
    mapError: false,
  },

  // markerId → 工单映射（标记 id 即数组下标）
  _orders: [],
  _mapCtx: null,

  onLoad() {
    this.setData({ mapHeight: this.calcMapHeight() })
  },

  /** 地图占屏幕 46%，换算成 px；取不到窗口信息时回退 360 */
  calcMapHeight() {
    try {
      const info = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync()
      return Math.max(260, Math.round((info.windowHeight || 780) * 0.46))
    } catch (e) {
      return 360
    }
  },

  onReady() {
    this._mapCtx = wx.createMapContext('orderMap', this)
  },

  onShow() {
    const tb = this.getTabBar && this.getTabBar()
    if (tb) { tb.init(); tb.setSelected('/pages/worker-map/worker-map') }
    this.load()
  },

  onPullDownRefresh() {
    this.load().finally(() => wx.stopPullDownRefresh())
  },

  /** 拉取已接单 + 可抢单的坐标点并渲染到地图 */
  async load() {
    try {
      const list = await get('/orders/worker-map')
      this._orders = list
      const center = list.length
        ? { lat: list[0].lat, lng: list[0].lng }
        : this.data.center
      this.setData({
        orders: list,
        center,
        markers: this.buildMarkers(list, this.data.markerSize),
      })
    } catch (e) { /* 拦截器已提示 */ }
  },

  buildMarkers(list, size) {
    return list.map((o, i) => ({
      id: i,
      latitude: o.lat,
      longitude: o.lng,
      width: size,
      height: size,
      anchor: { x: 0.5, y: 0.5 },
      iconPath: o.kind === 'MINE' ? '/assets/marker-mine.png' : '/assets/marker-grab.png',
      callout: {
        content: `${o.orderNo}\n${o.status} · ${o.faultType}`,
        display: 'BYCLICK',
        borderRadius: 10,
        padding: 10,
        fontSize: 12,
        borderWidth: 1,
        borderColor: '#E8EDEC',
      },
    }))
  },

  /** 缩放结束后按当前级别重算标记大小 */
  onRegionChange(e) {
    if (e.type !== 'end' || !this._mapCtx) return
    this._mapCtx.getScale({
      success: res => {
        const size = Math.max(SIZE_MIN, Math.min(SIZE_MAX, Math.round(res.scale * SIZE_RATIO)))
        if (size !== this.data.markerSize) {
          this.setData({
            markerSize: size,
            markers: this.buildMarkers(this._orders, size),
          })
        }
      },
    })
  },

  onMarkerTap(e) {
    const o = this._orders[e.detail.markerId]
    if (o) {
      wx.navigateTo({ url: '/pages/worker-detail/worker-detail?id=' + o.id })
    }
  },

  /** 地图组件加载失败（网络/权限/渲染异常）时给出兜底提示，不留下空白块 */
  onMapError(e) {
    console.warn('[worker-map] map error', e && e.detail)
    this.setData({ mapError: true })
  },

  /** 重建 map 组件：原生组件销毁后重新挂载才会重新拉底图 */
  retryMap() {
    this.setData({ mapVisible: false, mapError: false })
    setTimeout(() => {
      this._mapCtx = null
      this.setData({ mapVisible: true }, () => {
        this._mapCtx = wx.createMapContext('orderMap', this)
        this.load()
      })
    }, 120)
  },

  /** 用系统地图打开该工单位置（不依赖内嵌地图底图） */
  navigate(e) {
    const { lat, lng, name } = e.currentTarget.dataset
    if (!lat || !lng) {
      wx.showToast({ title: '该工单没有坐标', icon: 'none' })
      return
    }
    wx.openLocation({
      latitude: Number(lat),
      longitude: Number(lng),
      name: name || '报修位置',
      address: name || '',
      scale: 17,
    })
  },

  goDetail(e) {
    wx.navigateTo({ url: '/pages/worker-detail/worker-detail?id=' + e.currentTarget.dataset.id })
  },
})
