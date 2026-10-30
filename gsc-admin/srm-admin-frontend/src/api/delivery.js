/**
 * SRM Admin - 交付管理 API
 * 后端路径: /api/admin/delivery
 */
import request from './request'

/** 交付单列表 */
export function listDeliveries(params) {
  return request.get('/admin/delivery', { params })
}

/** 交付单详情（含履约时间线） */
export function getDeliveryDetail(id) {
  return request.get(`/admin/delivery/${id}`)
}

/** 按交付单查未关闭风险（物流追踪页风险联动，无则 data.risk=null） */
export function getDeliveryActiveRisk(deliveryId) {
  return request.get(`/admin/delivery-risk/by-delivery/${deliveryId}`)
}

/** 供应商提交承诺交期 */
export function submitPromiseDate(deliveryId, data) {
  return request.post(`/admin/delivery/${deliveryId}/promise-date`, data)
}

/** 采购方确认承诺交期 */
export function confirmPromiseDate(promiseId) {
  return request.put(`/admin/delivery/promise-date/${promiseId}/confirm`)
}

/** 采购方拒绝承诺交期 */
export function rejectPromiseDate(promiseId) {
  return request.put(`/admin/delivery/promise-date/${promiseId}/reject`)
}

/** 可创建交付单的合同列表（供应方视角，已签署且有未分配数量） */
export function listCreatableContracts() {
  return request.get('/admin/delivery/creatable-contracts')
}

/** 供应商手动创建交付单（主动分批） */
export function createDelivery(data) {
  return request.post('/admin/delivery', data)
}

/** 供应商删除交付单（仅待发货） */
export function deleteDelivery(id) {
  return request.delete(`/admin/delivery/${id}`)
}

/** 供应商发货 */
export function shipDelivery(id, data) {
  return request.put(`/admin/delivery/${id}/ship`, data)
}

/** 采购商确认收货 */
export function receiveDelivery(id, data) {
  return request.put(`/admin/delivery/${id}/receive`, data)
}

/** 采购商验收合格 */
export function acceptDelivery(id, remark) {
  return request.put(`/admin/delivery/${id}/accept`, null, { params: { remark } })
}

/** 采购商验收不合格（进入待供应商确认） */
export function rejectDelivery(id, data) {
  return request.put(`/admin/delivery/${id}/reject`, data)
}

/** 采购商验收部分残次（合格部分验收通过 + 残次部分发起补发） */
export function partialReject(id, data) {
  return request.put(`/admin/delivery/${id}/partial-reject`, data)
}

/** 供应商确认退回 */
export function confirmReturn(id) {
  return request.put(`/admin/delivery/${id}/confirm-return`)
}

/** 供应商对验收不合格提出异议 */
export function disputeReturn(id, remark) {
  return request.put(`/admin/delivery/${id}/dispute-return`, null, { params: { remark } })
}

/** 采购商填写退货物流 */
export function fillReturnLogistics(id, data) {
  return request.put(`/admin/delivery/${id}/return-logistics`, data)
}

/** 供应商确认收到退货 */
export function confirmReturnReceipt(id) {
  return request.put(`/admin/delivery/${id}/confirm-return-receipt`)
}

/** 供应商更正运单 */
export function correctTracking(id, data) {
  return request.put(`/admin/delivery/${id}/correct-tracking`, data)
}

/** 供应商发货排期列表（分批计划 + 发货状态） */
export function listShipmentPlan(params) {
  return request.get('/admin/delivery/shipment-plan', { params })
}

/** 供应商按分批计划发货（创建+发货一步到位） */
export function shipBatch(contractId, batchPlanId, data) {
  return request.post('/admin/delivery/ship-batch', data, {
    params: { contractId, batchPlanId }
  })
}

/** 物流节点当地实时天气（按城市名） */
export function getWeatherNow(city) {
  return request.get('/admin/delivery/weather/now', { params: { city } })
}

/** 物流节点当地逐日预报 */
export function getWeatherForecast(city, days = 3) {
  return request.get('/admin/delivery/weather/forecast', { params: { city, days } })
}
