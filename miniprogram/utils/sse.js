/**
 * SSE 流式请求：wx.request + enableChunked + 自写 UTF-8 流式解码。
 *
 * 中文一个字符可能被切在两个 chunk 里，必须保留半包字节再拼，
 * 不能用 decodeURIComponent / TextDecoder 一把梭（部分基础库没有 TextDecoder）。
 */
const { BASE } = require('./request')

/** 基础库是否支持分块接收（2.20.2 起） */
function streamSupported() {
  try {
    const info = wx.getAppBaseInfo ? wx.getAppBaseInfo() : wx.getSystemInfoSync()
    const version = String(info.SDKVersion || '')
    const parts = version.split('.').map(n => parseInt(n, 10) || 0)
    const [major = 0, minor = 0, patch = 0] = parts
    if (major !== 2) return major > 2
    if (minor !== 20) return minor > 20
    return patch >= 2
  } catch (e) {
    return false
  }
}

function utf8ToString(bytes) {
  let out = ''
  let i = 0
  while (i < bytes.length) {
    const b = bytes[i++]
    if (b < 0x80) {
      out += String.fromCharCode(b)
    } else if (b < 0xe0) {
      out += String.fromCharCode(((b & 0x1f) << 6) | (bytes[i++] & 0x3f))
    } else if (b < 0xf0) {
      out += String.fromCharCode(
        ((b & 0x0f) << 12) | ((bytes[i++] & 0x3f) << 6) | (bytes[i++] & 0x3f)
      )
    } else {
      const cp = ((b & 0x07) << 18) | ((bytes[i++] & 0x3f) << 12)
        | ((bytes[i++] & 0x3f) << 6) | (bytes[i++] & 0x3f)
      const v = cp - 0x10000
      out += String.fromCharCode(0xd800 + (v >> 10), 0xdc00 + (v & 0x3ff))
    }
  }
  return out
}

/** 返回 decode(ArrayBuffer) → string，内部保留不完整的多字节尾巴 */
function createUtf8Decoder() {
  let pending = []
  return function decode(arrayBuffer) {
    const incoming = new Uint8Array(arrayBuffer)
    let bytes
    if (pending.length) {
      bytes = new Uint8Array(pending.length + incoming.length)
      bytes.set(pending, 0)
      bytes.set(incoming, pending.length)
    } else {
      bytes = incoming
    }

    // 从尾部回退，判断最后一个字符是否被截断
    let end = bytes.length
    let i = end - 1
    let continuation = 0
    while (i >= 0 && (bytes[i] & 0xc0) === 0x80) {
      continuation += 1
      i -= 1
    }
    if (i >= 0) {
      const lead = bytes[i]
      let need = 1
      if (lead >= 0xf0) need = 4
      else if (lead >= 0xe0) need = 3
      else if (lead >= 0xc0) need = 2
      if (continuation + 1 < need) end = i
    }

    const text = utf8ToString(bytes.subarray(0, end))
    pending = Array.prototype.slice.call(bytes.subarray(end))
    return text
  }
}

/** 解析 SSE 帧：event + 多行 data，data 尝试按 JSON 解 */
function createSseParser(onEvent) {
  let buffer = ''

  function handleFrame(frame) {
    let event = 'message'
    const dataLines = []
    frame.split('\n').forEach(rawLine => {
      const line = rawLine.replace(/\r$/, '')
      if (!line || line.charAt(0) === ':') return
      const pos = line.indexOf(':')
      const field = pos === -1 ? line : line.slice(0, pos)
      let value = pos === -1 ? '' : line.slice(pos + 1)
      if (value.charAt(0) === ' ') value = value.slice(1)
      if (field === 'event') event = value
      else if (field === 'data') dataLines.push(value)
    })
    if (!dataLines.length) return
    const raw = dataLines.join('\n')
    let data = raw
    try {
      data = JSON.parse(raw)
    } catch (e) { /* 纯文本保持原样 */ }
    onEvent(event, data)
  }

  return {
    feed(text) {
      if (!text) return
      buffer += text
      let index = buffer.indexOf('\n\n')
      while (index >= 0) {
        handleFrame(buffer.slice(0, index))
        buffer = buffer.slice(index + 2)
        index = buffer.indexOf('\n\n')
      }
    },
    flush() {
      if (buffer.trim()) {
        handleFrame(buffer)
        buffer = ''
      }
    },
  }
}

/**
 * 发起流式问答。
 * @returns {{abort:Function}} 可中断句柄
 */
function streamChat(options) {
  const { data, onEvent, onError, onComplete } = options
  const decode = createUtf8Decoder()
  const parser = createSseParser(onEvent)
  let finished = false

  const task = wx.request({
    url: BASE + '/api/agent/chat/stream',
    method: 'POST',
    data,
    timeout: 120000,
    enableChunked: true,
    responseType: 'arraybuffer',
    header: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      Authorization: 'Bearer ' + (wx.getStorageSync('token') || ''),
    },
    success(res) {
      if (finished) return
      if (res.statusCode === 401) {
        finished = true
        wx.clearStorageSync()
        wx.reLaunch({ url: '/pages/login/login' })
        return
      }
      const header = res.header || {}
      const contentType = String(header['Content-Type'] || header['content-type'] || '')
      // 参数校验 / 业务异常时后端返回 JSON（HTTP 仍为 200），这里按错误处理
      if (contentType.indexOf('text/event-stream') < 0) {
        finished = true
        let body = null
        try {
          const raw = res.data && res.data.byteLength ? decode(res.data) : ''
          body = raw ? JSON.parse(raw) : null
        } catch (e) { /* 非 JSON 忽略 */ }
        onError({
          code: (body && body.code) || ('HTTP_' + res.statusCode),
          message: (body && body.message) || '服务暂时不可用，请稍后重试',
        })
        return
      }
      if (res.statusCode !== 200) {
        finished = true
        onError({ code: 'HTTP_' + res.statusCode, message: '服务暂时不可用，请稍后重试' })
        return
      }
      // 极少数基础库不触发 onChunkReceived，会一次性回完整数据
      if (res.data && res.data.byteLength) {
        parser.feed(decode(res.data))
      }
      parser.flush()
      finished = true
      onComplete && onComplete()
    },
    fail() {
      if (finished) return
      finished = true
      onError({ code: 'NETWORK', message: '网络开小差了，请检查后端是否启动' })
    },
  })

  if (typeof task.onChunkReceived === 'function') {
    task.onChunkReceived(res => {
      if (finished) return
      parser.feed(decode(res.data))
    })
  }

  return {
    abort() {
      if (finished) return
      finished = true
      try { task.abort() } catch (e) { /* 忽略 */ }
    },
  }
}

module.exports = {
  streamSupported,
  streamChat,
  createSseParser,
  createUtf8Decoder,
}
