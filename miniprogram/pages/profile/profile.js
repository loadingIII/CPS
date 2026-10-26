const { get, put } = require('../../utils/request')

Page({
  data: { user: {}, roleLabel: '', phone: '', address: '', department: '', stats: [] },

  onShow() {
    const tb = this.getTabBar && this.getTabBar()
    if (tb) { tb.init(); tb.setSelected('/pages/profile/profile') }
    this.load()
  },

  async load() {
    const me = await get('/auth/me')
    wx.setStorageSync('user', me)
    this.setData({
      user: me,
      avatarText: (me.name || '?').charAt(0),
      roleLabel: { REPORTER: '师生', ADMIN: '后勤管理员', REPAIRER: '维修工', SYS_ADMIN: '系统管理员' }[me.role] || me.role,
      phone: me.phone || '',
      address: me.address || '',
      department: me.department || '',
    })
    this.loadStats()
  },

  /** 数据卡：师生看报修统计，维修工看绩效（接口不可用时静默隐藏） */
  async loadStats() {
    try {
      if (this.data.user.role === 'REPAIRER') {
        const me = await get('/workers/me')
        this.setData({
          stats: [
            { label: '综合评分', value: me.score },
            { label: '在办工单', value: me.currentLoad },
            { label: '负载上限', value: me.loadLimit },
          ],
        })
      } else {
        const [all, doing, done] = await Promise.all([
          get('/orders/my?page=1&pageSize=1'),
          get('/orders/my?status=PROCESSING&page=1&pageSize=1'),
          get('/orders/my?status=EVALUATED&page=1&pageSize=1'),
        ])
        this.setData({
          stats: [
            { label: '累计报修', value: all.total },
            { label: '处理中', value: doing.total },
            { label: '已完成', value: done.total },
          ],
        })
      }
    } catch (e) {
      this.setData({ stats: [] })
    }
  },

  onPhone(e) { this.setData({ phone: e.detail.value }) },
  onAddress(e) { this.setData({ address: e.detail.value }) },
  onDepartment(e) { this.setData({ department: e.detail.value }) },

  async saveProfile() {
    const { phone, address, department, user } = this.data
    const body = { phone }
    if (user.role === 'REPORTER') body.address = address
    else body.department = department
    try {
      await put('/auth/me', body)
      wx.showToast({ title: '保存成功', icon: 'success' })
      this.load()
    } catch (e) { /* 已提示 */ }
  },

  onLogout() {
    wx.showModal({
      title: '退出登录',
      content: '确认退出当前账号吗？',
      success: r => {
        if (!r.confirm) return
        wx.clearStorageSync()
        wx.reLaunch({ url: '/pages/login/login' })
      },
    })
  },
})
