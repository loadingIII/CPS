/**
 * 轻量 Markdown → rich-text HTML。
 *
 * 小程序没有 DOM，也不方便为这一个页面引 npm 包，所以自己转：
 * 支持标题、段落、粗体/斜体/删除线、行内代码、代码块、引用、有序/无序列表、
 * 表格、分割线、链接、图片；所有样式写成内联，rich-text 不依赖页面 wxss。
 */
const { BASE } = require('./request')

const S = {
  h1: 'font-size:20px;font-weight:800;color:#22343a;line-height:1.45;margin:20px 0 12px;',
  h2: 'font-size:18px;font-weight:800;color:#22343a;line-height:1.5;margin:20px 0 10px;',
  h3: 'font-size:16.5px;font-weight:700;color:#2b3a3c;line-height:1.55;margin:16px 0 8px;',
  h4: 'font-size:15.5px;font-weight:700;color:#2b3a3c;line-height:1.6;margin:14px 0 6px;',
  p: 'margin:0 0 10px;',
  quote: 'margin:10px 0;padding:10px 14px;border-left:3px solid #bcdcdc;background:#f6fbfb;color:#52686b;border-radius:0 8px 8px 0;',
  code: 'font-family:Menlo,Consolas,monospace;font-size:13px;color:#0a5f62;background:#eef4f4;padding:1px 5px;border-radius:4px;',
  pre: 'margin:10px 0;padding:12px;background:#f5f8f8;border:1px solid #e6ecec;border-radius:8px;font-family:Menlo,Consolas,monospace;font-size:12.5px;line-height:1.65;color:#33464a;white-space:pre-wrap;word-break:break-all;',
  ul: 'margin:6px 0 10px;padding-left:20px;',
  ol: 'margin:6px 0 10px;padding-left:20px;',
  li: 'margin:4px 0;',
  table: 'width:100%;border-collapse:collapse;margin:10px 0;font-size:13px;line-height:1.6;',
  th: 'border:1px solid #dbe4e4;background:#f0f6f6;padding:7px 8px;text-align:left;font-weight:700;color:#2b3a3c;',
  td: 'border:1px solid #e6ecec;padding:7px 8px;vertical-align:top;',
  hr: 'border:none;border-top:1px dashed #d8e0e0;margin:18px 0;',
  img: 'max-width:100%;border-radius:8px;margin:8px 0;',
  link: 'color:#0f7b7e;text-decoration:underline;',
}

function escapeHtml(text) {
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function resolveUrl(url) {
  const value = String(url || '').trim()
  if (!value) return ''
  if (/^(https?:)?\/\//i.test(value) || value.startsWith('data:')) return value
  if (value.startsWith('/')) return BASE + value
  return value
}

/** 行内语法：先摘出行内代码，避免其中的 * _ 被当成强调 */
function inline(raw) {
  const codes = []
  let text = String(raw || '').replace(/`([^`]+)`/g, (_, code) => {
    codes.push('<span style="' + S.code + '">' + escapeHtml(code) + '</span>')
    return '\u0000' + (codes.length - 1) + '\u0000'
  })

  text = escapeHtml(text)
  text = text.replace(/!\[([^\]]*)\]\(([^)\s]+)\)/g,
    (_, alt, src) => '<img src="' + resolveUrl(src) + '" alt="' + alt + '" style="' + S.img + '" />')
  text = text.replace(/\[([^\]]+)\]\(([^)\s]+)\)/g,
    (_, label, href) => '<span style="' + S.link + '">' + label + '</span>')
  text = text.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  text = text.replace(/__([^_]+)__/g, '<strong>$1</strong>')
  text = text.replace(/~~([^~]+)~~/g, '<del>$1</del>')
  text = text.replace(/(^|[^*])\*([^*\n]+)\*/g, '$1<em>$2</em>')
  text = text.replace(/\u0000(\d+)\u0000/g, (_, i) => codes[Number(i)] || '')
  return text
}

const RE = {
  heading: /^(#{1,6})\s+(.*)$/,
  hr: /^\s{0,3}([-*_])\s*(?:\1\s*){2,}$/,
  quote: /^\s{0,3}>\s?(.*)$/,
  ul: /^\s*([-*+])\s+(.*)$/,
  ol: /^\s*(\d+)[.)]\s+(.*)$/,
  fence: /^\s*```(\w*)\s*$/,
  tableSep: /^\s*\|?[\s:|-]+\|[\s:|-]*$/,
}

function isTableStart(lines, i) {
  return lines[i].indexOf('|') >= 0
    && i + 1 < lines.length
    && lines[i + 1].indexOf('|') >= 0
    && RE.tableSep.test(lines[i + 1])
}

function splitRow(line) {
  let text = line.trim()
  if (text.startsWith('|')) text = text.slice(1)
  if (text.endsWith('|')) text = text.slice(0, -1)
  return text.split('|').map(cell => cell.trim())
}

function renderTable(lines, start) {
  const header = splitRow(lines[start])
  const rows = []
  let i = start + 2
  while (i < lines.length && lines[i].indexOf('|') >= 0 && lines[i].trim() !== '') {
    rows.push(splitRow(lines[i]))
    i++
  }

  let html = '<table style="' + S.table + '"><thead><tr>'
  header.forEach(cell => { html += '<th style="' + S.th + '">' + inline(cell) + '</th>' })
  html += '</tr></thead><tbody>'
  rows.forEach(row => {
    html += '<tr>'
    header.forEach((_, idx) => {
      html += '<td style="' + S.td + '">' + inline(row[idx] || '') + '</td>'
    })
    html += '</tr>'
  })
  html += '</tbody></table>'
  return { html, next: i }
}

function renderList(lines, start) {
  const ordered = RE.ol.test(lines[start])
  const pattern = ordered ? RE.ol : RE.ul
  const items = []
  let i = start

  while (i < lines.length) {
    const match = lines[i].match(pattern)
    if (!match) break
    items.push(match[2])
    i++
    // 列表项续行（缩进且不是新的列表项）
    while (i < lines.length && /^\s{2,}\S/.test(lines[i]) && !pattern.test(lines[i])) {
      items[items.length - 1] += '<br/>' + lines[i].trim()
      i++
    }
  }

  const tag = ordered ? 'ol' : 'ul'
  const style = ordered ? S.ol : S.ul
  let html = '<' + tag + ' style="' + style + '">'
  items.forEach(item => { html += '<li style="' + S.li + '">' + inline(item) + '</li>' })
  html += '</' + tag + '>'
  return { html, next: i }
}

/** 主入口：Markdown → HTML 字符串 */
function toHtml(markdown) {
  if (!markdown) return ''
  const lines = String(markdown)
    .replace(/^\uFEFF/, '')
    .replace(/^---\r?\n[\s\S]*?\r?\n---\r?\n?/, '')
    .replace(/\r\n?/g, '\n')
    .split('\n')

  const out = []
  let i = 0

  while (i < lines.length) {
    const line = lines[i]

    if (!line.trim()) { i++; continue }

    // 代码块
    const fence = line.match(RE.fence)
    if (fence) {
      const buffer = []
      i++
      while (i < lines.length && !/^\s*```/.test(lines[i])) {
        buffer.push(lines[i])
        i++
      }
      i++ // 跳过结束围栏
      out.push('<pre style="' + S.pre + '"><code>' + escapeHtml(buffer.join('\n')) + '</code></pre>')
      continue
    }

    // 标题
    const heading = line.match(RE.heading)
    if (heading) {
      const level = Math.min(heading[1].length, 6)
      const style = S['h' + Math.min(level, 4)]
      out.push('<h' + level + ' style="' + style + '">' + inline(heading[2]) + '</h' + level + '>')
      i++
      continue
    }

    // 分割线
    if (RE.hr.test(line)) {
      out.push('<hr style="' + S.hr + '" />')
      i++
      continue
    }

    // 引用
    if (RE.quote.test(line)) {
      const buffer = []
      while (i < lines.length && RE.quote.test(lines[i])) {
        buffer.push(lines[i].match(RE.quote)[1])
        i++
      }
      out.push('<blockquote style="' + S.quote + '">' + inline(buffer.join('\n')).replace(/\n/g, '<br/>') + '</blockquote>')
      continue
    }

    // 表格
    if (isTableStart(lines, i)) {
      const table = renderTable(lines, i)
      out.push(table.html)
      i = table.next
      continue
    }

    // 列表
    if (RE.ul.test(line) || RE.ol.test(line)) {
      const list = renderList(lines, i)
      out.push(list.html)
      i = list.next
      continue
    }

    // 段落：连续非空行合并，保留换行
    const buffer = []
    while (i < lines.length && lines[i].trim() && !isBlockStart(lines, i)) {
      buffer.push(lines[i])
      i++
    }
    out.push('<p style="' + S.p + '">' + inline(buffer.join('\n')).replace(/\n/g, '<br/>') + '</p>')
  }

  return out.join('')
}

function isBlockStart(lines, i) {
  const line = lines[i]
  return RE.heading.test(line)
    || RE.hr.test(line)
    || RE.quote.test(line)
    || RE.fence.test(line)
    || RE.ul.test(line)
    || RE.ol.test(line)
    || isTableStart(lines, i)
}

module.exports = { toHtml }
