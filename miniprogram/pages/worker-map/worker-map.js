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
  },

  // markerId → 工单映射（标记 id 即数组下标）
  _orders: [],
  _mapCtx: null,

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

  goDetail(e) {
    wx.navigateTo({ url: '/pages/worker-detail/worker-detail?id=' + e.currentTarget.dataset.id })
  },
})
