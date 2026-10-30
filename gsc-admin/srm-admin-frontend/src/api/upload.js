/**
 * SRM Admin - 文件上传 API
 */
import request from './request'

/**
 * 上传文件到 OSS
 * @param {File} file - 文件对象
 * @param {string} dir - 存储目录，默认 avatar
 * @returns {Promise<{url: string}>}
 */
export function uploadFile(file, dir = 'avatar') {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('dir', dir)
  return request.post('/admin/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
