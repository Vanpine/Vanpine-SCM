/**
 * SRM Admin - 用户管理 API
 */
import request from './request'

/**
 * 获取用户列表（分页）
 * @param {Object} params - 查询参数
 * @returns {Promise}
 */
export function getUserList(params) {
  return request.get('/admin/users', { params })
}

/**
 * 获取用户详情
 * @param {number} id - 用户ID
 * @returns {Promise}
 */
export function getUserById(id) {
  return request.get(`/admin/users/${id}`)
}

/**
 * 新增用户
 * @param {Object} data
 * @returns {Promise}
 */
export function createUser(data) {
  return request.post('/admin/users', data)
}

/**
 * 编辑用户
 * @param {number} id   - 用户ID
 * @param {Object} data - 更新数据
 * @returns {Promise}
 */
export function updateUser(id, data) {
  return request.put(`/admin/users/${id}`, data)
}

/**
 * 删除用户
 * @param {number} id - 用户ID
 * @returns {Promise}
 */
export function deleteUser(id) {
  return request.delete(`/admin/users/${id}`)
}

/**
 * 分配用户角色
 * @param {number} userId  - 用户ID
 * @param {number[]} roleIds - 角色ID数组
 * @returns {Promise}
 */
export function assignUserRoles(userId, roleIds) {
  return request.put(`/admin/users/${userId}/roles`, { roleIds })
}

/**
 * 重置用户密码
 * @param {number} id - 用户ID
 * @param {string} password - 新密码
 * @returns {Promise}
 */
export function resetUserPassword(id, password) {
  return request.put(`/admin/users/${id}/password`, { password })
}

/**
 * 更新用户状态
 * @param {number} id     - 用户ID
 * @param {string} status - 状态: active/disabled/locked
 * @returns {Promise}
 */
export function updateUserStatus(id, status) {
  return request.put(`/admin/users/${id}/status`, { status })
}
