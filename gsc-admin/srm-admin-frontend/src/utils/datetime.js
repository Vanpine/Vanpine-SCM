/**
 * 时间格式化工具 — 统一所有后端时间字段的展示格式
 * <p>
 * 后端 LocalDateTime 经 Jackson 默认序列化为 ISO 字符串 "2026-07-23T14:30:45"，
 * 这里直接字符串截取 + 替换 T，得到 "2026-07-23 14:30:45"（24 小时制、带秒）。
 * <p>
 * 关键点：不用 new Date() 解析，避免浏览器按本地时区转换导致显示时间与数据库不一致。
 */

/**
 * 格式化为 "YYYY-MM-DD HH:mm:ss"（24 小时制，带秒，与数据库 DATETIME 完全一致）
 * @param {string} t - 后端返回的 ISO 时间字符串，如 "2026-07-23T14:30:45"
 * @returns {string} "2026-07-23 14:30:45"；空值返回 "无"
 */
export function formatDateTime(t) {
  if (!t) return '无'
  // 取前 19 位 "YYYY-MM-DDTHH:mm:ss"，再把 T 替换为空格
  return String(t).slice(0, 19).replace('T', ' ')
}

/**
 * 格式化为 "YYYY-MM-DD"（仅日期，用于 LocalDate 字段或只需日期的场景）
 * @param {string} t - 后端返回的 ISO 日期/时间字符串
 * @returns {string} "2026-07-23"；空值返回 "无"
 */
export function formatDate(t) {
  if (!t) return '无'
  return String(t).slice(0, 10)
}
