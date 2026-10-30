/**
 * SRM Admin - 入驻申请 API
 * 后端路径: /api/admin/applications
 */
import request from './request'

/**
 * 分页查询入驻申请列表
 * @param {Object} params - { pageNum, pageSize, keyword, status }
 * @returns {Promise}
 */
export function getApplicationList(params) {
  return request.get('/admin/applications', { params })
}

/**
 * 入驻申请详情
 * @param {number} id - 申请ID
 * @returns {Promise}
 */
export function getApplicationDetail(id) {
  return request.get(`/admin/applications/${id}`)
}

/**
 * 审核入驻申请
 * @param {number} id   - 申请ID
 * @param {Object} data - { status: 'approved'|'rejected', reviewComment }
 * @returns {Promise}
 */
export function reviewApplication(id, data) {
  return request.put(`/admin/applications/${id}/review`, data)
}

/**
 * 尽调套餐价目表（平台开通币种 + 独立标价）
 * @returns {Promise<{ data: { currencies: string[], prices: Record<string, Record<string, number>> } }>}
 */
export function getDdPackages() {
  return request.get('/admin/dd-packages')
}

/**
 * 创建尽调支付单（返回收款码；已支付由回调写入）
 * @param {number} id - 申请ID
 * @param {Object} data - { packageType, paymentCurrency }
 * @returns {Promise<{ data: { qrcode, outTradeNo, orderNo, amountYuan, currency } }>}
 */
export function payApplication(id, data) {
  return request.put(`/admin/applications/${id}/pay`, data)
}

/** 查询尽调是否已支付（收款码页轮询，失败不弹窗） */
export function getPayStatus(id) {
  return request.get(`/admin/applications/${id}/pay-status`, { silentError: true })
}

/**
 * 保存尽调扩展信息（省份/城市/资质文件等）
 * @param {number} id - 申请ID
 * @param {Object} data - { province, city, businessLicenseUrl, attachmentUrls, shareholderInfo, financialReportUrl, legalRecord }
 * @returns {Promise}
 */
export function updateDdInfo(id, data) {
  return request.put(`/admin/applications/${id}/dd-info`, data)
}

/**
 * 尽调查看资料（只查 due_diligence 表）
 * @param {number} id - 申请ID
 * @returns {Promise}
 */
export function getDdDetail(id) {
  return request.get(`/admin/applications/${id}/dd-detail`)
}

/**
 * 分配事务所
 * @param {number} id - 申请ID
 * @param {Object} data - { firmId, firmName, firmContactName, firmContactEmail }
 * @returns {Promise}
 */
export function assignFirm(id, data) {
  return request.put(`/admin/applications/${id}/assign-firm`, data)
}

/**
 * 事务所工作台（FIRM 角色获取已分配的企业尽调列表）
 * @returns {Promise}
 */
export function getFirmWorkbench() {
  return request.get('/admin/firm/workbench')
}

/**
 * 事务所提交尽调结果
 * @param {number} applicationId - 申请ID
 * @param {Object} data - { progress, resultUrl }
 * @returns {Promise}
 */
export function submitFirmResult(applicationId, data) {
  return request.put(`/admin/firm/submit/${applicationId}`, data)
}

/**
 * 事务所退回尽调给企业补充材料
 * @param {number} applicationId - 申请ID
 * @param {string} reason - 退回原因
 * @returns {Promise}
 */
export function returnToEnterprise(applicationId, reason) {
  return request.put(`/admin/firm/return/${applicationId}`, null, { params: { reason } })
}

/**
 * 管理员确认尽调通过
 * @param {number} applicationId - 申请ID
 * @returns {Promise}
 */
export function confirmDdComplete(applicationId) {
  return request.put(`/admin/applications/${applicationId}/confirm-dd`)
}

/**
 * 生成尽调报告 PDF
 * @param {number} applicationId - 申请ID
 * @param {string} lang - 语言（zh/en，默认zh）
 * @returns {Promise}
 */
export function generateDdReport(applicationId, lang = 'zh') {
  return request.post(`/admin/applications/${applicationId}/generate-report`, null, { params: { lang } })
}

/**
 * 查询事务所列表（从公司 API 获取）
 * @param {string} keyword - 搜索关键字
 * @returns {Promise}
 */
export function searchFirms(keyword) {
  return request.get('/admin/firms/search', { params: { keyword } })
}

/**
 * 更新单条核查清单项
 * @param {number} itemId - 核查项ID
 * @param {Object} data - { checked, remark }
 * @returns {Promise}
 */
export function updateChecklistItem(itemId, data) {
  return request.put(`/admin/firm/checklist/${itemId}`, data)
}
