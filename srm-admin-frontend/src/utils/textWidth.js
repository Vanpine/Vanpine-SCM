/**
 * 文本渲染宽度估算 —— 用于表格列宽自适应（中文按14px/字、ASCII按8px/字）
 */

/** 估算单行文本渲染像素宽 */
export function estimateTextWidth(text) {
  let n = 0
  for (const ch of String(text || '')) n += ch.charCodeAt(0) > 255 ? 14 : 8
  return n
}

/**
 * 计算表格列宽：取内容最大宽度 与 表头宽度 的较大者 + 内边距余量，保证内容完整单行展示
 * @param {string[]} values  该列所有行的显示文本
 * @param {string}  header   表头文字
 * @param {object}  opts     { min: 最小宽度, pad: 单元格左右内边距+留白 }
 */
export function columnWidthByContent(values, header, opts = {}) {
  const { min = 120, pad = 26 } = opts
  const maxContent = (values || []).reduce((m, v) => Math.max(m, estimateTextWidth(v)), 0)
  return Math.max(min, Math.max(estimateTextWidth(header), maxContent) + pad)
}
