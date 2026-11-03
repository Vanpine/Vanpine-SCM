/**
 * SRM Admin - 租户管理 API
 */
import request from './request'

/** 分页查询租户列表 */
export function getTenantList(params) {
  return request.get('/admin/tenants', { params })
}

/** 租户详情 */
export function getTenantById(id) {
  return request.get(`/admin/tenants/${id}`)
}

/** 新增租户 */
export function createTenant(data) {
  return request.post('/admin/tenants', data)
}

/** 编辑租户 */
export function updateTenant(id, data) {
  return request.put(`/admin/tenants/${id}`, data)
}

/** 审核租户（切换状态） */
export function reviewTenant(id, status, comment) {
  return request.put(`/admin/tenants/${id}/status`, { status, comment })
}

/** 删除租户 */
export function deleteTenant(id) {
  return request.delete(`/admin/tenants/${id}`)
}
