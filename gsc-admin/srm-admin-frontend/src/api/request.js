/**
 * SRM Admin - Axios 请求实例
 * 封装：Token 注入、Token 过期自动刷新、统一错误处理
 * 注意：不设全局 Loading，各页面用 v-loading 自行管理加载状态
 */
import axios from 'axios'
import { getToken, setToken, getRefreshToken, setRefreshToken, clearAuth } from '@/utils/auth'
import { REQUEST_TIMEOUT } from '@/constants'
import { ElMessage } from 'element-plus'

// 创建 axios 实例
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: REQUEST_TIMEOUT,
  headers: { 'Content-Type': 'application/json' },
})

// 是否正在刷新 Token
let isRefreshing = false
let pendingRequests = []

function resolvePendingRequests(newToken) {
  pendingRequests.forEach(({ resolve, reject }) => {
    if (newToken) { resolve(newToken) } else { reject(new Error('Token 刷新失败')) }
  })
  pendingRequests = []
}

async function refreshAccessToken() {
  const refreshToken = getRefreshToken()
  if (!refreshToken) throw new Error('无 refreshToken')
  try {
    const { data } = await axios.post(`${import.meta.env.VITE_API_BASE || '/api'}/auth/refresh-token`, { refreshToken })
    if (data.code === 200) {
      const { accessToken, refreshToken: newRefreshToken } = data.data
      setToken(accessToken)
      setRefreshToken(newRefreshToken)
      return accessToken
    }
    throw new Error(data.message || 'Token 刷新失败')
  } catch (error) {
    clearAuth()
    throw error
  }
}

// ---- 请求拦截器 ----
request.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) config.headers.Authorization = `Bearer ${token}`
    return config
  },
  (error) => Promise.reject(error),
)

/**
 * 修复分页字段被后端 Long→String 序列化误伤成字符串的问题。
 * 后端 JacksonConfig 为了雪花主键精度，把 long 统一序列化成字符串，
 * 导致 total/current/size/pages 等分页数字变成 "8"，而 el-pagination 对 total
 * 有严格 Number 校验，收到字符串会直接拒绝渲染 → 分页条全站消失。
 * 这里仅把明确属于"分页数字"的字段转回 Number，绝不动 id 等主键（保持精度兜底）。
 */
function normalizePageNumbers(data) {
  if (data && typeof data === 'object') {
    const numberFields = ['total', 'pages', 'size', 'current']
    numberFields.forEach((key) => {
      if (key in data && data[key] !== null && data[key] !== undefined) {
        const num = Number(data[key])
        if (Number.isFinite(num)) data[key] = num
      }
    })
  }
}

/**
 * 统一错误提示：轮询类接口传入 silentError 时不弹窗，避免发版窗口的 502 打扰用户。
 */
function showError(config, message) {
  if (config?.silentError) return
  ElMessage.error(message)
}

// ---- 响应拦截器 ----
request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code !== 200) {
      showError(config, res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    // 分页数据通常挂在 res.data 上，统一修复 total 等字段的类型
    normalizePageNumbers(res.data)
    return res
  },
  async (error) => {
    const { response, config } = error
    if (!response) {
      showError(config, '网络连接失败，请检查网络')
      return Promise.reject(error)
    }
    const { status, data } = response
    if (status === 401) {
      const publicPaths = ['/auth/login', '/auth/refresh-token', '/auth/forgot-password', '/auth/reset-password']
      if (publicPaths.some((p) => config.url?.includes(p))) {
        return Promise.reject(error)
      }
      if (!isRefreshing) {
        isRefreshing = true
        try {
          const newToken = await refreshAccessToken()
          isRefreshing = false
          resolvePendingRequests(newToken)
          config.headers.Authorization = `Bearer ${newToken}`
          return request(config)
        } catch (refreshError) {
          isRefreshing = false
          resolvePendingRequests(null)
          // 立即清空 Token 并跳转登录页，消息通过 URL 参数传递避免重复请求循环
          const msg = data?.message || '登录已过期，请重新登录'
          clearAuth()
          window.location.href = '/login?reason=' + encodeURIComponent(msg)
          return Promise.reject(refreshError)
        }
      }
      return new Promise((resolve, reject) => {
        pendingRequests.push({
          resolve: (newToken) => {
            config.headers.Authorization = `Bearer ${newToken}`
            resolve(request(config))
          },
          reject,
        })
      })
    }
    if (status === 403) {
      showError(config, data?.message || '无权限访问')
      return Promise.reject(error)
    }
    showError(config, data?.message || '请求失败')
    return Promise.reject(error)
  },
)

export default request
