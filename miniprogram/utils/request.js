/**
 * 统一请求封装：自动携带 token；code===0 解包返回 data；
 * 401/40101 清登录态回登录页；其余错误 toast。
 *
 * 后端主机按运行环境自动切换：
 * - 开发者工具模拟器 → localhost
 * - 真机（预览 / 真机调试）→ 电脑局域网 IPv4
 * 电脑 IP 变了只需改 LAN_HOST 一行（ipconfig 查看 IPv4 地址）。
 */
const DEVTOOLS_HOST = 'http://localhost:8080'
const LAN_HOST = 'http://10.187.57.197:8080'

function detectPlatform() {
  try {
    if (wx.getDeviceInfo) {
      return wx.getDeviceInfo().platform
    }
  } catch (e) { /* 继续走兼容路径 */ }
  try {
    return wx.getSystemInfoSync().platform
  } catch (e) {
    return 'devtools'
  }
}

const isDevtools = detectPlatform() === 'devtools'
const BASE = isDevtools ? DEVTOOLS_HOST : LAN_HOST

// 所有业务接口统一挂在 /api 前缀下（BASE 仅作图片 /uploads/** 的主机前缀）
const API = BASE + '/api'

// 显式 10s 超时：真机连不上时快速失败（默认 60s 会长时间挂起）
const TIMEOUT = 10000

function request(method, path, data) {
  return new Promise((resolve, reject) => {
    wx.request({
      url: API + path,
      method,
      data,
      timeout: TIMEOUT,
      header: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer ' + (wx.getStorageSync('token') || ''),
      },
      success(res) {
        const body = res.data || {}
        if (res.statusCode === 401 || body.code === 40101) {
          wx.clearStorageSync()
          wx.reLaunch({ url: '/pages/login/login' })
          reject(new Error('未登录'))
          return
        }
        if (body.code === 0) {
          resolve(body.data)
        } else {
          wx.showToast({ title: body.message || '请求失败', icon: 'none' })
          reject(new Error(body.message || '请求失败'))
        }
      },
      fail() {
        wx.showToast({
          title: isDevtools
            ? '网络错误，请确认后端服务已启动'
            : '手机连不上电脑后端：请确认同一网络、防火墙放行 8080',
          icon: 'none',
          duration: 3000,
        })
        reject(new Error('网络错误'))
      },
    })
  })
}

/** 上传图片（wx.uploadFile），返回 fileUrl */
function upload(filePath) {
  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: API + '/files',
      filePath,
      name: 'file',
      timeout: TIMEOUT,
      header: { Authorization: 'Bearer ' + (wx.getStorageSync('token') || '') },
      success(res) {
        let body = {}
        try { body = JSON.parse(res.data) } catch (e) { /* ignore */ }
        if (body.code === 0 && body.data && body.data.fileUrl) {
          resolve(body.data.fileUrl)
        } else {
          wx.showToast({ title: body.message || '图片上传失败', icon: 'none' })
          reject(new Error(body.message || '上传失败'))
        }
      },
      fail() {
        wx.showToast({ title: '图片上传失败，请检查网络', icon: 'none' })
        reject(new Error('上传失败'))
      },
    })
  })
}

module.exports = {
  BASE,
  isDevtools,
  get: (path) => request('GET', path),
  post: (path, data) => request('POST', path, data),
  put: (path, data) => request('PUT', path, data),
  upload,
}
