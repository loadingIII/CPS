/**
 * 生成工单地图标记图标（纯 Node 内置库手写 PNG 编码，无需图片素材）：
 *   marker-mine.png 青色实心圆（已接的单）
 *   marker-grab.png 琥珀实心圆（可接的单）
 * 运行：node scripts/gen-markers.mjs <CPS目录>（默认当前工作目录）
 */
import zlib from 'node:zlib'
import fs from 'node:fs'
import path from 'node:path'

function crc32(buf) {
  if (!crc32.table) {
    crc32.table = new Int32Array(256)
    for (let n = 0; n < 256; n++) {
      let c = n
      for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
      crc32.table[n] = c
    }
  }
  let c = 0xffffffff
  for (const b of buf) c = crc32.table[(c ^ b) & 0xff] ^ (c >>> 8)
  return (c ^ 0xffffffff) >>> 0
}

function chunk(type, data) {
  const t = Buffer.from(type, 'ascii')
  const len = Buffer.alloc(4)
  len.writeUInt32BE(data.length)
  const crc = Buffer.alloc(4)
  crc.writeUInt32BE(crc32(Buffer.concat([t, data])))
  return Buffer.concat([len, t, data, crc])
}

function makeMarker(rgb, file) {
  const W = 48, H = 48, cx = 24, cy = 24, rIn = 15, rOut = 21
  const rows = []
  for (let y = 0; y < H; y++) {
    const row = Buffer.alloc(1 + W * 4)
    row[0] = 0
    for (let x = 0; x < W; x++) {
      const d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy)
      const o = 1 + x * 4
      let r = 0, g = 0, b = 0, a = 0
      if (d <= rIn) {
        ;[r, g, b, a] = [rgb[0], rgb[1], rgb[2], 255]
      } else if (d <= rOut) {
        ;[r, g, b, a] = [255, 255, 255, 255]
      } else if (d <= rOut + 1.5) {
        ;[r, g, b] = [255, 255, 255]
        a = Math.max(0, Math.round(255 * (1 - (d - rOut) / 1.5)))
      }
      row[o] = r; row[o + 1] = g; row[o + 2] = b; row[o + 3] = a
    }
    rows.push(row)
  }
  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(W, 0)
  ihdr.writeUInt32BE(H, 4)
  ihdr[8] = 8   // bit depth
  ihdr[9] = 6   // RGBA
  const png = Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(Buffer.concat(rows))),
    chunk('IEND', Buffer.alloc(0)),
  ])
  fs.mkdirSync(path.dirname(file), { recursive: true })
  fs.writeFileSync(file, png)
  console.log('wrote', file, png.length, 'bytes')
}

const root = path.resolve(process.argv[2] || '.', 'miniprogram/assets')
makeMarker([14, 74, 84], path.join(root, 'marker-mine.png'))   // 砚青
makeMarker([217, 106, 59], path.join(root, 'marker-grab.png')) // 琥珀
