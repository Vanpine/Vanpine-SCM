/**
 * SRM Admin - 采购管理 API
 * 后端路径: /api/admin/procurement
 */
import request from './request'

// ==================== 询盘 ====================

/** 创建询盘 */
export function createInquiry(data) {
  return request.post('/admin/procurement/inquiry', data)
}

/** 询盘列表 */
export function listInquiries(params) {
  return request.get('/admin/procurement/inquiry', { params })
}

/** 询盘详情 */
export function getInquiryDetail(id, params) {
  return request.get(`/admin/procurement/inquiry/${id}`, { params })
}

/** 编辑询盘 */
export function updateInquiry(id, data) {
  return request.put(`/admin/procurement/inquiry/${id}`, data)
}

/** 关闭询盘 */
export function closeInquiry(id) {
  return request.put(`/admin/procurement/inquiry/${id}/close`)
}

/** 取消询盘 */
export function cancelInquiry(id) {
  return request.put(`/admin/procurement/inquiry/${id}/cancel`)
}

/** 选定中标（支持分量授标，items: [{quoteId, awardedQuantity}]） */
export function awardInquiry(id, items, awardReason) {
  return request.put(`/admin/procurement/inquiry/${id}/award`, { items }, {
    params: { awardReason }
  })
}

/** 完成授标（数量未满也结束） */
export function completeAward(id) {
  return request.put(`/admin/procurement/inquiry/${id}/complete-award`)
}

// ==================== 报价 ====================

/** 提交报价 */
export function submitQuote(data) {
  return request.post('/admin/procurement/quote', data)
}

/** 查看询盘的报价列表 */
export function listQuotes(inquiryId) {
  return request.get(`/admin/procurement/inquiry/${inquiryId}/quotes`)
}

/** 撤回报价 */
export function withdrawQuote(id) {
  return request.put(`/admin/procurement/quote/${id}/withdraw`)
}

/** 批量查哪些对接已有活跃询盘，返回 {connectionId: inquiryId} */
export function checkActiveInquiries(connectionIds) {
  return request.get('/admin/procurement/inquiry/active-check', { params: { connectionIds: connectionIds.join(',') } })
}

/** 查当前采购商在该需求下已授标过的供应商（授标合作过即禁选） */
export function getInvitedSuppliers(requirementId) {
  return request.get('/admin/procurement/inquiry/invited-suppliers', { params: { requirementId } })
}

/** 查该需求下进行中询盘已邀请的供应商（询价中即禁选） */
export function getActiveInquirySuppliers(requirementId) {
  return request.get('/admin/procurement/inquiry/active-inquiry-suppliers', { params: { requirementId } })
}

// ==================== 邀请 ====================

/** 供应商接受邀请 */
export function acceptInvite(inquiryId, inviteId) {
  return request.put(`/admin/procurement/inquiry/${inquiryId}/invite/${inviteId}/accept`)
}

/** 供应商拒绝邀请 */
export function declineInvite(inquiryId, inviteId, declineReason) {
  return request.put(`/admin/procurement/inquiry/${inquiryId}/invite/${inviteId}/decline`, null, {
    params: { declineReason }
  })
}

// ==================== 供应商搜索 ====================

/** 追加供应商到已有询盘 */
export function addSuppliers(inquiryId, supplierTenantIds) {
  return request.put(`/admin/procurement/inquiry/${inquiryId}/add-suppliers`, supplierTenantIds)
}

/** 重新邀请已拒绝供应商 */
export function reinviteSupplier(inquiryId, inviteId) {
  return request.put(`/admin/procurement/inquiry/${inquiryId}/invite/${inviteId}/reinvite`)
}

/** 延长报价截止时间 */
export function extendDeadline(inquiryId, newDeadline) {
  return request.put(`/admin/procurement/inquiry/${inquiryId}/extend-deadline`, null, {
    params: { newDeadline }
  })
}

/** 搜索供应商（手动添加用） */
export function searchSuppliers(params) {
  return request.get('/admin/procurement/suppliers/search', { params })
}

// ==================== AI 功能 ====================

/** 获取已缓存的AI比价分析 */
export function getAiAnalysis(inquiryId) {
  return request.get(`/admin/procurement/inquiry/${inquiryId}/ai-analysis`)
}

/** AI 综合比价分析（执行并缓存） */
export function analyzeQuotes(inquiryId) {
  return request.post(`/admin/procurement/inquiry/${inquiryId}/analyze-quotes`)
}

/** AI 授标理由生成 */
export function generateAwardReason(inquiryId, quoteId) {
  return request.post(`/admin/procurement/inquiry/${inquiryId}/generate-award-reason`, null, {
    params: { quoteId }
  })
}

/** AI 询盘内容辅助 */
export function suggestInquiryFields(title, category, quantity, unit) {
  return request.post('/admin/procurement/inquiry/suggest-fields', null, {
    params: { title, category, quantity, unit }
  })
}

/** AI 推荐受邀供应商（复用撮合） */
export function recommendSuppliers(data) {
  return request.post('/admin/procurement/inquiry/recommend-suppliers', null, {
    params: data
  })
}

// ==================== 合同 ====================

/** 创建合同 */
export function createContract(data) {
  return request.post('/admin/procurement/contract', data)
}

/** 合同列表 */
export function listContracts(params) {
  return request.get('/admin/procurement/contract', { params })
}

/** 合同详情 */
export function getContractDetail(id) {
  return request.get(`/admin/procurement/contract/${id}`)
}

/** 编辑合同 */
export function updateContract(id, data) {
  return request.put(`/admin/procurement/contract/${id}`, data)
}

/** 确认盖章件签署（pending_confirm → signed，旧路径兼容） */
export function signContract(id) {
  return request.put(`/admin/procurement/contract/${id}/sign`)
}

/** 发起合同（采购方，draft → pending_supplier） */
export function submitContract(id) {
  return request.put(`/admin/procurement/contract/${id}/submit`)
}

/** 供应商确认条款（pending_supplier 记确认标记，状态不变） */
export function confirmContract(id) {
  return request.put(`/admin/procurement/contract/${id}/confirm`)
}

/** 供应商拒绝条款（pending_supplier → draft，必填理由） */
export function declineContract(id, reason) {
  return request.put(`/admin/procurement/contract/${id}/decline`, null, { params: { reason } })
}

/** 采购方撤回/修订（pending_supplier → draft，清确认标记，无需理由） */
export function withdrawContract(id) {
  return request.put(`/admin/procurement/contract/${id}/withdraw`)
}

/** 上传盖章件（任一方，pending_supplier → pending_confirm）—— 传 OSS object key，不是签名 URL */
export function uploadSignContract(id, key) {
  return request.put(`/admin/procurement/contract/${id}/upload-sign`, null, { params: { key } })
}

/** 确认盖章件签署（对方，pending_confirm → signed） */
export function confirmSignContract(id) {
  return request.put(`/admin/procurement/contract/${id}/confirm-sign`)
}

/** 生成合同 PDF（返回 {url, name}） */
export function generateContractPdf(id) {
  return request.post(`/admin/procurement/contract/${id}/pdf`)
}

/** 完成合同（signed → completed） */
export function completeContract(id) {
  return request.put(`/admin/procurement/contract/${id}/complete`)
}

/** 终止合同（signed → terminated） */
export function terminateContract(id, reason, terminateType) {
  return request.put(`/admin/procurement/contract/${id}/terminate`, null, { params: { reason, terminateType } })
}

/** 查某询盘下已有活跃合同的报价ID列表（前端禁选用） */
export function getActiveContractQuoteIds(inquiryId) {
  return request.get('/admin/procurement/contract/active-quotes', { params: { inquiryId } })
}
