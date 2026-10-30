/**
 * 可信交付常量 — 风险/物流事件/补救工单 状态映射
 */

/** 风险类型中文映射 */
export const RISK_TYPE_MAP = {
  late_shipment: '未按计划发运',
  logistics_stagnation: '物流停滞',
  eta_delay: '预计晚于交期',
  clearance_hold: '清关阻塞',
  quantity_shortage: '数量不足',
  weather_disruption: '天气异常',
  other: '其他异常'
}

/** 风险等级中文映射 */
export const RISK_LEVEL_MAP = {
  low: '低',
  medium: '中',
  high: '高'
}

/** 风险等级标签颜色 */
export const RISK_LEVEL_TAG = {
  low: 'info',
  medium: 'warning',
  high: 'danger'
}

/** 风险状态中文映射 */
export const RISK_STATUS_MAP = {
  active: '观察中',
  warned: '已预警',
  high: '高风险',
  disposing: '处置中',
  mitigated: '已缓解',
  closed: '已关闭'
}

/** 风险状态标签颜色 */
export const RISK_STATUS_TAG = {
  active: 'primary',
  warned: 'warning',
  high: 'danger',
  disposing: 'warning',
  mitigated: 'success',
  closed: 'info'
}

/** 风险因子状态中文映射 */
export const FACTOR_STATUS_MAP = {
  active: '活跃',
  closed: '已解除'
}

/** 风险状态下拉选项（筛选用） */
export const RISK_STATUS_OPTIONS = Object.entries(RISK_STATUS_MAP).map(([value, label]) => ({ value, label }))

/** 驾驶舱风险状态筛选（仅活跃态：未缓解未关闭） */
export const RISK_STATUS_ACTIVE_OPTIONS = ['active', 'warned', 'high', 'disposing']
  .map((value) => ({ value, label: RISK_STATUS_MAP[value] }))

/** 风险等级下拉选项（筛选用） */
export const RISK_LEVEL_OPTIONS = Object.entries(RISK_LEVEL_MAP).map(([value, label]) => ({ value, label }))

/** 物流事件类型中文映射 */
export const LOGISTICS_EVENT_MAP = {
  planned_ship: '计划发运',
  actual_ship: '实际发运',
  in_transit: '运输中',
  arrived_transit: '到达中转站',
  customs_start: '清关开始',
  customs_hold: '清关/查验滞留',
  customs_clear: '清关完成',
  delay: '运输延误',
  arrived_delivered: '到达/签收',
  exception: '异常'
}

/** 物流事件类型下拉选项 */
export const LOGISTICS_EVENT_OPTIONS = Object.entries(LOGISTICS_EVENT_MAP).map(([value, label]) => ({ value, label }))

/** 物流追踪进度条顺序里程碑（异常类节点不占 step，只在纵向时间线红色展示） */
export const LOGISTICS_MILESTONES = ['planned_ship', 'actual_ship', 'in_transit', 'arrived_transit', 'customs_clear', 'arrived_delivered']

/** 补救动作类型中文映射 */
export const ACTION_TYPE_MAP = {
  air_shipment: '改空运/加急',
  alternative_supplier: '备选供应商',
  substitution: '替代物料',
  stock_transfer: '调拨库存',
  other: '其他'
}

/** 补救动作类型下拉选项 */
export const ACTION_TYPE_OPTIONS = Object.entries(ACTION_TYPE_MAP).map(([value, label]) => ({ value, label }))

/** 补救工单状态中文映射 */
export const ACTION_STATUS_MAP = {
  pending: '待执行',
  executing: '执行中',
  completed: '已完成',
  failed: '失败'
}

/** 补救工单状态标签颜色 */
export const ACTION_STATUS_TAG = {
  pending: 'primary',
  executing: 'warning',
  completed: 'success',
  failed: 'danger'
}

/** 8D 整改状态中文映射 */
export const REPORT8D_STATUS_MAP = {
  pending: '待填写',
  submitted: '待审核',
  approved: '已通过',
  rejected: '已打回'
}

/** 8D 整改状态标签颜色 */
export const REPORT8D_STATUS_TAG = {
  pending: 'primary',
  submitted: 'warning',
  approved: 'success',
  rejected: 'danger'
}

/** 8D 八步骤说明（供应商填写表单用） */
export const REPORT8D_STEPS = [
  { key: 'd1Team', label: 'D1 组建团队', placeholder: '谁负责整改（人员/职务）' },
  { key: 'd2Desc', label: 'D2 问题描述', placeholder: '什么问题、多大影响（数量/批次/金额/时间）' },
  { key: 'd3Containment', label: 'D3 临时围堵措施', placeholder: '现在怎么止血（隔离库存/换物流/紧急补货），要有完成时限' },
  { key: 'd4RootCause', label: 'D4 根本原因分析', placeholder: '为什么发生（5-Why/鱼骨图），不是"我们注意一下"' },
  { key: 'd5Corrective', label: 'D5 永久纠正措施', placeholder: '根治方案（换供应商/改工艺/备安全库存）' },
  { key: 'd6Verification', label: 'D6 实施与验证', placeholder: '措施落地证据 + 效果数据' },
  { key: 'd7Prevention', label: 'D7 预防再发', placeholder: '制度化（更新流程文件/SOP/写入合同）' },
  { key: 'd8Summary', label: 'D8 总结与关闭', placeholder: '整改总结，供采购方认可归档' }
]
