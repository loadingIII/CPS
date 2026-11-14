const { post } = require('../../utils/request')

let seq = 0
function nextId() {
  seq += 1
  return 'm' + Date.now() + '-' + seq
}

const WELCOME = '你好，我是报修小助手。描述一下故障现象，我会先帮你查知识库；如果解决不了，再帮你转到报修。'

Page({
  data: {
    messages: [],
    input: '',
    sending: false,
    scrollTop: 0,
    quickReplies: ['空调不制冷', '宿舍跳闸', '门锁打不开', '讲台没反应'],
  },

  onLoad() {
    this.setData({
      messages: [{ id: nextId(), role: 'agent', text: WELCOME, articles: [] }],
    }, () => this.scrollBottom())
  },

  onInput(e) {
    this.setData({ input: e.detail.value })
  },

  sendQuick(e) {
    this.ask(e.currentTarget.dataset.q)
  },

  onSend() {
    this.ask(this.data.input)
  },

  /** 发一条消息：先落用户气泡与“正在查知识库”，拿到结果后替换占位 */
  async ask(raw) {
    const text = String(raw || '').trim()
    if (!text || this.data.sending) return

    const userMsg = { id: nextId(), role: 'user', text, articles: [] }
    const typing = { id: nextId(), role: 'agent', typing: true, text: '', articles: [] }
    const messages = this.data.messages.concat(userMsg, typing)
    this.setData({ messages, input: '', sending: true }, () => this.scrollBottom())

    try {
      const d = await post('/agent/chat', { message: text })
      this.replaceMessage(typing.id, {
        id: typing.id,
        role: 'agent',
        text: d.answer || '我暂时没有找到答案，可以直接提交报修。',
        articles: d.articles || [],
        faultType: d.suggestedFaultType || '',
      })
    } catch (e) {
      this.replaceMessage(typing.id, {
        id: typing.id,
        role: 'agent',
        text: '网络开小差了，请稍后再试，或直接提交报修。',
        articles: [],
      })
    } finally {
      this.setData({ sending: false })
    }
  },

  replaceMessage(id, next) {
    const messages = this.data.messages.map(m => (m.id === id ? next : m))
    this.setData({ messages }, () => this.scrollBottom())
  },

  /** scroll-top 用递增的大值，保证每次内容变化都能滚到底部 */
  scrollBottom() {
    this._scrollTick = (this._scrollTick || 0) + 1
    this.setData({ scrollTop: 100000 + this._scrollTick })
  },

  openArticle(e) {
    wx.navigateTo({ url: '/pages/kb-detail/kb-detail?id=' + e.currentTarget.dataset.id })
  },

  /** 带故障类型去报修；没有类型则直接打开表单 */
  goReport(e) {
    const type = e.currentTarget.dataset.type
    const app = getApp()
    if (type) app.globalData.homeFaultType = type
    else app.globalData.reportView = 'form'
    wx.switchTab({ url: '/pages/report/report' })
  },
})
