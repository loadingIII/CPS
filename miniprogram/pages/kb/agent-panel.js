const { post } = require('../../utils/request')

let seq = 0
function nextId() {
  seq += 1
  return 'm' + Date.now() + '-' + seq
}

const WELCOME = '你好，我是报修小助手。描述一下故障现象，我会先帮你查知识库；如果解决不了，再帮你转到报修。'

Component({
  data: {
    full: false,
    ball: false,
    dragging: false,
    entered: false,
    panelHeight: 0,
    halfHeight: 0,
    fullHeight: 0,
    messages: [],
    input: '',
    sending: false,
    scrollTop: 0,
    keyboardHeight: 0,
    quickReplies: ['空调不制冷', '宿舍跳闸', '门锁打不开', '讲台没反应'],
  },

  lifetimes: {
    attached() {
      this._alive = true
      const info = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync()
      const windowHeight = info.windowHeight || 700
      const halfHeight = Math.round(windowHeight * 0.48)
      const fullHeight = windowHeight - 8

      this.setData({
        halfHeight,
        fullHeight,
        messages: [{ id: nextId(), role: 'agent', text: WELCOME, articles: [] }],
      })
      // 初始从底部升到半屏
      setTimeout(() => {
        if (this._alive) this.setData({ panelHeight: halfHeight, entered: true })
      }, 80)

      this._kbHandler = res => {
        if (this._alive) this.setData({ keyboardHeight: res.height || 0 })
      }
      if (wx.onKeyboardHeightChange) {
        wx.onKeyboardHeightChange(this._kbHandler)
      }
    },
    detached() {
      this._alive = false
      if (this._tabTimer) clearTimeout(this._tabTimer)
      if (this._kbHandler && wx.offKeyboardHeightChange) {
        wx.offKeyboardHeightChange(this._kbHandler)
      }
    },
  },

  methods: {
    toHalf() {
      this.setFull(false)
    },

    setFull(next) {
      if (!this._alive || this.data.ball) return
      const targetHeight = next ? this.data.fullHeight : this.data.halfHeight
      if (next === this.data.full && this.data.panelHeight === targetHeight) return

      this.setData({ full: next, panelHeight: targetHeight })
      if (!next && this.data.keyboardHeight) {
        try { wx.hideKeyboard() } catch (e) { /* 忽略 */ }
      }

      // TabBar 等面板盖住后再隐藏，避免过渡中底部闪一下；收起时先恢复
      if (next) {
        this.hideTabBarAfterCover()
      } else {
        if (this._tabTimer) clearTimeout(this._tabTimer)
        try { wx.showTabBar({ animation: false }) } catch (e) { /* 忽略 */ }
      }
      setTimeout(() => this.scrollBottom(), 440)
    },

    /** 等面板盖住 TabBar 后再隐藏，避免过渡中底部闪一下 */
    hideTabBarAfterCover() {
      if (this._tabTimer) clearTimeout(this._tabTimer)
      this._tabTimer = setTimeout(() => {
        try { wx.hideTabBar({ animation: false }) } catch (e) { /* 忽略 */ }
      }, 420)
    },

    /** 左上角：收起为右下角悬浮球 */
    minimize() {
      if (this.data.ball) return
      if (this._tabTimer) clearTimeout(this._tabTimer)
      if (this.data.keyboardHeight) {
        try { wx.hideKeyboard() } catch (e) { /* 忽略 */ }
      }
      try { wx.showTabBar({ animation: false }) } catch (e) { /* 忽略 */ }
      this.setData({ ball: true, full: false, panelHeight: 0, dragging: false })
    },

    /** 点悬浮球：直接回到全屏 */
    restore() {
      if (!this.data.ball) return
      this.setData({ ball: false, full: true, panelHeight: this.data.fullHeight })
      this.hideTabBarAfterCover()
      setTimeout(() => this.scrollBottom(), 440)
    },

    /* ---------- 拖拽：上滑展开，下滑收起 ---------- */
    onHandleStart(e) {
      this._startY = e.touches[0].clientY
      this._startHeight = this.data.panelHeight
      this._dragging = true
      this._moved = false
      this._lastMoveAt = 0
      this.setData({ dragging: true })
    },

    onHandleMove(e) {
      if (!this._dragging) return
      // 限制到 ~60fps，避免每个 touchmove 都 setData 造成拖拽发涩
      const now = Date.now()
      if (now - (this._lastMoveAt || 0) < 16) return
      this._lastMoveAt = now

      const dy = this._startY - e.touches[0].clientY // 上滑为正
      if (Math.abs(dy) > 4) this._moved = true
      const min = Math.max(220, Math.round(this.data.halfHeight * 0.7))
      const next = Math.round(Math.max(min, Math.min(this.data.fullHeight, this._startHeight + dy)))
      this.setData({ panelHeight: next })
    },

    onHandleEnd() {
      if (!this._dragging) return
      this._dragging = false
      this.setData({ dragging: false })
      // 只是点了一下头部（没拖动）时保持当前状态，交给按钮的 tap 处理
      if (!this._moved) return
      const mid = (this.data.halfHeight + this.data.fullHeight) / 2
      this.setFull(this.data.panelHeight >= mid)
    },

    /** 聚焦输入框时自动升到全屏，给键盘留出空间 */
    onInputFocus() {
      if (!this.data.full) this.setFull(true)
    },

    /* ---------- 对话 ---------- */
    onInput(e) {
      this.setData({ input: e.detail.value })
    },

    sendQuick(e) {
      this.ask(e.currentTarget.dataset.q)
    },

    onSend() {
      this.ask(this.data.input)
    },

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
        if (this._alive) this.setData({ sending: false })
      }
    },

    replaceMessage(id, next) {
      const messages = this.data.messages.map(m => (m.id === id ? next : m))
      this.setData({ messages }, () => this.scrollBottom())
    },

    scrollBottom() {
      if (!this._alive) return
      this._scrollTick = (this._scrollTick || 0) + 1
      this.setData({ scrollTop: 100000 + this._scrollTick })
    },

    openArticle(e) {
      wx.navigateTo({ url: '/pages/kb-detail/kb-detail?id=' + e.currentTarget.dataset.id })
    },

    goReport(e) {
      const type = e.currentTarget.dataset.type
      this.setFull(false)
      const app = getApp()
      if (type) app.globalData.homeFaultType = type
      else app.globalData.reportView = 'form'
      wx.switchTab({ url: '/pages/report/report' })
    },
  },
})
