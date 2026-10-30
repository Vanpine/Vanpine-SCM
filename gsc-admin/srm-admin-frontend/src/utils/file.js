/**
 * 文件URL工具函数 — 解析 "OSS URL|原始文件名" 格式
 * <p>
 * 前端上传文件时存储为 url|originalName 格式（竖线分隔），
 * 这些函数从拼接串中提取 URL 和显示用的文件名。
 */

/** OSS 旧域名（需替换为自定义域名） */
const OSS_LEGACY_HOST = 'srm-system.oss-cn-guangzhou.aliyuncs.com'
/** OSS 自定义域名 */
const OSS_CUSTOM_HOST = 'oss.gsc.greensmartplanet.cn'

/**
 * 归一化 OSS URL：强制 HTTPS + 替换为自定义域名
 * <p>
 * 历史数据可能存储了 http:// 前缀或旧 OSS 域名，
 * 浏览器会阻止 HTTP 内容在 HTTPS 页面中加载（或报告不安全）。
 * 自定义域名支持更灵活的响应头控制（如 PDF 内联预览）。
 */
function normalizeOssUrl(url) {
  if (!url) return ''
  let result = url
  // 强制 HTTPS（兼容历史 http:// 数据）
  result = result.replace(/^http:\/\//, 'https://')
  // 旧 OSS 域名 → 自定义域名（签名 V2 不绑定域名，替换后仍有效）
  if (result.includes(OSS_LEGACY_HOST)) {
    result = result.replace(OSS_LEGACY_HOST, OSS_CUSTOM_HOST)
  }
  return result
}

/** 从 url|原始名 中提取纯 URL（已归一化：HTTPS + 自定义域名） */
export function getFileUrl(str) {
  if (!str) return ''
  const idx = str.lastIndexOf('|')
  const raw = idx > -1 ? str.substring(0, idx) : str
  return normalizeOssUrl(raw)
}

/** 从 url|原始名 中提取显示用的文件名 */
export function getFileName(str, maxLen) {
  if (!str) return '文件'
  // 优先取 | 后面的原始文件名
  const idx = str.lastIndexOf('|')
  let name
  if (idx > -1) {
    name = str.substring(idx + 1)
  } else {
    // 兜底：从 URL 中提取文件名
    const urlPart = str.split('?')[0]
    const lastSlash = urlPart.lastIndexOf('/')
    name = decodeURIComponent(urlPart.substring(lastSlash + 1)) || '文件'
  }
  // 可选的截断（保留扩展名）
  const limit = maxLen || 0
  if (limit > 0 && name.length > limit) {
    const dotIdx = name.lastIndexOf('.')
    if (dotIdx !== -1) {
      const ext = name.substring(dotIdx)
      const available = limit - ext.length - 3
      if (available > 0) {
        return name.substring(0, available) + '...' + ext
      }
    }
    return name.substring(0, limit - 3) + '...'
  }
  return name
}

/**
 * 拼接 OSS URL 与原始文件名 → "url|原始名"（上传成功回调里用）
 * 显示时用 getFileName 提取原始名，避免展示 OSS 存储的 UUID 乱码
 */
export function composeFileValue(url, originalName) {
  return originalName ? (url + '|' + originalName) : (url || '')
}

/** 逗号分隔的附件 URL 列表 → 数组 */
export function getAttachmentList(urls) {
  if (!urls) return []
  return urls.split(',').filter(Boolean)
}
