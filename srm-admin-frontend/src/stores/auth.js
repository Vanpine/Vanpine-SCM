/**
 * SRM Admin - 认证状态管理
 * 管理登录/登出、Token 存储、用户基础信息
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, logout as logoutApi, getCurrentUser, updateProfile as updateProfileApi } from '@/api/auth'
import {
  getToken,
  setToken,
  getRefreshToken,
  setRefreshToken,
  setUserInfo,
  clearAuth,
} from '@/utils/auth'

export const useAuthStore = defineStore('auth', () => {
  // ---- state ----
  const token = ref(getToken() || '')
  const refreshTokenValue = ref(getRefreshToken() || '')
  const user = ref(null)
  /** 企业画像类型：buyer | supplier | both | null（未加载） */
  const profileType = ref(null)

  // ---- getters ----
  const isLoggedIn = computed(() => !!token.value)
  const userId = computed(() => user.value?.id)
  const username = computed(() => user.value?.username)
  const tenantId = computed(() => user.value?.tenantId)
  const tenantCode = computed(() => user.value?.tenantCode)
  const isAdmin = computed(() => {
    const roles = user.value?.roles || []
    return roles.includes('SUPER_ADMIN') || roles.includes('TENANT_ADMIN')
  })

  // ---- actions ----

  /**
   * 登录
   * 调用后端 /api/auth/login，获取 accessToken + refreshToken + 用户信息 + 菜单
   * @param {Object} params - { tenantCode, username, password }
   * @returns {Promise} 后端返回的完整登录响应数据
   */
  async function login(params) {
    // 调用后端登录接口
    const { data } = await loginApi(params)

    // 将 token 存到内存状态和 localStorage
    token.value = data.accessToken
    refreshTokenValue.value = data.refreshToken
    setToken(data.accessToken)
    setRefreshToken(data.refreshToken)

    // 存储用户信息到内存和 localStorage
    if (data.userInfo) {
      user.value = data.userInfo
      setUserInfo(data.userInfo)
    }

    return data
  }

  /**
   * 登出
   * 调用后端 /api/auth/logout，然后清除前端所有状态
   */
  async function logout() {
    try {
      // 调用后端登出接口（通知服务端清除 token 白名单）
      await logoutApi()
    } catch {
      // 即使后端登出失败，也清除前端状态
    } finally {
      token.value = ''
      refreshTokenValue.value = ''
      user.value = null
      clearAuth()  // 清除 localStorage 中的 token 和用户信息
    }
  }

  /**
   * 更新当前用户个人信息
   * 调用后端 /api/auth/profile，成功后同步更新内存和 localStorage
   * @param {Object} params - { username, realName, email, phone, avatarKey }
   */
  async function updateProfile(params) {
    // 调用后端更新个人资料接口
    await updateProfileApi(params)
    // 刷新本地用户信息（避免重新请求 /auth/me）
    if (user.value) {
      if (params.username !== undefined) user.value.username = params.username
      if (params.realName !== undefined) user.value.realName = params.realName
      if (params.email !== undefined) user.value.email = params.email
      if (params.phone !== undefined) user.value.phone = params.phone
      // 注意：avatarKey 是提交用的 OSS 对象路径，本地展示仍靠 avatarUrl（后端现签返回）
    }
    // 同步更新 localStorage 中的用户信息
    const { setUserInfo } = await import('@/utils/auth')
    setUserInfo({ ...user.value })
  }

  /**
   * 刷新用户信息（用于路由守卫恢复状态）
   * 调用后端 /api/auth/me，获取当前用户完整信息
   * @returns {Promise} 用户信息对象
   */
  async function fetchUserInfo() {
    try {
      // 调用后端获取当前用户信息接口
      const { data } = await getCurrentUser()
      user.value = data
      setUserInfo(data)  // 同步到 localStorage
      return data
    } catch {
      clearAuth()
      token.value = ''
      user.value = null
      throw new Error('获取用户信息失败')
    }
  }

  /**
   * 从 localStorage 恢复登录状态
   * 用于刷新页面后恢复 session
   */
  function restoreSession() {
    const storedToken = getToken()
    if (storedToken) {
      token.value = storedToken
      refreshTokenValue.value = getRefreshToken() || ''
    }
  }

  /**
   * 加载企业画像类型（供路由守卫和 Sidebar 使用）
   * 超管（tenantId=1）无需企业画像，直接设为 both
   * 失败时默认 supplier，不阻塞页面渲染
   */
  async function loadProfileType() {
    // 超管不需要企业画像
    // 超管不需要企业画像（后端 Long 序列化为 String，用 String() 兼容旧缓存 number）
    if (String(tenantId.value) === '1') {
      profileType.value = 'both'
      return
    }
    try {
      const { getProfile } = await import('@/api/match')
      const { data } = await getProfile()
      profileType.value = data?.profileType || 'supplier'
    } catch {
      profileType.value = 'supplier'
    }
  }

  return {
    // state
    token,
    refreshTokenValue,
    user,
    // getters
    isLoggedIn,
    userId,
    username,
    tenantId,
    tenantCode,
    isAdmin,
    profileType,
    // actions
    login,
    logout,
    fetchUserInfo,
    restoreSession,
    updateProfile,
    loadProfileType,
  }
})
