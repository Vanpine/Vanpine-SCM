/**
 * SRM Admin - 制裁筛查 API
 * 后端路径: /api/admin/sanction
 */
import request from './request'

/** 制裁名单库分页 */
export function listSanctionEntries(params) {
  return request.get('/admin/sanction/entries', { params })
}

/** 手动触发名单同步 */
export function syncSanctionEntries() {
  return request.post('/admin/sanction/sync')
}

/** 名单同步状态 */
export function getSanctionSyncStatus() {
  return request.get('/admin/sanction/sync/status')
}

/** 对入驻申请做制裁筛查（幂等可重查） */
export function screenApplication(applicationId) {
  return request.post(`/admin/sanction/screen/${applicationId}`)
}
