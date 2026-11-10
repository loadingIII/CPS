const tabsByRole = {
  REPORTER: [
    { path: '/pages/home/home', text: '首页', icon: 'home' },
    { path: '/pages/report/report', text: '报修服务', icon: 'tool' },
    { path: '/pages/profile/profile', text: '个人中心', icon: 'user' },
  ],
  REPAIRER: [
    { path: '/pages/home/home', text: '首页', icon: 'home' },
    { path: '/pages/worker-orders/worker-orders', text: '维修工单', icon: 'case' },
    { path: '/pages/worker-map/worker-map', text: '工单地图', icon: 'map' },
    { path: '/pages/profile/profile', text: '个人中心', icon: 'user' },
  ],
}

Component({
  data: {
    selected: 0,
    tabs: tabsByRole.REPORTER,
  },
  lifetimes: {
    attached() {
      this.init()
    },
  },
  methods: {
    init() {
      const role = (wx.getStorageSync('user') || {}).role || 'REPORTER'
      this.setData({ tabs: tabsByRole[role] || tabsByRole.REPORTER })
    },
    /** 页面 onShow 调用：按路径高亮 */
    setSelected(path) {
      const idx = this.data.tabs.findIndex(t => t.path === path)
      if (idx >= 0) this.setData({ selected: idx })
    },
    onTab(e) {
      wx.switchTab({ url: e.currentTarget.dataset.path })
    },
  },
})
