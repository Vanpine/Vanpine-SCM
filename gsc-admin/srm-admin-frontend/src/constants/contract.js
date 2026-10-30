/**
 * 合同常量 — 状态映射 / 终止类型
 * 全局统一，ContractManagement.vue 与 PriceComparison.vue 共用
 */

/** 合同状态中文映射 */
export const CONTRACT_STATUS_MAP = {
  draft: '草稿',
  pending_supplier: '待供应商确认',
  pending_confirm: '待确认盖章',
  signed: '已签署',
  completed: '已完成',
  terminated: '已终止'
}

/**
 * 按视角 + 供应商确认标记 + 本方是否已盖章获取合同状态文案
 * pending_supplier 阶段：
 *   - 未确认：供应商视角"待我确认"，采购方视角"待供应商确认"
 *   - 已确认：统一显示"待盖章"（已确认条款，等线下盖章/上传盖章件）
 * pending_confirm 阶段（轮流盖章）：
 *   - 本方已盖章上传 → "待对方盖章"
 *   - 本方未盖章 → "待我方盖章"
 */
export function getContractStatusLabel(status, isSupplier, confirmed, signUploaded) {
  if (status === 'pending_supplier') {
    if (confirmed) return '待盖章'
    return isSupplier ? '待我确认' : CONTRACT_STATUS_MAP[status]
  }
  if (status === 'pending_confirm') {
    // 未传 signUploaded（比价页等无盖章件字段）→ 显示默认"待确认盖章"
    if (signUploaded === undefined) return CONTRACT_STATUS_MAP[status]
    return signUploaded ? '待对方盖章' : '待我方盖章'
  }
  return CONTRACT_STATUS_MAP[status] || status
}

/** 合同状态标签颜色 */
export const CONTRACT_STATUS_TAG = {
  draft: 'info',
  pending_supplier: 'warning',
  pending_confirm: 'warning',
  signed: 'primary',
  completed: 'success',
  terminated: 'danger'
}

/** 合同状态下拉选项（搜索/筛选用） */
export const CONTRACT_STATUS_OPTIONS = Object.entries(CONTRACT_STATUS_MAP).map(([value, label]) => ({ value, label }))

/** 合同终止类型中文映射 */
export const TERMINATE_TYPE_MAP = {
  mutual: '协商终止',
  supplier_breach: '供应商违约',
  buyer_breach: '采购方违约',
  force_majeure: '不可抗力'
}
