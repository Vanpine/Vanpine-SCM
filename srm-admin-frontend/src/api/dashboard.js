/**
 * SRM Admin - 工作台统计数据 API
 * 后端路径: /api/admin/dashboard
 */
import request from './request'

/**
 * 获取工作台数据（按角色返回 stats/todos/recentActivities）
 * GET /api/admin/dashboard
 * @param {string} role - 角色标识（SUPER_ADMIN / TENANT_ADMIN / BUYER / SUPPLIER / BOTH）
 */
export function fetchDashboardStats(role) {
  return request.get('/admin/dashboard', { params: { role } })
}
