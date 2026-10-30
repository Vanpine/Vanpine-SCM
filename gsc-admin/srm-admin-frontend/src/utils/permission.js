/**
 * SRM Admin - 权限校验工具函数
 */

/**
 * 检查当前用户是否拥有指定权限
 * @param {string} permCode - 权限标识，如 'user:delete'
 * @param {string[]} userPermissions - 当前用户权限列表
 * @returns {boolean}
 */
export function hasPermission(permCode, userPermissions) {
  if (!userPermissions || userPermissions.length === 0) return false
  // 超级管理员拥有所有权限
  if (userPermissions.includes('*:*:*') || userPermissions.includes('admin')) return true
  return userPermissions.includes(permCode)
}

/**
 * 检查是否拥有任一权限
 * @param {string[]} permCodes - 权限标识数组
 * @param {string[]} userPermissions - 当前用户权限列表
 * @returns {boolean}
 */
export function hasAnyPermission(permCodes, userPermissions) {
  return permCodes.some((code) => hasPermission(code, userPermissions))
}

/**
 * 检查是否拥有全部权限
 * @param {string[]} permCodes - 权限标识数组
 * @param {string[]} userPermissions - 当前用户权限列表
 * @returns {boolean}
 */
export function hasAllPermissions(permCodes, userPermissions) {
  return permCodes.every((code) => hasPermission(code, userPermissions))
}

/**
 * 是否可以使用 AI 助手。尽调预审、事务所与业务助手不是同一类用户，不开放。
 *
 * @param {string[]} roles 当前用户角色编码
 * @returns {boolean}
 */
export function canUseAiAssistant(roles) {
  if (!roles || roles.length === 0) return true
  return !roles.includes('DUE_DILIGENCE') && !roles.includes('FIRM')
}
