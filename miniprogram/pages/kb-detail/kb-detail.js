const { get } = require('../../utils/request')
const { toHtml } = require('../../utils/markdown')

Page({
  data: { detail: null, contentHtml: '' },

  onLoad(options) {
    this.id = options.id
    this.load()
  },

  async load() {
    const detail = await get('/kb/articles/' + this.id)
    let contentHtml = ''
    try {
      contentHtml = toHtml(detail.content || '')
    } catch (e) {
      // 解析失败时退回纯文本，至少不影响阅读
      contentHtml = ''
    }
    this.setData({ detail, contentHtml })
    if (detail.title) wx.setNavigationBarTitle({ title: detail.title })
  },
})
