const { get } = require('../../utils/request')

const ROLE_LABEL = {
  REPORTER: '师生',
  REPAIRER: '维修工',
  ADMIN: '后勤管理员',
  SYS_ADMIN: '系统管理员',
}

Page({
  data: {
    user: {},
    roleLabel: '',
    avatarText: '',
    stats: [],
    unread: 0,
    workerCampuses: '',
    workerSkills: '',
  },

  onShow() {
    const tb = this.getTabBar && this.getTabBar()
    if (tb) { tb.init(); tb.setSelected('/pages/profile/profile') }
    this.load()
  },

  async load() {
    try {
      const me = await get('/auth/me')
      wx.setStorageSync('user', me)
      this.setData({
        user: me,
        avatarText: (me.name || '?').charAt(0),
        roleLabel: ROLE_LABEL[me.role] || me.role,
      })
      this.loadStats(me)
    } catch (e) {
      // 网络失败时退回本地缓存的资料，至少不显示空卡片
      const cached = wx.getStorageSync('user')
      if (cached && cached.role) {
        this.setData({
          user: cached,
          avatarText: (cached.name || '?').charAt(0),
          roleLabel: ROLE_LABEL[cached.role] || cached.role,
        })
        this.loadStats(cached)
      }
    }
    this.loadUnread()
  },

  /** 数据卡：师生看报修统计，维修工看接单情况（接口不可用时静默隐藏） */
  async loadStats(user) {
    if (user.role !== 'REPORTER' && user.role !== 'REPAIRER') {
      this.setData({ stats: [] })
      return
    }
    try {
      if (user.role === 'REPAIRER') {
        const [me, pending, processing, done] = await Promise.all([
          get('/workers/me'),
          get('/orders/mine-assigned?status=PENDING&page=1&pageSize=1'),
          get('/orders/mine-assigned?status=PROCESSING&page=1&pageSize=1'),
          get('/orders/mine-assigned?status=COMPLETED&page=1&pageSize=1'),
        ])
        this.setData({
          workerCampuses: (me.campuses || []).join('、') || '由后勤分配',
          workerSkills: (me.skills || []).join('、') || '未配置',
          stats: [
            { label: '待接单', value: pending.total || 0 },
            { label: '在办', value: processing.total || 0 },
            { label: '已完成', value: done.total || 0 },
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
            { label: '累计报修', value: all.total || 0 },
            { label: '处理中', value: doing.total || 0 },
            { label: '已完成', value: done.total || 0 },
          ],
        })
      }
    } catch (e) {
      this.setData({ stats: [] })
    }
  },

  /** 未读通知角标（失败静默，不打扰主流程） */
  async loadUnread() {
    try {
      const n = await get('/notifications/unread-count')
      this.setData({ unread: n || 0 })
    } catch (e) {
      this.setData({ unread: 0 })
    }
  },

  goEdit() {
    wx.navigateTo({ url: '/pages/profile/edit' })
  },

  goMessages() {
    wx.navigateTo({ url: '/pages/messages/messages' })
  },

  goKb() {
    wx.navigateTo({ url: '/pages/kb/kb' })
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
