/**
 * SRM Admin - 角色首页路径
 *
 * 不同角色的「首页」不同：事务所只有事务所工作台一个页面，
 * 预审用户只有尽调页面，若统一跳 /dashboard 会进入一个空壳页面。
 * 路由守卫、侧边栏首页、面包屑首页、登录跳转、404 兜底全部走这里，避免多处硬编码。
 */

/** 角色 → 首页路径 */
const ROLE_HOME = {
  FIRM: '/due-diligence/firm',
  DUE_DILIGENCE: '/due-diligence',
}

/** 默认首页（企业管理员 / 采购 / 供应 / 超管） */
const DEFAULT_HOME = '/dashboard'

/**
 * 按角色取首页路径
 * @param {string[]} roles - 当前用户角色编码列表
 * @returns {string} 首页路径
 */
export function homePath(roles) {
  if (!Array.isArray(roles)) return DEFAULT_HOME
  for (const role of roles) {
    if (ROLE_HOME[role]) return ROLE_HOME[role]
  }
  return DEFAULT_HOME
}
