/**
 * SRM Admin - AI 对话机器人 API
 * 后端路径: /api/admin/ai/**
 * CRUD 走 request（axios）；流式对话走原生 fetch（axios 拦截器不支持流式读取）
 */
import request from './request'
import { getToken } from '@/utils/auth'

// ==================== 会话 ====================

/** 查当前用户会话列表 */
export function listConversations() {
  return request.get('/admin/ai/conversations')
}

/** 新建会话（title 可空） */
export function createConversation(title) {
  return request.post('/admin/ai/conversations', { title })
}

/** 删除会话 */
export function deleteConversation(id) {
  return request.delete(`/admin/ai/conversations/${id}`)
}

/** 查某会话历史消息 */
export function listMessages(conversationId) {
  return request.get(`/admin/ai/conversations/${conversationId}/messages`)
}

// ==================== 知识库文档 ====================

/** 上传知识库文档（txt/md/pdf ≤10MB） */
export function uploadDocument(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/admin/ai/documents', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** 查当前租户文档列表 */
export function listDocuments() {
  return request.get('/admin/ai/documents')
}

/** 删除知识库文档 */
export function deleteDocument(id) {
  return request.delete(`/admin/ai/documents/${id}`)
}

/** 重新解析失败的文档（清旧向量 → 重跑解析） */
export function reparseDocument(id) {
  return request.post(`/admin/ai/documents/${id}/reparse`)
}

/** 获取文档预览地址（后端按 oss_key 现签短效 URL，有效期 10 分钟，用完即弃） */
export function previewDocument(id) {
  return request.get(`/admin/ai/documents/${id}/preview`)
}

// ==================== 对话图片 ====================

/**
 * 上传对话图片（jpg/jpeg/png/webp ≤10MB）。
 * 返回 { id, fileName, contentType, fileSize, previewUrl }；
 * 发送消息时用 id 放进 imageIds（最多 4 张），previewUrl 仅用于本地缩略图展示。
 */
export function uploadChatImage(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/admin/ai/chat/images', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

// ==================== 流式对话 ====================

/**
 * 停止生成：把进行中的回答收成 cancelled，挡住异步线程随后写入 complete。
 * 关页 / F5 必须走 keepalive fetch：axios 在页面卸载时发不出去。
 *
 * @param {string} conversationId 会话ID
 * @param {Object} extra
 * @param {string} [extra.content] 界面上已展示的半段正文
 * @param {boolean} [extra.regenerate] 是否正在重新生成
 * @param {Array} [extra.citations] 界面上已展示的引用来源
 * @param {Object} [options]
 * @param {boolean} [options.keepalive] 页面卸载时用 fetch keepalive，不等待响应
 */
export function cancelChat(conversationId, extra = {}, options = {}) {
  const body = {
    conversationId,
    content: extra.content || '',
    regenerate: !!extra.regenerate,
    citations: extra.citations || [],
  }
  if (options.keepalive) {
    const baseURL = import.meta.env.VITE_API_BASE || '/api'
    return fetch(`${baseURL}/admin/ai/chat/cancel`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${getToken()}`,
      },
      body: JSON.stringify(body),
      keepalive: true,
    }).catch(() => {})
  }
  return request.post('/admin/ai/chat/cancel', body)
}

/** 从统一响应体里抽出给用户看的错误文案 */
function parseChatError(text, status) {
  try {
    const json = JSON.parse(text)
    if (json?.message) return json.message
  } catch { /* 非 JSON 用原文 */ }
  return text || `请求失败（HTTP ${status}）`
}

/** 确认 AI 工具写操作（二次确认：用户点确认按钮后落库） */
export function confirmToolAction(proposalId) {
  return request.post('/admin/ai/tool/confirm', { proposalId })
}

/** 确认 AI 询盘写操作（修改/关闭/取消询盘，走独立审计端点 UPDATE/Inquiry） */
export function confirmInquiryAction(proposalId) {
  return request.post('/admin/ai/tool/confirm-inquiry', { proposalId })
}

/** 取消 AI 工具写操作（不落库业务数据，仅消费提案 + 覆盖气泡为取消文案） */
export function cancelToolAction(proposalId) {
  return request.post('/admin/ai/tool/cancel', { proposalId })
}

/**
 * 发起流式对话。控制器必须同步返回：若等流结束再返回，停止按钮的 abort() 打不到当前请求。
 * 用原生 fetch 而非 axios：axios 拦截器/超时机制不支持 text/event-stream 流式读取。
 *
 * @param {Object} body - { conversationId: string|null, content: string, imageIds?: string[], regenerate?: boolean }
 * @param {Object} onEvent - { onConversation, onChunk, onCitations, onToolCall, onToolResult, onConfirmRequired, onDone, onError } 事件回调
 * @returns {AbortController} 立即返回，供「停止生成」中断当前 fetch
 */
export function chatStream(body, onEvent = {}) {
  const { onConversation, onChunk, onCitations, onToolCall, onToolResult, onConfirmRequired, onDone, onError } = onEvent
  const controller = new AbortController()
  const baseURL = import.meta.env.VITE_API_BASE || '/api'

  ;(async () => {
    try {
      const response = await fetch(`${baseURL}/admin/ai/chat`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${getToken()}`,
        },
        body: JSON.stringify(body),
        signal: controller.signal,
      })

      if (!response.ok || !response.body) {
        const text = await response.text().catch(() => '')
        onError?.(new Error(parseChatError(text, response.status)))
        return
      }

      // 解析 SSE：逐行读，data: 行承载 JSON 事件
      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''
      // done 只触发一次：Python 会发 done 事件，流自然结束也会走到这里，防重
      let doneDispatched = false
      const dispatchDone = (event) => {
        if (doneDispatched) return
        doneDispatched = true
        onDone?.(event)
      }

      const dispatch = (line) => {
        const trimmed = line.trim()
        if (!trimmed.startsWith('data:')) return
        const payload = trimmed.slice(5).trim()
        if (!payload) return
        let event
        try {
          event = JSON.parse(payload)
        } catch {
          return // 非法 JSON 忽略
        }
        if (event.type === 'conversation') onConversation?.(event.conversationId || null)
        else if (event.type === 'chunk') onChunk?.(event.content || '')
        else if (event.type === 'citations') onCitations?.(event.sources || [])
        else if (event.type === 'tool_call') onToolCall?.(event)
        else if (event.type === 'tool_result') onToolResult?.(event)
        else if (event.type === 'confirm_required') onConfirmRequired?.(event)
        else if (event.type === 'done') dispatchDone(event)
        else if (event.type === 'error') onError?.(new Error(event.message || 'AI 服务异常'))
      }

      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        // 按换行切分，处理完整事件行
        let idx
        while ((idx = buffer.indexOf('\n')) !== -1) {
          const line = buffer.slice(0, idx)
          buffer = buffer.slice(idx + 1)
          dispatch(line)
        }
      }
      // 处理末尾未换行的数据
      if (buffer.trim()) dispatch(buffer)
      dispatchDone({})
    } catch (error) {
      // AbortError 是主动中断，不算错误
      if (error.name !== 'AbortError') onError?.(error)
    }
  })()

  return controller
}
