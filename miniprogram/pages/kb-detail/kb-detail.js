const { get, post } = require('../../utils/request')
const { toHtml } = require('../../utils/markdown')

Page({
  data: { detail: null, contentHtml: '', feedback: '', sending: false },

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

  /** 问题已解决：写 SOLVED 事件，作为自助解决率的分子 */
  async markSolved() {
    if (this.data.sending) return
    this.setData({ sending: true })
    try {
      await post(`/kb/articles/${this.id}/events`, { eventType: 'SOLVED' })
      this.setData({ feedback: 'solved' })
      wx.showToast({ title: '已记录，感谢反馈', icon: 'none' })
    } catch (e) { /* 拦截器已提示 */ }
    finally { this.setData({ sending: false }) }
  },

  /** 仍未解决：写 UNSOLVED 事件，带文章 id 去报修 */
  async goReport() {
    if (this.data.sending) return
    this.setData({ sending: true })
    try {
      await post(`/kb/articles/${this.id}/events`, { eventType: 'UNSOLVED' })
    } catch (e) { /* 事件失败不阻断报修 */ }
    const app = getApp()
    app.globalData.reportSourceKbId = Number(this.id)
    app.globalData.reportView = 'form'
    this.setData({ sending: false })
    wx.switchTab({ url: '/pages/report/report' })
  },
})
