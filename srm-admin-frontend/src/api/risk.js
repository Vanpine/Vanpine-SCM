/**
 * SRM Admin - 可信交付（交付风险/补救） API
 * 后端路径: /api/admin/delivery-risk
 */
import request from './request'

/** 交付风险列表 */
export function listRisks(params) {
  return request.get('/admin/delivery-risk', { params })
}

/** 供应保障驾驶舱：按交付单聚合活跃风险（一行一交付单） */
export function listRisksDashboard(params) {
  return request.get('/admin/delivery-risk/dashboard', { params })
}

/** 交付风险详情（含物流事件时间线） */
export function getRiskDetail(id) {
  return request.get(`/admin/delivery-risk/${id}`)
}

/** 录入物流事件 */
export function recordLogisticsEvent(data) {
  return request.post('/admin/delivery-risk/logistics-event', data)
}

/** 创建补救工单 */
export function createRecoveryAction(data) {
  return request.post('/admin/delivery-risk/recovery-action', data)
}

/** 按风险查补救工单列表 */
export function listRecoveryActions(riskId) {
  return request.get(`/admin/delivery-risk/${riskId}/recovery-actions`)
}

/** 开始执行补救工单 */
export function startRecoveryAction(id) {
  return request.put(`/admin/delivery-risk/recovery-action/${id}/start`)
}

/** 完成补救工单（填回执/结果） */
export function completeRecoveryAction(id, data) {
  return request.put(`/admin/delivery-risk/recovery-action/${id}/complete`, data)
}

/** 关闭风险 */
export function closeRisk(riskId) {
  return request.put(`/admin/delivery-risk/${riskId}/close`)
}

/** 采购方发起 8D 整改（一风险一张） */
export function create8d(riskId, data) {
  return request.post(`/admin/delivery-risk/${riskId}/8d`, data)
}

/** 供应商提交 8D 报告（D1-D8 全必填） */
export function submit8d(reportId, data) {
  return request.put(`/admin/delivery-risk/8d/${reportId}/submit`, data)
}

/** 采购方审核通过 8D */
export function approve8d(reportId) {
  return request.put(`/admin/delivery-risk/8d/${reportId}/approve`)
}

/** 采购方打回 8D */
export function reject8d(reportId, reason) {
  return request.put(`/admin/delivery-risk/8d/${reportId}/reject`, null, { params: { reason } })
}
