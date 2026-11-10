const { post } = require('../../utils/request')

Page({
  data: { jobNo: '', password: '', loading: false },

  onLoad() {
    // 已登录直接进入对应角色首页
    if (wx.getStorageSync('token')) {
      this.goHome()
    }
  },

  onJobNo(e) { this.setData({ jobNo: e.detail.value }) },
  onPassword(e) { this.setData({ password: e.detail.value }) },

  async onLogin() {
    const { jobNo, password } = this.data
    if (!jobNo || !password) {
      wx.showToast({ title: '请输入学工号和密码', icon: 'none' })
      return
    }
    this.setData({ loading: true })
    try {
      const data = await post('/auth/login', { jobNo, password })
      wx.setStorageSync('token', data.token)
      wx.setStorageSync('user', data.user)
      wx.showToast({ title: '欢迎，' + data.user.name, icon: 'none' })
      setTimeout(() => this.goHome(), 400)
    } catch (e) { /* 拦截器已提示 */ }
    finally { this.setData({ loading: false }) }
  },

  goHome() {
    wx.switchTab({ url: '/pages/home/home' })
  },
})
