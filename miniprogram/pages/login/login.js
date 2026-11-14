const { post } = require('../../utils/request')

Page({
  data: {
    mode: 'login',        // login | register
    role: 'REPORTER',     // REPORTER | REPAIRER
    jobNo: '',
    name: '',
    phone: '',
    password: '',
    confirm: '',
    loading: false,
  },

  onLoad() {
    // 已登录直接进入对应角色首页
    if (wx.getStorageSync('token')) {
      this.goHome()
    }
  },

  /** 切换登录入口：报修人 / 维修工 */
  onRole(e) {
    const role = e.currentTarget.dataset.role
    if (role && role !== this.data.role) {
      this.setData({ role })
    }
  },

  /** 登录 / 注册模式切换，注册只对报修人开放 */
  switchMode(e) {
    const mode = e.currentTarget.dataset.mode
    if (!mode || mode === this.data.mode) return
    this.setData({ mode, password: '', confirm: '' })
  },

  onJobNo(e) { this.setData({ jobNo: e.detail.value }) },
  onName(e) { this.setData({ name: e.detail.value }) },
  onPhone(e) { this.setData({ phone: e.detail.value }) },
  onPassword(e) { this.setData({ password: e.detail.value }) },
  onConfirm(e) { this.setData({ confirm: e.detail.value }) },

  async onLogin() {
    const { role, jobNo, password } = this.data
    if (!jobNo || !password) {
      wx.showToast({ title: '请输入学工号和密码', icon: 'none' })
      return
    }
    this.setData({ loading: true })
    try {
      const data = await post('/auth/login', { jobNo, password, role })
      this.afterAuth(data)
    } catch (e) { /* 拦截器已提示 */ }
    finally { this.setData({ loading: false }) }
  },

  /** 师生自助注册：角色固定报修人，注册成功直接登录 */
  async onRegister() {
    const { jobNo, name, phone, password, confirm } = this.data
    const jobNoTrim = jobNo.trim()
    const nameTrim = name.trim()

    if (!/^[A-Za-z0-9_-]{4,20}$/.test(jobNoTrim)) {
      wx.showToast({ title: '学工号需 4-20 位字母/数字', icon: 'none' })
      return
    }
    if (nameTrim.length < 2) {
      wx.showToast({ title: '请填写真实姓名', icon: 'none' })
      return
    }
    if (!/^1[3-9]\d{9}$/.test(phone)) {
      wx.showToast({ title: '请输入正确的手机号', icon: 'none' })
      return
    }
    if (password.length < 6) {
      wx.showToast({ title: '密码至少 6 位', icon: 'none' })
      return
    }
    if (password !== confirm) {
      wx.showToast({ title: '两次输入的密码不一致', icon: 'none' })
      return
    }

    this.setData({ loading: true })
    try {
      const data = await post('/auth/register', {
        jobNo: jobNoTrim,
        name: nameTrim,
        phone,
        password,
      })
      this.afterAuth(data)
    } catch (e) { /* 拦截器已提示 */ }
    finally { this.setData({ loading: false }) }
  },

  afterAuth(data) {
    wx.setStorageSync('token', data.token)
    wx.setStorageSync('user', data.user)
    wx.showToast({ title: '欢迎，' + data.user.name, icon: 'none' })
    setTimeout(() => this.goHome(), 400)
  },

  goHome() {
    wx.switchTab({ url: '/pages/home/home' })
  },
})
