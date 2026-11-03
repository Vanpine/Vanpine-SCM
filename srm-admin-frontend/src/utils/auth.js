/**
 * SRM Admin - Token 与用户信息存储工具
 * 使用 localStorage 持久化认证信息
 */
import { TOKEN_KEY, REFRESH_TOKEN_KEY, USER_INFO_KEY } from '@/constants'

/** 获取 accessToken */
export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

/** 设置 accessToken */
export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
}

/** 删除 accessToken */
export function removeToken() {
  localStorage.removeItem(TOKEN_KEY)
}

/** 获取 refreshToken */
export function getRefreshToken() {
  return localStorage.getItem(REFRESH_TOKEN_KEY)
}

/** 设置 refreshToken */
export function setRefreshToken(token) {
  localStorage.setItem(REFRESH_TOKEN_KEY, token)
}

/** 删除 refreshToken */
export function removeRefreshToken() {
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}

/** 获取用户信息 */
export function getUserInfo() {
  const raw = localStorage.getItem(USER_INFO_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

/** 设置用户信息 */
export function setUserInfo(info) {
  localStorage.setItem(USER_INFO_KEY, JSON.stringify(info))
}

/** 删除用户信息 */
export function removeUserInfo() {
  localStorage.removeItem(USER_INFO_KEY)
}

/** 清除所有认证信息 */
export function clearAuth() {
  removeToken()
  removeRefreshToken()
  removeUserInfo()
}
