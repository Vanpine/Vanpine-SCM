/**
 * 交付常量 — 状态映射 / 履约时间线
 */

/** 交付单状态中文映射 */
export const DELIVERY_STATUS_MAP = {
  pending_shipment: '待发货',
  shipped: '待收货',
  received: '待验收',
  accepted: '已验收',
  return_pending: '待供应商确认',
  return_confirmed: '待填退货物流',
  return_shipping: '退货中',
  returned: '已退回',
  reship_pending: '待补发'
}

/** 交付单状态标签颜色 */
export const DELIVERY_STATUS_TAG = {
  pending_shipment: 'primary',
  shipped: 'primary',
  received: 'primary',
  accepted: 'success',
  return_pending: 'primary',
  return_confirmed: 'primary',
  return_shipping: 'primary',
  returned: 'danger',
  reship_pending: 'primary'
}

/** 交付单状态下拉选项（搜索/筛选用） */
export const DELIVERY_STATUS_OPTIONS = Object.entries(DELIVERY_STATUS_MAP).map(([value, label]) => ({ value, label }))

/** 收货确认页状态下拉（仅收货环节：待收货/待验收） */
export const RECEIPT_STATUS_OPTIONS = DELIVERY_STATUS_OPTIONS.filter((o) => ['shipped', 'received'].includes(o.value))

/** 验收视角交付单状态下拉（验收环节：待验收/已验收/待供应商确认/待填物流/已退回） */
export const ACCEPTANCE_STATUS_OPTIONS = DELIVERY_STATUS_OPTIONS.filter((o) => ['received', 'accepted', 'return_pending', 'return_confirmed', 'return_shipping', 'returned'].includes(o.value))

/** 发货页默认：待发货 + 已退回 */
export const SHIPMENT_TODO_STATUS = 'pending_shipment,returned'

/** 物流追踪范围：仅已发货及之后（待发货/待补发无在途货物可追踪） */
export const TRACKING_STATUS_RANGE = 'shipped,received,accepted,return_pending,return_confirmed,return_shipping,returned'

/** 物流追踪页状态下拉 */
export const TRACKING_STATUS_OPTIONS = DELIVERY_STATUS_OPTIONS.filter((o) =>
  TRACKING_STATUS_RANGE.split(',').includes(o.value))

/** 发货页状态下拉（含待处理合集） */
export const SHIPMENT_STATUS_OPTIONS = [
  { value: SHIPMENT_TODO_STATUS, label: '待处理' },
  ...DELIVERY_STATUS_OPTIONS
]

/** 发货排期状态下拉（基于分批计划，待发货=无发货单） */
export const SHIPMENT_PLAN_STATUS_OPTIONS = [
  { value: 'pending,returned', label: '待处理' },
  { value: 'reship_pending', label: '待补发' },
  { value: 'return_pending', label: '待确认退回' },
  { value: 'return_shipping', label: '退货中' },
  { value: 'shipped', label: '已发货' },
  { value: 'returned', label: '已退回' }
]

/** 履约时间线动作中文映射 */
export const DELIVERY_TRACE_ACTION_MAP = {
  create: '生成交付单',
  ship: '发货',
  receive: '收货',
  accept: '验收合格',
  return_request: '验收不合格',
  return_confirm: '供应商确认退回',
  return_dispute: '供应商提出异议',
  return_logistics: '填写退货物流',
  return_receipt: '供应商确认收到退货',
  partial_reject: '部分残次补发',
  delay_warning: '交期预警',
  correct_tracking: '更正运单',
  split: '分批拆单',
  delete: '删除交付单'
}
