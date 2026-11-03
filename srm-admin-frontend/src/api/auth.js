/**
 * SRM Admin - 认证模块 API
 */
import request from './request'

/**
 * 登录
 * @param {Object} params
 * @param {string} params.tenantCode - 租户编码
 * @param {string} params.username  - 用户名
 * @param {string} params.password  - 密码
 * @returns {Promise}
 */
export function login(params) {
  return request.post('/auth/login', params)
}

/**
 * 登出
 * @returns {Promise}
 */
export function logout() {
  return request.post('/auth/logout')
}

/**
 * 刷新 Token
 * @param {string} refreshToken
 * @returns {Promise}
 */
export function refreshToken(refreshToken) {
  return request.post('/auth/refresh-token', { refreshToken })
}

/**
 * 获取当前用户信息（含角色、权限列表）
 * @returns {Promise}
 */
export function getCurrentUser() {
  return request.get('/auth/me')
}

/**
 * 同步权限（带版本号，权限未变时只返回 changed=false）
 * @param {number} version - 客户端上次的权限版本号
 * @returns {Promise}
 */
export function syncPermissions(version) {
  return request.get('/auth/permissions', { params: { version } })
}

/**
 * 获取当前用户菜单树
 * @returns {Promise}
 */
export function getUserMenus() {
  return request.get('/auth/menus')
}

/**
 * 修改密码
 * @param {Object} params
 * @param {string} params.oldPassword
 * @param {string} params.newPassword
 * @returns {Promise}
 */
export function changePassword(params) {
  return request.put('/auth/password', params)
}

/**
 * 忘记密码 - 发送验证码
 * @param {Object} params
 * @param {string} params.tenantCode - 租户编码
 * @param {string} params.email       - 邮箱
 * @returns {Promise}
 */
export function forgotPassword(params) {
  return request.post('/auth/forgot-password', params)
}

/**
 * 重置密码（通过验证码）
 * @param {Object} params
 * @param {string} params.tenantCode  - 租户编码
 * @param {string} params.email       - 邮箱
 * @param {string} params.code        - 验证码
 * @param {string} params.newPassword - 新密码
 * @returns {Promise}
 */
export function resetPassword(params) {
  return request.post('/auth/reset-password', params)
}

/**
 * 更新当前用户个人信息
 * @param {Object} params - { username, realName, email, phone }
 * @returns {Promise}
 */
export function updateProfile(params) {
  return request.put('/auth/profile', params)
}
