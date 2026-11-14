const { post } = require('../../utils/request')
const { streamChat, streamSupported } = require('../../utils/sse')

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
      this._conversationId = 'c' + Date.now().toString(36)
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
      this._stopTypewriter()
      if (this._task && this._task.abort) this._task.abort()
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
      if (!this.data.full) this.setFull(true)

      const userMsg = { id: nextId(), role: 'user', text, articles: [] }
      const reply = { id: nextId(), role: 'agent', text: '', articles: [], streaming: true }
      const messages = this.data.messages.concat(userMsg, reply)
      this.setData({ messages, input: '', sending: true }, () => this.scrollBottom())
      this.runStream(text, messages.length - 1)
    },

    /** 失败重试：复用原气泡，不重复插入用户消息 */
    retry(e) {
      const index = Number(e.currentTarget.dataset.idx)
      if (this.data.sending || Number.isNaN(index) || !this._lastQuestion) return
      this.setData({
        [`messages[${index}].text`]: '',
        [`messages[${index}].failed`]: false,
        [`messages[${index}].errorText`]: '',
        [`messages[${index}].streaming`]: true,
        sending: true,
      })
      this.runStream(this._lastQuestion, index)
    },

    runStream(question, index) {
      this._lastQuestion = question
      this._streamIndex = index
      this._streamText = ''
      this._queue = ''
      this._finished = false
      this._pendingFinish = false
      this._refs = []
      this._doneInfo = null
      this._startTypewriter()

      const onEvent = (event, data) => {
        if (event === 'start') {
          if (data && data.conversationId) this._conversationId = data.conversationId
        } else if (event === 'refs') {
          this._refs = Array.isArray(data) ? data : []
        } else if (event === 'delta') {
          this._queue += (data && data.t) || ''
        } else if (event === 'done') {
          this._doneInfo = data || {}
          this.finishStream()
        } else if (event === 'error') {
          this.failStream(data)
        }
      }

      if (streamSupported()) {
        this._task = streamChat({
          data: { message: question, conversationId: this._conversationId },
          onEvent,
          onError: err => this.failStream(err),
          onComplete: () => this.finishStream(),
        })
        return
      }

      // 低版本基础库不支持分块接收：退回同步接口，前端仍逐字渲染
      post('/agent/chat', { message: question, conversationId: this._conversationId })
        .then(d => {
          this._queue += d.answer || ''
          this._refs = d.articles || []
          this._doneInfo = { suggestedFaultType: d.suggestedFaultType }
          this.finishStream()
        })
        .catch(err => this.failStream({ message: err.message }))
    },

    /* ---------- 打字机：把增量文本匀速吐到气泡里 ---------- */
    _startTypewriter() {
      this._stopTypewriter()
      this._typer = setInterval(() => {
        if (this._queue) {
          const len = this._queue.length
          const take = len > 120 ? 10 : len > 60 ? 6 : len > 20 ? 3 : 1
          this._streamText += this._queue.slice(0, take)
          this._queue = this._queue.slice(take)
          this.flushText()
        }
        // 流已结束但还有积压：等吐完再收尾，保证降级模式也是逐字出现
        if (this._pendingFinish && !this._queue) {
          this._pendingFinish = false
          this.finishStream()
        }
      }, 32)
    },

    _stopTypewriter() {
      if (this._typer) {
        clearInterval(this._typer)
        this._typer = null
      }
    },

    flushText() {
      if (!this._alive || this._streamIndex == null) return
      this.setData({ [`messages[${this._streamIndex}].text`]: this._streamText })
      this._scrollTickCount = (this._scrollTickCount || 0) + 1
      if (this._scrollTickCount % 6 === 0) this.scrollBottom()
    },

    finishStream() {
      if (this._finished) return
      // 还有没吐完的字：交给打字机收尾
      if (this._queue) {
        this._pendingFinish = true
        return
      }
      this._finished = true
      this._stopTypewriter()
      const i = this._streamIndex
      const text = this._streamText || '我暂时没有找到答案，可以直接提交报修。'
      const done = this._doneInfo || {}
      this.setData({
        [`messages[${i}].text`]: text,
        [`messages[${i}].streaming`]: false,
        [`messages[${i}].failed`]: false,
        [`messages[${i}].errorText`]: '',
        [`messages[${i}].articles`]: this._refs || [],
        [`messages[${i}].faultType`]: done.suggestedFaultType || '',
        sending: false,
      }, () => this.scrollBottom())
      this._task = null
    },

    failStream(err) {
      if (this._finished || this._pendingFinish) return
      this._finished = true
      this._stopTypewriter()
      if (this._queue) {
        this._streamText += this._queue
        this._queue = ''
      }
      const i = this._streamIndex
      const message = (err && err.message) || '智能助手开小差了，请重试或直接提交报修'
      this.setData({
        [`messages[${i}].text`]: this._streamText || message,
        [`messages[${i}].streaming`]: false,
        [`messages[${i}].failed`]: true,
        [`messages[${i}].errorText`]: this._streamText ? '回答中断，可重试' : '',
        sending: false,
      }, () => this.scrollBottom())
      this._task = null
    },

    scrollBottom() {
      if (!this._alive) return
      this._scrollTick = (this._scrollTick || 0) + 1
      this.setData({ scrollTop: 100000 + this._scrollTick })
    },

    openArticle(e) {
      const id = e.currentTarget.dataset.id
      if (!id) return
      wx.navigateTo({ url: '/pages/kb-detail/kb-detail?id=' + id })
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
