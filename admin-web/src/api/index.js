import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

/**
 * 统一请求封装：自动携带 token；code!==0 统一报错并 reject；401 清登录态跳登录页。
 * 成功时直接返回 Result.data。
 */
const api = axios.create({ baseURL: '/api', timeout: 15000 })

api.interceptors.request.use(cfg => {
  const token = localStorage.getItem('token')
  if (token) cfg.headers.Authorization = 'Bearer ' + token
  return cfg
})

api.interceptors.response.use(
  res => {
    const { code, message, data } = res.data || {}
    if (code === 0) return data
    if (code === 40101) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      router.push('/login')
    }
    ElMessage.error(message || '请求失败')
    return Promise.reject(new Error(message || '请求失败'))
  },
  err => {
    const status = err.response?.status
    const body = err.response?.data
    if (status === 401 || body?.code === 40101) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      router.push('/login')
      ElMessage.error('登录已过期，请重新登录')
    } else {
      ElMessage.error(body?.message || `请求失败(${status || '网络错误'})`)
    }
    return Promise.reject(err)
  }
)

export default api
