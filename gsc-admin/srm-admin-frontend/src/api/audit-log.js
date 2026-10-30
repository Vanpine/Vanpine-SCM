/**
 * SRM Admin - 操作日志 API
 */
import request from './request'

/**
 * 分页查询操作日志
 * @param {Object} params - { pageNum, pageSize, module, operation, username, status, startTime, endTime }
 * @returns {Promise}
 */
export function getAuditLogList(params) {
  return request.get('/admin/audit-logs', { params })
}
