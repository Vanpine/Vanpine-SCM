/**
 * HTML 转义：防止 XSS 注入
 * 将 & < > " ' 转为实体字符
 * @param {string} str - 原始字符串
 * @returns {string} 转义后的安全字符串
 */
export function escapeHtml(str) {
  if (!str) return ''
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}
