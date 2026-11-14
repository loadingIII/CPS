const { get } = require('../../utils/request')

Page({
  data: { detail: null },

  onLoad(options) {
    this.id = options.id
    this.load()
  },

  async load() {
    const detail = await get('/kb/articles/' + this.id)
    this.setData({ detail })
    if (detail.title) wx.setNavigationBarTitle({ title: detail.title })
  },
})
