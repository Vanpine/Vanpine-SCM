/**
 * SRM Admin - 角色管理 API
 */
import request from './request'

/**
 * 获取角色列表（分页）
 * @param {Object} params - 查询参数
 * @returns {Promise}
 */
export function getRoleList(params) {
  return request.get('/admin/roles', { params })
}

/**
 * 获取所有角色（不分页，用于下拉选择）
 * @returns {Promise}
 */
export function getAllRoles() {
  return request.get('/admin/roles/all')
}

/**
 * 获取角色详情
 * @param {number} id - 角色ID
 * @returns {Promise}
 */
export function getRoleById(id) {
  return request.get(`/admin/roles/${id}`)
}

/**
 * 新增角色
 * @param {Object} data
 * @returns {Promise}
 */
export function createRole(data) {
  return request.post('/admin/roles', data)
}

/**
 * 编辑角色
 * @param {number} id   - 角色ID
 * @param {Object} data - 更新数据
 * @returns {Promise}
 */
export function updateRole(id, data) {
  return request.put(`/admin/roles/${id}`, data)
}

/**
 * 删除角色
 * @param {number} id - 角色ID
 * @returns {Promise}
 */
export function deleteRole(id) {
  return request.delete(`/admin/roles/${id}`)
}

/**
 * 分配角色权限
 * @param {number} roleId   - 角色ID
 * @param {number[]} permIds  - 权限ID数组
 * @returns {Promise}
 */
export function assignRolePermissions(roleId, permIds) {
  return request.put(`/admin/roles/${roleId}/permissions`, { permIds })
}

/**
 * 分配角色菜单
 * @param {number} roleId   - 角色ID
 * @param {number[]} menuIds  - 菜单ID数组
 * @returns {Promise}
 */
export function assignRoleMenus(roleId, menuIds) {
  return request.put(`/admin/roles/${roleId}/menus`, { menuIds })
}

/**
 * 获取所有权限列表（树形）
 * @returns {Promise}
 */
export function getAllPermissions() {
  return request.get('/admin/permissions')
}

/**
 * 获取所有菜单列表（树形）
 * @returns {Promise}
 */
export function getAllMenus() {
  return request.get('/admin/menus')
}
