const { get } = require('../../utils/request')
const { STATUS_LABEL, STATUS_TAG } = require('../../utils/util')

const TABS = [
  { value: '', label: '全部' },
  { value: 'PENDING_AUDIT', label: '待审核' },
  { value: 'PENDING', label: '待处理' },
  { value: 'PROCESSING', label: '处理中' },
  { value: 'COMPLETED', label: '待评价' },
  { value: 'EVALUATED', label: '已评价' },
  { value: 'REJECTED', label: '已驳回' },
]

Page({
  data: {
    tabs: TABS,
    STATUS_LABEL, STATUS_TAG,
    status: '',
    list: [],
    page: 1,
    total: 0,
  },

  onShow() {
    const tb = this.getTabBar && this.getTabBar()
    if (tb) { tb.init(); tb.setSelected('/pages/my-orders/my-orders') }
    this.reload()
  },

  onTab(e) {
    this.setData({ status: e.currentTarget.dataset.value })
    this.reload()
  },

  reload() {
    this.setData({ page: 1, list: [] })
    this.load()
  },

  async load() {
    const { status, page } = this.data
    const d = await get(`/orders/my?page=${page}&pageSize=10${status ? '&status=' + status : ''}`)
    const list = page === 1 ? d.list : this.data.list.concat(d.list)
    this.setData({ list, total: d.total })
  },

  onReachBottom() {
    if (this.data.list.length < this.data.total) {
      this.setData({ page: this.data.page + 1 })
      this.load()
    }
  },

  goDetail(e) {
    wx.navigateTo({ url: '/pages/order-detail/order-detail?id=' + e.currentTarget.dataset.id })
  },
})
