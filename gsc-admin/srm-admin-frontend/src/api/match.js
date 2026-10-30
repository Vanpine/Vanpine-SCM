/**
 * SRM Admin - 智能撮合 API
 * 后端路径: /api/admin/match
 */
import request from './request'

/** 获取当前租户的企业画像 */
export function getProfile() {
  return request.get('/admin/match/profile')
}

/** 更新企业画像 */
export function updateProfile(data) {
  return request.put('/admin/match/profile', data)
}

/** 查对方企业画像+尽调信息 */
export function getPublicProfile(tenantId) {
  return request.get(`/admin/match/profile/${tenantId}`)
}

// ==================== 采购需求 ====================

/** 发布采购需求 */
export function publishRequirement(data) {
  return request.post('/admin/match/requirement', data)
}

/** 分页查询我的需求列表 */
export function listMyRequirements(params) {
  return request.get('/admin/match/requirement', { params })
}

/** 查询需求详情 */
export function getRequirementDetail(id) {
  return request.get(`/admin/match/requirement/${id}`)
}

/** 编辑需求 */
export function updateRequirement(id, data) {
  return request.put(`/admin/match/requirement/${id}`, data)
}

/** 重新发布需求（可同时修改内容） */
export function republishRequirement(id, data) {
  return request.put(`/admin/match/requirement/${id}/republish`, data)
}

/** 关闭需求 */
export function closeRequirement(id) {
  return request.put(`/admin/match/requirement/${id}/close`)
}

/** 智能匹配候选供应商（名单同步返回；reasonStatus=generating 时需轮询补 AI 理由） */
export function matchSuppliers(id) {
  return request.get(`/admin/match/requirement/${id}/match`)
}

/** 需求广场-找供应商：按指定需求智能推荐（分页，含匹配分/理由） */
export function recommendPlazaSuppliers(requirementId, params) {
  return request.get(`/admin/match/requirement/${requirementId}/plaza-recommend`, { params })
}

// ==================== 需求广场 ====================

/** 需求广场-找需求：按本厂画像智能推荐公开需求（分页，含匹配分，无理由） */
export function recommendPlazaRequirements(params) {
  return request.get('/admin/match/requirement/plaza/recommend-requirements', { params })
}

/** 需求广场：供应商浏览全平台公开需求 */
export function listPlaza(params) {
  return request.get('/admin/match/requirement/plaza', { params })
}

/** 供应商主动申请需求 */
export function applyRequirement(id, message) {
  return request.post(`/admin/match/requirement/${id}/apply`, null, { params: { message } })
}

/** 需求广场-找供应商：分页查供应商列表 */
export function listSuppliers(params) {
  return request.get('/admin/match/profile/suppliers', { params })
}

// ==================== 对接记录 ====================

/** 发起对接意向 */
export function createConnection(data) {
  return request.post('/admin/match/connection', data)
}

/** 分页查我的对接记录 */
export function listConnections(params) {
  return request.get('/admin/match/connection', { params })
}

/** 接受对接 */
export function acceptConnection(id) {
  return request.put(`/admin/match/connection/${id}/accept`)
}

/** 拒绝对接 */
export function rejectConnection(id, replyMessage) {
  return request.put(`/admin/match/connection/${id}/reject`, null, { params: { replyMessage } })
}

/** 关闭对接（发起方取消 pending） */
export function closeConnection(id) {
  return request.put(`/admin/match/connection/${id}/close`)
}

/** 查我对某需求的对接详情（广场内查看联系方式等） */
export function getMyConnection(requirementId) {
  return request.get(`/admin/match/requirement/${requirementId}/my-connection`)
}

/** 查拒绝过该需求相关询盘的供应商（智能匹配页"已拒绝该询盘"标注） */
export function getDeclinedSuppliers(requirementId) {
  return request.get(`/admin/match/requirement/${requirementId}/declined-suppliers`)
}
