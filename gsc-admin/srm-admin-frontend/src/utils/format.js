/**
 * 格式化工具函数
 */

/** YYYY-MM-DDTHH:mm:ss → YYYY-MM-DD HH:mm */
export function formatTime(t) {
  if (!t) return '无'
  return t.replace('T', ' ').substring(0, 16)
}
