/**
 * SRM Admin - 站内消息通知 API
 * 后端路径: /api/admin/notification
 */
import request from './request'

/**
 * 我的通知列表（未读优先）
 * GET /api/admin/notification
 * @param {Object} params - { pageNum, pageSize, unreadOnly }
 */
export function getNotificationList(params) {
  return request.get('/admin/notification', { params })
}

/**
 * 未读通知数
 * GET /api/admin/notification/unread-count
 * @param {Object} config - 额外 axios 配置，轮询使用 silentError 静默失败
 */
export function getNotificationUnreadCount(config = {}) {
  return request.get('/admin/notification/unread-count', config)
}

/**
 * 标记单条已读
 * PUT /api/admin/notification/:id/read
 */
export function markNotificationRead(id) {
  return request.put(`/admin/notification/${id}/read`)
}

/**
 * 全部标记已读
 * PUT /api/admin/notification/read-all
 */
export function markAllNotificationsRead() {
  return request.put('/admin/notification/read-all')
}
