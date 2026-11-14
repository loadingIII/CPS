const { get } = require('../../utils/request')

Page({
  data: { skills: [], faultType: '', keyword: '', list: [], page: 1, total: 0, loading: false },

  onLoad() {
    this.loadSkills()
    this.load(true)
  },

  onPullDownRefresh() {
    this.load(true).finally(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    if (this.data.list.length >= this.data.total) return
    this.load(false)
  },

  async loadSkills() {
    try {
      const skills = await get('/skills')
      this.setData({ skills: skills || [] })
    } catch (e) { /* 已提示 */ }
  },

  onKeyword(e) { this.setData({ keyword: e.detail.value }) },

  onType(e) {
    const type = e.currentTarget.dataset.type
    this.setData({ faultType: this.data.faultType === type ? '' : type })
    this.load(true)
  },

  onSearch() { this.load(true) },

  async load(reset) {
    if (this.data.loading) return
    const page = reset ? 1 : this.data.page + 1
    this.setData({ loading: true })
    try {
      const d = await get('/kb/articles?page=' + page + '&pageSize=10'
        + (this.data.faultType ? '&faultType=' + encodeURIComponent(this.data.faultType) : '')
        + (this.data.keyword.trim() ? '&keyword=' + encodeURIComponent(this.data.keyword.trim()) : ''))
      this.setData({
        list: reset ? (d.list || []) : this.data.list.concat(d.list || []),
        total: d.total || 0,
        page,
      })
    } finally {
      this.setData({ loading: false })
    }
  },

  goDetail(e) {
    wx.navigateTo({ url: '/pages/kb-detail/kb-detail?id=' + e.currentTarget.dataset.id })
  },
})
