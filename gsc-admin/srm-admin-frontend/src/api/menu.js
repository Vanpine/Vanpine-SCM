/**
 * SRM Admin - 菜单管理 API
 * 后端路径: /api/admin/menu-manage
 */
import request from './request'

/**
 * 获取菜单树（超管返回全部菜单，企业管理员返回自己有权限的菜单）
 * GET /api/admin/menu-manage/tree
 * @returns {Promise<{code:200, data: Array}>} data 为树形菜单数组
 */
export function getMenuTree() {
  return request.get('/admin/menu-manage/tree')
}

/**
 * 菜单详情
 * GET /api/admin/menu-manage/:id
 * @param {number} id - 菜单ID
 * @returns {Promise} data 为菜单实体对象
 */
export function getMenuById(id) {
  return request.get(`/admin/menu-manage/${id}`)
}

/**
 * 新增菜单
 * POST /api/admin/menu-manage
 * @param {Object} data - 菜单数据 { parentId, menuType, menuName, menuPath, menuIcon, permCode, sortOrder, isVisible, isCache }
 * @returns {Promise} data 为新创建的菜单实体（含自增ID）
 */
export function createMenu(data) {
  return request.post('/admin/menu-manage', data)
}

/**
 * 编辑菜单
 * PUT /api/admin/menu-manage/:id
 * @param {number} id   - 菜单ID
 * @param {Object} data - 更新数据
 * @returns {Promise}
 */
export function updateMenu(id, data) {
  return request.put(`/admin/menu-manage/${id}`, data)
}

/**
 * 删除菜单（逻辑删除，有子菜单时后端拒绝）
 * DELETE /api/admin/menu-manage/:id
 * @param {number} id - 菜单ID
 * @returns {Promise}
 */
export function deleteMenu(id) {
  return request.delete(`/admin/menu-manage/${id}`)
}
