// 兼容旧入口：我的报修现已与报修表单整合在同一页。
Page({
  onLoad() {
    getApp().globalData.reportView = 'orders'
    wx.switchTab({ url: '/pages/report/report' })
  },
})
