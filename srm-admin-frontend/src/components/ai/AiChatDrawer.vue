<template>
  <el-drawer v-model="visible" :with-header="false" direction="rtl" size="min(1240px, 96vw)"
    :close-on-click-modal="true" class="ai-drawer">
    <div class="ai-drawer-wrap">
      <!-- 左侧：会话栏 -->
      <aside class="ai-session-panel">
        <div class="ai-session-header">
          <span class="ai-session-heading">会话</span>
          <el-tooltip content="新建" placement="bottom" :show-after="300">
            <el-button
              link
              type="primary"
              size="small"
              class="ai-session-new"
              aria-label="新建"
              @click="handleNewSession"
            >
              <el-icon :size="22"><Plus /></el-icon>
            </el-button>
          </el-tooltip>
        </div>
        <div class="ai-session-list">
          <div v-if="isPendingView" class="ai-session-item active ai-session-draft">
            <div class="ai-session-main">
              <span class="ai-session-title">新对话</span>
            </div>
          </div>
          <p v-if="sessionLoading && !sessions.length" class="ai-session-empty">加载中…</p>
          <p v-else-if="!sessions.length && !isPendingView" class="ai-session-empty">暂无历史会话</p>
          <div
            v-for="s in sessions"
            :key="s.id"
            class="ai-session-item"
            :class="{ active: String(s.id) === String(viewingKey) }"
            @click="handleSwitchSession(s)"
          >
            <div class="ai-session-main">
              <span class="ai-session-title" :title="s.title">{{ s.title }}</span>
              <span v-if="s.updateTime" class="ai-session-time">{{ formatSessionTime(s.updateTime) }}</span>
            </div>
            <span class="ai-session-trail">
              <el-icon v-if="isSessionStreaming(s.id)" class="ai-session-generating is-loading" title="生成中">
                <Loading />
              </el-icon>
              <button type="button" class="ai-session-delete" title="删除" aria-label="删除会话" @click.stop="handleDeleteSession(s)">
                <el-icon><Delete /></el-icon>
              </button>
            </span>
          </div>
        </div>
      </aside>

      <!-- 右侧：聊天区 -->
      <section class="ai-chat-panel">
        <header class="ai-chat-header">
          <div class="ai-drawer-title">AI 助手</div>
        </header>

        <div class="ai-chat-body">
          <!-- 消息区 -->
          <div ref="msgBox" class="ai-msg-box">
            <el-empty v-if="!loadingHistory && messages.length === 0 && !viewingStreaming" description="开始提问吧，我会基于知识库回答" :image-size="80" />
            <div v-for="msg in messages" :key="msg.id" class="ai-msg" :class="msg.role">
              <div class="ai-msg-avatar">
                <svg v-if="msg.role === 'assistant'" class="ai-robot" viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="#409eff" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M12 8V4H8" />
                  <rect width="16" height="12" x="4" y="8" rx="2" />
                  <path d="M2 14h2" />
                  <path d="M20 14h2" />
                  <path d="M15 13v2" />
                  <path d="M9 13v2" />
                </svg>
                <el-avatar v-else :size="32" :src="authStore.user?.avatarUrl" icon="UserFilled" />
              </div>
              <div class="ai-msg-content">
                <!-- 消息附带的图片（user 消息，点击可放大） -->
                <div v-if="msg.images?.length" class="ai-msg-images" :class="msg.role">
                  <div
                    v-for="img in msg.images"
                    :key="img.id"
                    class="ai-msg-image-wrap"
                    :class="{ selected: isPendingImage(img.id) }"
                  >
                    <el-image :src="img.previewUrl"
                      :preview-src-list="msg.images.map((i) => i.previewUrl)" :initial-index="msg.images.indexOf(img)"
                      fit="cover" class="ai-msg-image" preview-teleported />
                    <button
                      v-if="msg.role === 'user' && !viewingStreaming"
                      type="button"
                      class="ai-msg-image-use"
                      @click.stop="toggleHistoryImage(img)"
                    >
                      {{ isPendingImage(img.id) ? '取消' : '问这张' }}
                    </button>
                  </div>
                </div>
                <!-- assistant 用 markdown 渲染，user 纯文本 -->
                <div v-if="msg.role === 'assistant'" class="ai-msg-md" v-html="renderMd(msg.content)" />
                <div v-else-if="visibleUserText(msg)" class="ai-msg-text">{{ visibleUserText(msg) }}</div>
                <div v-if="msg.role === 'assistant' && uniqueCitations(msg.citations).length" class="ai-msg-citations">
                  <button type="button" class="ai-citation-toggle" @click="toggleCitations(msg)">
                    <span class="ai-citation-label">参考来源 · {{ uniqueCitations(msg.citations).length }}</span>
                    <el-icon :size="12" class="ai-citation-arrow" :class="{ open: isCitationsOpen(msg) }"><ArrowDown /></el-icon>
                  </button>
                  <div v-if="isCitationsOpen(msg)" class="ai-citation-list">
                    <button
                      v-for="c in uniqueCitations(msg.citations)"
                      :key="String(c.documentId || c.fileName) + (c.sectionPath || '')"
                      type="button"
                      class="ai-citation-chip"
                      :title="c.contentPreview || c.fileName"
                      @click="handlePreviewCitation(c)"
                    >
                      {{ c.fileName || '知识库文档' }}<span v-if="c.sectionPath" class="ai-citation-section"> · {{ c.sectionPath }}</span>
                    </button>
                  </div>
                </div>
                <div v-if="msg.role === 'assistant' && msg.status === 'cancelled'" class="ai-msg-stopped">
                  已停止生成
                </div>
                <div v-if="formatMsgTime(msg.createTime)" class="ai-msg-time">{{ formatMsgTime(msg.createTime) }}</div>
                <!-- 消息操作：仅最后一条且非流式中显示 -->
                <div v-if="msg.role === 'assistant' && !viewingStreaming && isLastAssistant(msg)" class="ai-msg-actions">
                  <el-tooltip content="复制" placement="top">
                    <el-button link size="small" @click="handleCopy(msg)">
                      <el-icon><CopyDocument /></el-icon>
                    </el-button>
                  </el-tooltip>
                  <el-tooltip content="重新生成" placement="top">
                    <el-button link size="small" @click="handleRegenerate(msg)">
                      <el-icon><Refresh /></el-icon>
                    </el-button>
                  </el-tooltip>
                </div>
              </div>
            </div>
            <!-- 流式生成中的占位 -->
            <div v-if="viewingStreaming" class="ai-msg assistant">
              <div class="ai-msg-avatar"><svg class="ai-robot" viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="#409eff" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M12 8V4H8" />
                  <rect width="16" height="12" x="4" y="8" rx="2" />
                  <path d="M2 14h2" />
                  <path d="M20 14h2" />
                  <path d="M15 13v2" />
                  <path d="M9 13v2" />
                </svg></div>
              <div class="ai-msg-content">
                <div class="ai-msg-md" v-html="renderMd(activeStream.text)" /><span v-if="!activeStream.pendingConfirm" class="ai-cursor">|</span>
                <!-- 工具调用状态：紧跟光标后，正在查业务数据时显示转圈+文案 -->
                <span v-if="activeStream.toolStatus" class="ai-tool-status">
                  <el-icon v-if="activeStream.toolRunning" class="is-loading" :size="14"><Loading /></el-icon>
                  <span>{{ activeStream.toolStatus }}</span>
                </span>
                <!-- 写操作二次确认卡片：模型发起写工具后，等用户点确认才落库 -->
                <div v-if="activeStream.pendingConfirm" class="ai-confirm-card">
                  <div class="ai-confirm-head">
                    <el-icon class="ai-confirm-icon"><WarningFilled /></el-icon>
                    <span class="ai-confirm-title">需要你确认后执行</span>
                  </div>
                  <div class="ai-confirm-text">{{ activeStream.pendingConfirm.actionSummary }}</div>
                  <div class="ai-confirm-actions">
                    <el-button size="small" :disabled="activeStream.pendingConfirm.confirming"
                      @click="handleCancelConfirm">取消</el-button>
                    <el-button size="small" type="primary" :loading="activeStream.pendingConfirm.confirming"
                      @click="handleConfirmAction">{{ activeStream.pendingConfirm.confirmed ? '已执行' : '确认执行' }}</el-button>
                  </div>
                </div>
                <div v-if="formatMsgTime(activeStream.startedAt)" class="ai-msg-time">{{ formatMsgTime(activeStream.startedAt) }}</div>
              </div>
            </div>
          </div>

          <!-- 输入区 -->
          <div class="ai-input-bar">
            <div class="ai-input-box" :class="{ 'is-streaming': viewingStreaming }"
              @paste="handlePaste" @dragover.prevent @drop.prevent="handleDrop">
              <!-- 待发送图片缩略图 -->
              <div v-if="pendingImages.length" class="ai-pending-list">
                <div v-for="img in pendingImages" :key="img.localKey" class="ai-pending-image" :title="img.fileName">
                  <el-image :src="img.previewUrl" fit="cover" class="ai-pending-thumb" />
                  <div class="ai-pending-mask">
                    <el-icon v-if="img.uploading" class="is-loading"><Loading /></el-icon>
                    <el-icon v-else class="ai-pending-remove" @click="removePendingImage(img.localKey)"><Close /></el-icon>
                  </div>
                </div>
              </div>
              <el-input v-model="input" type="textarea" :rows="2" resize="none" class="ai-input-field"
                :placeholder="inputPlaceholder" :disabled="viewingStreaming"
                @keydown.enter.exact.prevent="handleSend()" />
              <div class="ai-input-actions">
                <el-tooltip :content="`上传图片，最多 ${MAX_IMAGES_PER_MESSAGE} 张（也可粘贴 / 拖入）`" placement="top">
                  <el-button
                    link
                    :type="pendingImages.length ? 'primary' : 'default'"
                    size="small"
                    class="ai-upload-btn"
                    aria-label="上传图片"
                    :disabled="viewingStreaming || pendingImages.length >= MAX_IMAGES_PER_MESSAGE"
                    @click="fileInput?.click()"
                  >
                    <el-icon :size="18"><PictureFilled /></el-icon>
                  </el-button>
                </el-tooltip>
                <input ref="fileInput" type="file" accept="image/jpeg,image/png,image/webp" multiple hidden
                  @change="handleFilePick" />
                <el-button v-if="viewingStreaming" size="small" @click="handleStop">
                  <el-icon class="ai-stop-icon"><VideoPause /></el-icon>停止生成
                </el-button>
                <el-tooltip v-else content="发送" placement="top" :show-after="300">
                  <el-button
                    link
                    type="primary"
                    size="small"
                    class="ai-send-btn"
                    aria-label="发送"
                    :disabled="(!input.trim() && !pendingImages.length) || pendingBusy"
                    @click="handleSend()"
                  >
                    <el-icon :size="26"><Promotion /></el-icon>
                  </el-button>
                </el-tooltip>
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>
    <el-dialog
      v-model="previewVisible"
      :title="previewTitle"
      :close-on-click-modal="false"
      class="ai-citation-preview-dialog"
      destroy-on-close
    >
      <iframe
        v-if="previewUrl"
        :src="previewUrl"
        class="ai-citation-preview-frame"
        frameborder="0"
      />
    </el-dialog>
  </el-drawer>
</template>

<script setup>
import { computed, reactive, ref, nextTick, watch, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CopyDocument, Refresh, VideoPause, PictureFilled, Close, Loading, Delete, Plus, Promotion, ArrowDown, WarningFilled } from '@element-plus/icons-vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

// AI 回复里的链接（如尽调报告 PDF 下载地址）统一新标签页打开 + 防 tabnabbing。
// 全局 hook 只注册一次，作用于本组件所有经过 DOMPurify 消毒的 markdown。
DOMPurify.addHook('afterSanitizeAttributes', (node) => {
  if (node.tagName === 'A') {
    node.setAttribute('target', '_blank')
    node.setAttribute('rel', 'noopener noreferrer')
  }
})
import { useAuthStore } from '@/stores/auth'
import {
  listMessages,
  deleteConversation,
  listConversations,
  chatStream,
  cancelChat,
  uploadChatImage,
  previewDocument,
  confirmToolAction,
  confirmInquiryAction,
  cancelToolAction,
} from '@/api/ai'

// 抽屉开关（由父组件 AiChatWidget 控制）
const visible = defineModel('visible', { type: Boolean, default: false })

const input = ref('')

// 当前登录用户（头像取 user.avatarUrl，与顶栏同源）
const authStore = useAuthStore()

// 对话状态
const messages = ref([])
const currentConversationId = ref(null)
const loadingHistory = ref(false)
const msgBox = ref(null)
/** 新对话还没会话 ID 时，每场新建一个独立 pending key，避免两路挤在一起 */
const PENDING_PREFIX = '__pending__'
/**
 * 按会话隔离的进行中流。key 为真实会话 ID 或 `__pending__N`。
 * 切走界面只换 viewingKey，不中断这里的任务。
 */
const streams = reactive({})
/** 输入框 + 待发图按会话存，切换时互不带过去 */
const drafts = reactive({})
/** 人已切走时流结束的半段，回来若库还没写成则先用这份 */
const leftovers = reactive({})
/** 当前右侧正在看的 key（真实会话 ID 或 pending） */
const viewingKey = ref(null)
let pendingSeq = 0
let pendingLocalSeq = 0

function isPendingKey(key) {
  return key == null || key === '' || String(key).startsWith(PENDING_PREFIX)
}

function newPendingKey() {
  return `${PENDING_PREFIX}${++pendingSeq}`
}

function streamKey(id) {
  return String(id)
}

function getStream(id) {
  return id == null || id === '' ? null : streams[String(id)]
}

function isViewing(id) {
  return id != null && viewingKey.value != null && String(id) === String(viewingKey.value)
}

/** 当前看着的会话是否正在生成（输入区 / 停止按钮 / 流式气泡） */
const activeStream = computed(() => (viewingKey.value ? streams[viewingKey.value] : null) || null)
const viewingStreaming = computed(() => !!activeStream.value && !activeStream.value.finalized)
const pendingBusy = computed(() => pendingImages.value.some((img) => img.uploading || !img.id))
const inputPlaceholder = computed(() => {
  if (viewingStreaming.value) return '正在回答…'
  if (pendingImages.value.length === 1) return '想问这张图什么？也可直接发送'
  if (pendingImages.value.length > 1) return '想问这些图什么？也可直接发送'
  return '输入问题，Enter 发送'
})

function isSessionStreaming(id) {
  if (isPendingKey(id)) return false
  const job = getStream(id)
  return !!job && !job.finalized
}

/** 正在看未落库的新对话（viewingKey 为空时不算，避免打开抽屉闪一下） */
const isPendingView = computed(() => {
  const key = viewingKey.value
  return !!key && isPendingKey(key)
})

/**
 * 会话列表相对时间。updateTime 来自 LocalDateTime，常见为 ISO 或空格分隔。
 *
 * @param {string} t 更新时间
 * @returns {string}
 */
function formatSessionTime(t) {
  if (!t) return ''
  const d = new Date(typeof t === 'string' ? t.replace(' ', 'T') : t)
  if (Number.isNaN(d.getTime())) return ''
  const diff = Date.now() - d.getTime()
  const min = Math.floor(diff / 60000)
  if (min < 1) return '刚刚'
  if (min < 60) return `${min} 分钟前`
  const hour = Math.floor(min / 60)
  if (hour < 24) return `${hour} 小时前`
  const day = Math.floor(hour / 24)
  if (day === 1) return '昨天'
  if (day < 7) return `${day} 天前`
  const pad = (n) => String(n).padStart(2, '0')
  if (d.getFullYear() === new Date().getFullYear()) {
    return `${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
  }
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/**
 * 消息气泡时间：当天只显示时分，跨天带日期。
 *
 * @param {string} t 创建时间
 * @returns {string}
 */
function formatMsgTime(t) {
  if (!t) return ''
  const d = new Date(typeof t === 'string' ? t.replace(' ', 'T') : t)
  if (Number.isNaN(d.getTime())) return ''
  const pad = (n) => String(n).padStart(2, '0')
  const hm = `${pad(d.getHours())}:${pad(d.getMinutes())}`
  const now = new Date()
  const sameDay = d.getFullYear() === now.getFullYear()
    && d.getMonth() === now.getMonth()
    && d.getDate() === now.getDate()
  if (sameDay) return hm
  if (d.getFullYear() === now.getFullYear()) {
    return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${hm}`
  }
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${hm}`
}

function saveDraft() {
  const key = viewingKey.value
  if (!key) return
  drafts[key] = { input: input.value, pendingImages: pendingImages.value }
}

function restoreDraft(key) {
  const d = key ? drafts[key] : null
  input.value = d?.input || ''
  pendingImages.value = d?.pendingImages ? [...d.pendingImages] : []
}

function clearDraft(key) {
  if (key) delete drafts[key]
}

function migrateDraft(fromKey, toKey) {
  if (!fromKey || !toKey || fromKey === toKey) return
  if (drafts[fromKey]) {
    drafts[toKey] = drafts[fromKey]
    delete drafts[fromKey]
  }
}

function contentsOverlap(a, b) {
  if (!a || !b) return false
  return a === b || a.startsWith(b) || b.startsWith(a)
}

/** 切回来时：库已有则丢掉缓存；库还没有则先展示这份，避免空白 */
function applyLeftover(conversationId, list) {
  const key = String(conversationId)
  const leftover = leftovers[key]
  if (!leftover?.text) return list
  const next = [...list]
  const idx = next.findIndex((m) => m.role === 'assistant' && contentsOverlap(m.content, leftover.text))
  if (idx >= 0) {
    if ((leftover.text || '').length > (next[idx].content || '').length) {
      next[idx] = {
        ...next[idx],
        content: leftover.text,
        citations: leftover.citations,
        status: leftover.status,
      }
    }
    delete leftovers[key]
    return next
  }
  next.push({
    id: `assistant-leftover-${key}`,
    role: 'assistant',
    content: leftover.text,
    citations: leftover.citations || [],
    status: leftover.status,
    createTime: leftover.createTime,
  })
  return next
}

// 待发送图片（单轮最多 4 张）与上传态
const pendingImages = ref([])
const fileInput = ref(null)
/** 允许的图片类型（与后端文件头校验保持一致） */
const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']
/** 单图大小上限 10MB（与后端一致，前端先拦一次省一次往返） */
const MAX_IMAGE_SIZE = 10 * 1024 * 1024
/** 单轮最多 4 张，与 Java MAX_IMAGES_PER_MESSAGE 对齐 */
const MAX_IMAGES_PER_MESSAGE = 4

/** 工具名 → 可读文案（tool_call 状态展示用） */
const TOOL_LABEL = {
  search_suppliers: '正在搜索供应商…',
  get_supplier_detail: '正在查询供应商详情…',
  list_contracts: '正在查询合同…',
  list_inquiries: '正在查询询盘…',
  get_weather: '正在查询天气…',
  create_supplier_note: '正在生成备注操作…',
  update_inquiry: '正在生成修改询盘操作…',
  extend_inquiry_deadline: '正在生成延长截止操作…',
  close_inquiry: '正在生成关闭询盘操作…',
  cancel_inquiry: '正在生成取消询盘操作…',
}

/** 询盘写工具集合（确认时走独立审计端点 confirm-inquiry） */
const INQUIRY_WRITE_TOOLS = new Set(['update_inquiry', 'extend_inquiry_deadline', 'close_inquiry', 'cancel_inquiry'])

// 引用来源预览（现签短效 URL，用完即弃）
const previewVisible = ref(false)
const previewUrl = ref('')
const previewTitle = ref('')
/** 展开「参考来源」的消息 id 集合 */
const expandedCitations = ref(new Set())

// 会话列表
const sessions = ref([])
const sessionLoading = ref(false)

/** 清理模型偶发的 DSML 伪工具调用文本，避免乱码上屏（DeepSeek 偶发把 function call 渲染成 <｜DSML｜invoke> 标签） */
function stripDsml(content) {
  if (!content) return ''
  if (!/DSML|invoke|function_calls/i.test(content)) return content
  const cleaned = content
    .replace(/<[^>\n]*?(?:DSML|invoke|parameter|function_calls)[^>]*>/gi, '')
    .replace(/｜[^｜\n]*?(?:DSML|invoke|parameter|function_calls)[^｜\n]*｜/gi, '')
    .replace(/\s{2,}/g, ' ')
    .trim()
  return cleaned || '（AI 返回内容异常，请重新提问）'
}

/** markdown 渲染：先清 DSML 乱码，marked 转 HTML 后过 DOMPurify 消毒，防 XSS（AI 输出不可信） */
function renderMd(content) {
  if (!content) return ''
  try {
    return DOMPurify.sanitize(marked.parse(stripDsml(content)))
  } catch {
    return escapeHtml(content)
  }
}

/** 纯图片提问不展示空气泡；旧数据里的兜底文案「请解析这张图片」也不当作用户原话 */
function visibleUserText(msg) {
  const text = (msg?.content || '').trim()
  if (!text) return ''
  if (msg.images?.length && text === '请解析这张图片') return ''
  return text
}

/** 转义 HTML 特殊字符（用于把用户可控文本安全拼入确认框/纯文本展示） */
function escapeHtml(str) {
  if (!str) return ''
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

/**
 * 引用来源按文档去重。同一文件会被切成多块召回，展示只保留首次出现的那条。
 *
 * @param {Array} citations 原始引用（含 chunkIndex / sectionPath）
 * @returns {Array} 按 documentId + sectionPath 去重后的列表
 */
function uniqueCitations(citations) {
  if (!citations?.length) return []
  const seen = new Set()
  return citations.filter((c) => {
    const key = String(c.documentId || c.fileName || '') + '|' + (c.sectionPath || '')
    if (!key || seen.has(key)) return false
    seen.add(key)
    return true
  })
}

/** 参考来源折叠态：某条消息的引用是否展开 */
function isCitationsOpen(msg) {
  return expandedCitations.value.has(msg.id)
}

function toggleCitations(msg) {
  const set = expandedCitations.value
  const next = new Set(set)
  if (next.has(msg.id)) next.delete(msg.id)
  else next.add(msg.id)
  expandedCitations.value = next
}

/** 打开抽屉时拉取会话列表（懒加载历史） */
watch(visible, async (val) => {
  if (val) {
    await fetchSessions()
    if (sessions.value.length && !currentConversationId.value) {
      await switchTo(sessions.value[0])
    } else if (!viewingKey.value) {
      viewingKey.value = newPendingKey()
    }
  }
})

async function fetchSessions() {
  sessionLoading.value = true
  try {
    const res = await listConversations()
    sessions.value = res?.data || []
  } catch { /* 拦截器已处理 */ }
  finally { sessionLoading.value = false }
}

/** 切换会话的请求序号：连点时丢掉过期的 listMessages，避免慢响应盖掉当前会话 */
let historySeq = 0

async function switchTo(session) {
  saveDraft()
  const seq = ++historySeq
  viewingKey.value = String(session.id)
  currentConversationId.value = session.id
  restoreDraft(viewingKey.value)
  loadingHistory.value = true
  try {
    const res = await listMessages(session.id)
    if (seq !== historySeq) return
    messages.value = applyLeftover(session.id, res?.data || [])
    await scrollBottom()
  } catch { /* 拦截器已处理 */ }
  finally {
    if (seq === historySeq) loadingHistory.value = false
  }
}

async function handleSwitchSession(s) {
  if (String(s.id) === String(viewingKey.value)) return
  await switchTo(s)
}

/**
 * 新建会话：只清空当前界面，后台其他会话的流继续跑。
 * 发出第一条消息时由后端自动建会话并按首句前 20 字命名。
 */
function handleNewSession() {
  saveDraft()
  historySeq++
  viewingKey.value = newPendingKey()
  currentConversationId.value = null
  messages.value = []
  restoreDraft(viewingKey.value)
}

/**
 * 通知后端停止某一路。关页必须 keepalive，axios 在卸载时发不出去。
 *
 * @param {Object} job 进行中的流
 * @param {boolean} [keepalive=false] 页面卸载时用 fetch keepalive
 */
function requestCancel(job, keepalive = false) {
  const cid = isPendingKey(job.id) ? currentConversationId.value : job.id
  if (!cid || isPendingKey(cid)) return
  const extra = {
    content: job.text,
    regenerate: job.regenerate,
    citations: job.citations || [],
  }
  if (keepalive) {
    cancelChat(cid, extra, { keepalive: true })
    return
  }
  cancelChat(cid, extra).catch(() => {})
}

/** 删会话前先停掉该会话自己的流，避免异步线程往已删会话写 complete */
function abortJob(job, persistCancel) {
  if (!job || job.finalized) return
  if (persistCancel) requestCancel(job)
  job.aborter?.abort()
  job.finalized = true
  delete streams[streamKey(job.id)]
}

async function handleDeleteSession(s) {
  try {
    await ElMessageBox.confirm(
      `<div style="white-space:nowrap;margin-bottom:4px">确认删除会话 <span class="highlight-name">${escapeHtml(s.title)}</span> 吗？</div><div>删除后会话内的消息将一并删除。</div>`,
      '删除会话',
      { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning', dangerouslyUseHTMLString: true, customClass: 'confirmDdMsgBox' },
    )
  } catch { return }
  try {
    abortJob(getStream(s.id), true)
    await deleteConversation(s.id)
    ElMessage.success('会话已删除')
    if (String(s.id) === String(currentConversationId.value)) {
      historySeq++
      clearDraft(String(s.id))
      viewingKey.value = newPendingKey()
      currentConversationId.value = null
      messages.value = []
      restoreDraft(viewingKey.value)
    }
    await fetchSessions()
  } catch { /* 拦截器已处理 */ }
}

/** 点击选择图片（可多选） */
function handleFilePick(e) {
  const files = [...(e.target.files || [])]
  e.target.value = ''
  addFiles(files)
}

/** 粘贴图片（截图等） */
function handlePaste(e) {
  if (viewingStreaming.value) return
  const files = [...(e.clipboardData?.items || [])]
    .filter((i) => i.type.startsWith('image/'))
    .map((i) => i.getAsFile())
    .filter(Boolean)
  if (!files.length) return
  e.preventDefault()
  addFiles(files)
}

/** 拖拽图片入输入区 */
function handleDrop(e) {
  if (viewingStreaming.value) return
  addFiles([...(e.dataTransfer?.files || [])])
}

function addFiles(files) {
  const images = files.filter((f) => f && f.type.startsWith('image/'))
  if (!images.length) return
  const room = MAX_IMAGES_PER_MESSAGE - pendingImages.value.length
  if (room <= 0) {
    ElMessage.warning(`最多上传 ${MAX_IMAGES_PER_MESSAGE} 张图片`)
    return
  }
  const take = images.slice(0, room)
  if (images.length > room) {
    ElMessage.warning(`最多 ${MAX_IMAGES_PER_MESSAGE} 张，已忽略多余文件`)
  }
  take.forEach((file) => doUpload(file))
}

function patchPending(sessionKey, localKey, patch) {
  const apply = (arr) => {
    const i = arr.findIndex((x) => x.localKey === localKey)
    if (i < 0) return arr
    const next = arr.slice()
    next[i] = { ...next[i], ...patch }
    return next
  }
  if (viewingKey.value === sessionKey) {
    pendingImages.value = apply(pendingImages.value)
  }
  if (drafts[sessionKey]?.pendingImages) {
    drafts[sessionKey].pendingImages = apply(drafts[sessionKey].pendingImages)
  }
}

function dropPending(sessionKey, localKey) {
  const apply = (arr) => {
    const hit = arr.find((x) => x.localKey === localKey)
    if (hit?.previewUrl?.startsWith('blob:')) URL.revokeObjectURL(hit.previewUrl)
    return arr.filter((x) => x.localKey !== localKey)
  }
  if (viewingKey.value === sessionKey) {
    pendingImages.value = apply(pendingImages.value)
  }
  if (drafts[sessionKey]?.pendingImages) {
    drafts[sessionKey].pendingImages = apply(drafts[sessionKey].pendingImages)
  }
}

/** 校验并上传图片，成功后展示缩略图 */
async function doUpload(file) {
  if (!ALLOWED_IMAGE_TYPES.includes(file.type)) {
    ElMessage.error('仅支持 jpg / png / webp 格式的图片')
    return
  }
  if (file.size > MAX_IMAGE_SIZE) {
    ElMessage.error('图片不能超过 10MB')
    return
  }
  const key = viewingKey.value
  if (!key) return
  if ((viewingKey.value === key ? pendingImages.value : drafts[key]?.pendingImages || []).length >= MAX_IMAGES_PER_MESSAGE) {
    ElMessage.warning(`最多上传 ${MAX_IMAGES_PER_MESSAGE} 张图片`)
    return
  }
  const localKey = `pimg-${++pendingLocalSeq}`
  const localUrl = URL.createObjectURL(file)
  const localPreview = { localKey, id: null, fileName: file.name, previewUrl: localUrl, uploading: true }
  if (viewingKey.value === key) {
    pendingImages.value = [...pendingImages.value, localPreview]
  } else {
    const prev = drafts[key]?.pendingImages || []
    drafts[key] = { input: drafts[key]?.input || '', pendingImages: [...prev, localPreview] }
  }
  try {
    const res = await uploadChatImage(file)
    const data = res?.data
    if (!data?.id) {
      dropPending(key, localKey)
      return
    }
    URL.revokeObjectURL(localUrl)
    patchPending(key, localKey, {
      id: data.id,
      fileName: data.fileName || file.name,
      previewUrl: data.previewUrl,
      uploading: false,
    })
  } catch {
    dropPending(key, localKey)
  }
}

/** 移除一张待发送图片 */
function removePendingImage(localKey) {
  dropPending(viewingKey.value, localKey)
}

function isPendingImage(imageId) {
  if (imageId == null || imageId === '') return false
  return pendingImages.value.some((p) => String(p.id) === String(imageId))
}

/** 把历史里的某一张带到本轮提问，不重新上传；再点一次取消 */
function toggleHistoryImage(img) {
  if (viewingStreaming.value) return
  if (!img?.id) {
    ElMessage.warning('该图片无法引用')
    return
  }
  const hit = pendingImages.value.find((p) => String(p.id) === String(img.id))
  if (hit) {
    removePendingImage(hit.localKey)
    return
  }
  if (pendingImages.value.length >= MAX_IMAGES_PER_MESSAGE) {
    ElMessage.warning(`最多 ${MAX_IMAGES_PER_MESSAGE} 张图片`)
    return
  }
  pendingImages.value = [
    ...pendingImages.value,
    {
      localKey: `pimg-${++pendingLocalSeq}`,
      id: String(img.id),
      fileName: img.fileName || '图片',
      previewUrl: img.previewUrl,
      uploading: false,
    },
  ]
}

/**
 * 发送消息（流式）。
 *
 * @param {string} [overrideContent] 重新生成时复用的提问原文
 * @param {Array}  [overrideImages]  重新生成时复用的原消息图片（含 id）
 */
async function handleSend(overrideContent, overrideImages) {
  const isRegenerate = overrideContent !== undefined
  const content = (overrideContent ?? input.value).trim()
  // 纯图片提问也允许发送，但二者不能都为空
  if (isRegenerate) {
    if (!content && !overrideImages?.length) return
  } else if ((!content && !pendingImages.value.length) || pendingBusy.value) {
    return
  }
  const startKey = viewingKey.value || newPendingKey()
  if (!viewingKey.value) viewingKey.value = startKey
  if (streams[startKey] && !streams[startKey].finalized) return
  if (!isRegenerate) {
    input.value = ''
    clearDraft(startKey)
  }
  // 图片只在本轮生效；重新生成时复用原消息图片的 id
  const imageIds = isRegenerate
    ? (overrideImages || []).map((i) => String(i.id)).filter(Boolean)
    : pendingImages.value.map((i) => String(i.id)).filter(Boolean)
  const sendConversationId = isPendingKey(startKey) ? null : currentConversationId.value
  // 先落一条 user 消息到界面（后端会再落库）；重新生成不重复插入
  if (!isRegenerate) {
    messages.value.push({
      id: `local-${Date.now()}`,
      role: 'user',
      content,
      images: pendingImages.value.map((i) => ({ ...i })),
      createTime: new Date().toISOString(),
    })
    pendingImages.value.forEach((i) => {
      if (i.previewUrl?.startsWith('blob:')) URL.revokeObjectURL(i.previewUrl)
    })
    pendingImages.value = []
    await scrollBottom()
  }

  const job = reactive({
    id: startKey,
    text: '',
    citations: [],
    regenerate: isRegenerate,
    finalized: false,
    aborter: null,
    startedAt: new Date().toISOString(),
    // 工具调用状态：初始即显示"正在思考"，tool_call 置 running + 文案，tool_result 停转圈回到"正在思考"
    toolRunning: false,
    toolStatus: '正在思考…',
    // 写操作二次确认：confirm_required 时填充，用户点确认/取消后清空
    pendingConfirm: null,
  })
  streams[startKey] = job
  // job 入 streams 后流式气泡（闪烁的 |）才渲染出来，再滚一次到底，避免思考位置被顶出可视区
  await scrollBottom()
  let doneCalled = false
  const finish = (errMsg) => {
    if (doneCalled) return
    doneCalled = true
    finalizeJob(job.id, errMsg)
  }

  job.aborter = chatStream(
    {
      conversationId: sendConversationId,
      content,
      imageIds,
      regenerate: isRegenerate,
    },
    {
      onConversation: (conversationId) => {
        if (!conversationId) return
        const prevKey = job.id
        const realKey = String(conversationId)
        if (job.id !== realKey) {
          streams[realKey] = job
          delete streams[job.id]
          migrateDraft(job.id, realKey)
          job.id = realKey
        }
        // 人还在看这一路才改高亮；已经切到别的新建/会话就不要拽回来
        if (isViewing(prevKey) || isViewing(realKey)) {
          currentConversationId.value = conversationId
          viewingKey.value = realKey
        }
        fetchSessions()
      },
      onChunk: (t) => {
        if (job.finalized) return
        // 正文首字到达，工具状态标签退场（此前一直由它填补 tool_result 后的生成空置期）
        if (!job.text && job.toolStatus) job.toolStatus = ''
        job.text += t
        if (isViewing(job.id)) scrollBottom()
      },
      onCitations: (sources) => {
        if (job.finalized) return
        job.citations = uniqueCitations(sources)
      },
      onToolCall: (event) => {
        if (job.finalized) return
        // 模型发起工具调用：亮"正在查询…"转圈（工具名映射成可读文案）
        job.toolRunning = true
        job.toolStatus = TOOL_LABEL[event?.tool] || '正在查询业务数据…'
        if (isViewing(job.id)) scrollBottom()
      },
      onToolResult: (event) => {
        if (job.finalized) return
        // 查询完成：停转圈，回到"正在思考"，直到正文首字到达（onChunk）再退场
        job.toolRunning = false
        job.toolStatus = '正在思考…'
        if (isViewing(job.id)) scrollBottom()
      },
      onConfirmRequired: (event) => {
        if (job.finalized) return
        // 写工具：模型只提了提案，等用户点确认才落库；停转圈，弹出确认卡片
        job.toolRunning = false
        job.toolStatus = ''
        job.pendingConfirm = {
          tool: event?.tool,
          proposalId: event?.proposalId,
          actionSummary: event?.actionSummary || '确认执行该操作',
          confirming: false,
          confirmed: false,
        }
        if (isViewing(job.id)) scrollBottom()
      },
      onDone: () => {
        // 写工具待确认：Python 发 confirm_required 后即 return，done 紧随而至；
        // 此时确认卡片还挂在流上，不能收尾，否则卡片会被 finalizeJob 一起删掉。
        // 等用户点「确认/取消」后由对应 handler 手动 finalize。
        if (job.pendingConfirm) return
        finish(null)
      },
      onError: (err) => finish(err?.message || 'AI 服务异常，请稍后重试'),
    },
  )
}

/** 停止当前看着的这一路，不影响其他会话后台生成 */
function handleStop() {
  const job = activeStream.value
  if (!job || job.finalized) return
  requestCancel(job)
  job.aborter?.abort()
  finalizeJob(job.id, null, true)
}

/** 确认写操作：调后端确认接口落库，成功后把确认结果作为本条 assistant 气泡收尾 */
async function handleConfirmAction() {
  const job = activeStream.value
  const confirm = job?.pendingConfirm
  if (!confirm || !confirm.proposalId || confirm.confirming || confirm.confirmed) return
  confirm.confirming = true
  // 询盘写操作走独立审计端点（UPDATE/Inquiry），其余写操作走默认端点（CREATE/SupplierNote）
  const isInquiry = INQUIRY_WRITE_TOOLS.has(confirm.tool)
  try {
    const res = isInquiry
      ? await confirmInquiryAction(confirm.proposalId)
      : await confirmToolAction(confirm.proposalId)
    confirm.confirmed = true
    // 落库成功：以后端回传的最终文案覆盖气泡（后端已同步落库该文案，历史刷新后一致）
    job.text = res?.data?.finalText || (isInquiry ? '已完成操作' : '已记录供应商备注')
    finalizeJob(job.id, null)
  } catch {
    // 拦截器已弹错误；卡片保留，用户可重试或取消
  } finally {
    confirm.confirming = false
  }
}

/** 取消写操作：调后端取消端点消费提案 + 覆盖气泡为取消文案，收尾后历史一致 */
async function handleCancelConfirm() {
  const job = activeStream.value
  const confirm = job?.pendingConfirm
  if (!confirm || !confirm.proposalId || confirm.confirming) return
  confirm.confirming = true
  try {
    const res = await cancelToolAction(confirm.proposalId)
    // 后端已消费提案并把气泡覆盖成取消文案，本地同步
    job.text = res?.data?.finalText || '好的，已取消该操作，未做任何变更。'
    job.pendingConfirm = null
    finalizeJob(job.id, null)
  } catch {
    // 拦截器已弹错误；卡片保留，用户可重试
  } finally {
    confirm.confirming = false
  }
}

/** 关页 / F5：把所有进行中的路都按停止收口，axios 来不及发 */
function flushCancelOnLeave() {
  Object.keys(streams).forEach((key) => {
    const job = streams[key]
    if (!job || job.finalized) return
    requestCancel(job, true)
    job.aborter?.abort()
  })
}

onMounted(() => {
  window.addEventListener('pagehide', flushCancelOnLeave)
})

onBeforeUnmount(() => {
  window.removeEventListener('pagehide', flushCancelOnLeave)
  flushCancelOnLeave()
})

/**
 * 收尾某一路流。人还在这个会话才改当前气泡；已经切走则只清任务，回来靠 listMessages。
 *
 * @param {string} jobId 流的 key（会话 ID 或 pending）
 * @param {string|null} errMsg 失败文案，主动停止传 null
 * @param {boolean} [stopped=false] 用户点了停止生成
 */
async function finalizeJob(jobId, errMsg, stopped = false) {
  const key = streamKey(jobId)
  const job = streams[key]
  if (!job) return
  if (!job.finalized) job.finalized = true
  const text = job.text
  const isRegenerate = job.regenerate
  const viewing = isViewing(job.id)
  const status = errMsg ? 'error' : (stopped ? 'cancelled' : 'complete')
  if (!viewing && text && !isPendingKey(job.id)) {
    leftovers[job.id] = { text, citations: job.citations, status, createTime: job.startedAt }
  }
  delete streams[key]

  if (isRegenerate && (stopped || errMsg)) {
    if (viewing && currentConversationId.value) {
      await switchTo({ id: currentConversationId.value })
    }
    fetchSessions()
    if (errMsg && !stopped) ElMessage.error(errMsg)
    return
  }
  if (viewing) {
    if (text) {
      messages.value.push({
        id: `assistant-${Date.now()}`,
        role: 'assistant',
        content: text,
        citations: job.citations,
        status,
        createTime: job.startedAt || new Date().toISOString(),
      })
    } else if (errMsg) {
      ElMessage.error(errMsg)
      const last = messages.value[messages.value.length - 1]
      if (!isRegenerate && last?.id?.toString().startsWith('local-')) {
        messages.value.pop()
      }
    }
    scrollBottom()
    if (isRegenerate && !text && currentConversationId.value) {
      await switchTo({ id: currentConversationId.value })
    }
  } else if (errMsg && !stopped) {
    ElMessage.error(errMsg)
  }
  fetchSessions()
}

/**
 * 打开引用来源文档预览。documentId 为空则无法现签，只提示。
 *
 * @param {Object} citation 后端 citations 项
 */
async function handlePreviewCitation(citation) {
  if (!citation?.documentId) {
    ElMessage.warning('该来源没有可预览的文档')
    return
  }
  try {
    const res = await previewDocument(citation.documentId)
    const url = res?.data || ''
    if (!url) {
      ElMessage.warning('预览地址获取失败')
      return
    }
    previewUrl.value = url
    previewTitle.value = citation.fileName || '知识库文档'
    previewVisible.value = true
  } catch {
    /* 拦截器已处理 */
  }
}

/** 仅最后一条 assistant 消息显示操作按钮 */
function isLastAssistant(msg) {
  const last = [...messages.value].reverse().find((m) => m.role === 'assistant')
  return last?.id === msg.id
}

/** 复制回答原文 */
async function handleCopy(msg) {
  try {
    await navigator.clipboard.writeText(msg.content || '')
    ElMessage.success('已复制')
  } catch {
    ElMessage.error('复制失败，请手动选择文本')
  }
}

/**
 * 重新生成：先从界面摘掉该回答，用其前面最近的用户提问重发。
 * 中途停止会重新拉历史，旧回答仍在库里。
 * 原提问带图时一并带回 imageIds（图片资产已落库，后端会按 id 重新现签 URL）。
 */
function handleRegenerate(msg) {
  if (viewingStreaming.value) return
  const idx = messages.value.indexOf(msg)
  if (idx < 0) return
  let question
  let questionImages = []
  for (let i = idx - 1; i >= 0; i--) {
    if (messages.value[i].role === 'user') {
      question = messages.value[i].content
      questionImages = messages.value[i].images || []
      break
    }
  }
  if (!question && !questionImages.length) return
  messages.value.splice(idx, 1)
  handleSend(question, questionImages)
}

async function scrollBottom() {
  await nextTick()
  if (msgBox.value) {
    msgBox.value.scrollTop = msgBox.value.scrollHeight
  }
}

</script>

<style>
.ai-drawer .el-drawer__body {
  padding: 0;
  overflow: hidden;
}

.ai-drawer-wrap {
  display: flex;
  flex-direction: row;
  height: 100%;
  overflow: hidden;
}

/* ---- 左侧会话栏 ---- */

.ai-session-panel {
  width: 240px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #ebeef5;
  background: #f8fafc;
}

.ai-session-header {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  border-bottom: 1px solid #ebeef5;
  background: #fff;
  flex-shrink: 0;
}

.ai-session-heading {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: #111827;
}

.ai-session-new {
  padding: 0 4px;
}

/* ---- 右侧聊天区 ---- */

.ai-chat-panel {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.ai-chat-header {
  height: 56px;
  display: flex;
  align-items: center;
  padding: 0 16px;
  border-bottom: 1px solid #ebeef5;
  background: #fff;
  flex-shrink: 0;
}

.ai-chat-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.ai-drawer-title {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a2e;
  flex-shrink: 0;
}

.ai-msg-box {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.ai-msg {
  display: flex;
  gap: 10px;
}

.ai-msg.user {
  flex-direction: row-reverse;
}

.ai-msg-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #ecf5ff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.ai-msg.user .ai-msg-avatar {
  background: transparent;
}

.ai-msg.assistant .ai-msg-avatar {
  background: transparent;
}

.ai-msg-content {
  max-width: 75%;
  min-width: 0;
}

.ai-msg-text {
  background: #ecf5ff;
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 14px;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
}

.ai-msg-md {
  font-size: 14px;
  line-height: 1.75;
  color: #303133;
  word-break: break-word;
}

/* ---- Markdown 排版 ---- */

.ai-msg-md>*:first-child {
  margin-top: 0;
}

.ai-msg-md>*:last-child {
  margin-bottom: 0;
}

.ai-msg-md p {
  margin: 0 0 10px;
}

.ai-msg-md h1,
.ai-msg-md h2,
.ai-msg-md h3,
.ai-msg-md h4 {
  margin: 18px 0 8px;
  font-weight: 600;
  line-height: 1.4;
  color: #1a1a2e;
}

.ai-msg-md h1 {
  font-size: 18px;
  padding-bottom: 6px;
  border-bottom: 1px solid #ebeef5;
}

.ai-msg-md h2 {
  font-size: 16px;
  padding-bottom: 4px;
  border-bottom: 1px solid #f2f3f5;
}

.ai-msg-md h3 {
  font-size: 15px;
}

.ai-msg-md h4 {
  font-size: 14px;
  color: #606266;
}

.ai-msg-md strong {
  font-weight: 600;
  color: #1a1a2e;
}

.ai-msg-md em {
  font-style: italic;
  color: #606266;
}

.ai-msg-md ul,
.ai-msg-md ol {
  margin: 0 0 10px;
  padding-left: 22px;
}

.ai-msg-md li {
  margin: 4px 0;
}

.ai-msg-md li>ul,
.ai-msg-md li>ol {
  margin: 4px 0;
}

.ai-msg-md blockquote {
  margin: 10px 0;
  padding: 6px 12px;
  border-left: 3px solid #c6e2ff;
  background: #f7fbff;
  color: #5a6570;
}

.ai-msg-md blockquote p {
  margin: 0;
}

.ai-msg-md hr {
  margin: 16px 0;
  border: 0;
  border-top: 1px solid #ebeef5;
}

.ai-msg-md a {
  color: #409eff;
  text-decoration: none;
}

.ai-msg-md a:hover {
  text-decoration: underline;
}

/* 行内代码与代码块 */
.ai-msg-md code {
  background: #f6f8fa;
  padding: 2px 5px;
  border-radius: 4px;
  font-size: 13px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.ai-msg-md pre {
  background: #f6f8fa;
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 0 0 10px;
}

.ai-msg-md pre code {
  background: none;
  padding: 0;
  font-size: 13px;
}

/* 表格：横向可滚动，避免窄气泡里挤压换行 */
.ai-msg-md table {
  display: block;
  width: max-content;
  max-width: 100%;
  overflow-x: auto;
  border-collapse: collapse;
  margin: 10px 0;
  font-size: 13px;
}

.ai-msg-md th,
.ai-msg-md td {
  padding: 7px 12px;
  border: 1px solid #e4e7ed;
  text-align: left;
  white-space: nowrap;
}

.ai-msg-md thead th {
  background: #f5f7fa;
  font-weight: 600;
  color: #1a1a2e;
}

.ai-msg-md tbody tr:nth-child(even) {
  background: #fafafa;
}

.ai-msg-md tbody tr:hover {
  background: #f0f7ff;
}

/* ---- 消息内图片 ---- */

.ai-msg-images {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 6px;
  justify-content: flex-end;
}

.ai-msg-images.assistant {
  justify-content: flex-start;
}

.ai-msg-image-wrap {
  position: relative;
  width: 140px;
  height: 140px;
  border-radius: 8px;
  overflow: hidden;
}

.ai-msg-image-wrap.selected {
  outline: 2px solid #409eff;
  outline-offset: 1px;
}

.ai-msg-image {
  width: 140px;
  height: 140px;
  border-radius: 8px;
  border: 1px solid #ebeef5;
  cursor: zoom-in;
  overflow: hidden;
}

.ai-msg-image-use {
  position: absolute;
  left: 6px;
  right: 6px;
  bottom: 6px;
  z-index: 2;
  border: none;
  border-radius: 4px;
  padding: 4px 0;
  font-size: 12px;
  color: #fff;
  background: rgba(0, 0, 0, 0.55);
  cursor: pointer;
}

.ai-msg-image-use:hover {
  background: #409eff;
}

/* ---- 待发送图片缩略图 ---- */

.ai-pending-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 6px;
}

.ai-pending-image {
  position: relative;
  width: 56px;
  height: 56px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  overflow: hidden;
  background: #fafbfc;
}

.ai-pending-thumb {
  width: 56px;
  height: 56px;
}

.ai-pending-mask {
  position: absolute;
  top: 2px;
  right: 2px;
  color: #fff;
  display: flex;
}

.ai-pending-remove {
  background: rgba(0, 0, 0, 0.45);
  border-radius: 50%;
  padding: 2px;
  cursor: pointer;
  font-size: 12px;
}

.ai-pending-remove:hover {
  background: #f56c6c;
}

.ai-upload-btn {
  margin-right: auto;
  padding: 0 4px;
  color: #909399;
}

/* ---- 消息操作按钮 ---- */

.ai-msg-time {
  margin-top: 4px;
  font-size: 11px;
  line-height: 1.2;
  color: #c0c4cc;
}

.ai-msg.user .ai-msg-time {
  text-align: right;
}

.ai-msg-actions {
  display: flex;
  gap: 2px;
  margin-top: 4px;
  opacity: 0.45;
  transition: opacity 0.2s;
}

.ai-msg-actions:hover {
  opacity: 1;
}

.ai-cursor {
  color: #409eff;
  animation: ai-blink 1s infinite;
}

@keyframes ai-blink {

  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0;
  }
}

.ai-input-bar {
  padding: 12px 16px 14px;
  border-top: 1px solid #ebeef5;
  background: #fff;
}

/* 卡片式输入区：整体一个圆角框，聚焦时描边，内部 textarea 无边框 */
.ai-input-box {
  border: 1px solid #dcdfe6;
  border-radius: 10px;
  padding: 8px 10px 6px;
  background: #fff;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.ai-input-box:focus-within {
  border-color: #409eff;
  box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.12);
}

.ai-input-box.is-streaming {
  background: #fafbfc;
}

.ai-input-box .ai-input-field .el-textarea__inner {
  border: none !important;
  box-shadow: none !important;
  padding: 0;
  font-size: 14px;
  line-height: 1.6;
  background: transparent;
  min-height: 42px !important;
}

.ai-input-box .ai-input-field .el-textarea__inner::placeholder {
  color: #a8abb2;
}

/* 输入框内按钮行：提示文案靠左，按钮靠右 */
.ai-input-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 2px;
}

.ai-send-btn {
  padding: 0 4px;
}

.ai-stop-icon {
  margin-right: 3px;
}

.ai-session-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.ai-session-empty {
  margin: 24px 8px;
  text-align: center;
  font-size: 12px;
  color: #909399;
}

.ai-session-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 8px 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
}

.ai-session-item:hover {
  background: #fff;
}

.ai-session-item.active {
  background: #ecf5ff;
}

.ai-session-item.active:hover {
  background: #d9ecff;
}

.ai-session-item.active .ai-session-title {
  color: #409eff;
  font-weight: 600;
}

.ai-session-draft {
  cursor: default;
}

.ai-session-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.ai-session-title {
  font-size: 13px;
  line-height: 1.4;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-session-time {
  font-size: 11px;
  line-height: 1.2;
  color: #909399;
}

.ai-session-item.active .ai-session-time {
  color: #79bbff;
}

.ai-session-trail {
  position: relative;
  width: 22px;
  height: 22px;
  flex-shrink: 0;
}

.ai-session-generating,
.ai-session-delete {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ai-session-generating {
  font-size: 14px;
  color: #409eff;
}

.ai-session-delete {
  border: none;
  padding: 0;
  border-radius: 4px;
  background: transparent;
  color: #c0c4cc;
  cursor: pointer;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.15s, color 0.15s, background 0.15s;
}

.ai-session-item:hover .ai-session-delete,
.ai-session-item:focus-within .ai-session-delete {
  opacity: 1;
  pointer-events: auto;
}

.ai-session-item:hover .ai-session-generating,
.ai-session-item:focus-within .ai-session-generating {
  opacity: 0;
}

.ai-session-delete:hover {
  color: #f56c6c;
  background: #fef0f0;
}

@media (hover: none) {
  .ai-session-delete {
    opacity: 1;
    pointer-events: auto;
  }

  .ai-session-generating {
    position: static;
    opacity: 1 !important;
  }

  .ai-session-delete {
    position: static;
  }

  .ai-session-trail {
    width: auto;
    gap: 2px;
    display: flex;
    align-items: center;
  }
}

/* ---- 窄屏：上下布局 ---- */

@media (max-width: 768px) {
  .ai-drawer-wrap {
    flex-direction: column;
  }

  .ai-session-panel {
    width: 100%;
    max-height: 38vh;
    border-right: 0;
    border-bottom: 1px solid #ebeef5;
  }

  .ai-chat-panel {
    flex: 1;
    min-height: 0;
  }
}

.ai-msg-citations {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}

.ai-citation-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: none;
  padding: 0;
  background: transparent;
  cursor: pointer;
  color: #909399;
}

.ai-citation-toggle:hover {
  color: #409eff;
}

.ai-citation-arrow {
  transition: transform 0.2s;
}

.ai-citation-arrow.open {
  transform: rotate(180deg);
}

.ai-citation-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.ai-citation-label {
  font-size: 12px;
  color: inherit;
}

.ai-msg-stopped {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}

/* 工具调用状态：紧跟光标的纯文字轻提示，无背景盒子 */
.ai-tool-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-left: 8px;
  font-size: 12px;
  color: #b1b3b8;
  vertical-align: middle;
}

/* 写操作二次确认卡片：模型发起写工具后，等用户点确认才落库 */
.ai-confirm-card {
  margin-top: 8px;
  padding: 12px 14px;
  border-radius: 10px;
  background: #fdf6ec;
  border: 1px solid #f5dab1;
  font-size: 13px;
}

.ai-confirm-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}

.ai-confirm-icon {
  color: #e6a23c;
  font-size: 16px;
}

.ai-confirm-title {
  font-size: 13px;
  font-weight: 600;
  color: #e6a23c;
  line-height: 1;
}

.ai-confirm-text {
  color: #303133;
  line-height: 1.6;
  word-break: break-word;
  margin-bottom: 12px;
  padding-left: 22px;
}

.ai-confirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 10px;
  border-top: 1px solid #f5dab1;
}

.ai-citation-chip {
  max-width: 260px;
  padding: 2px 10px;
  overflow: hidden;
  border: 1px solid #d9ecff;
  border-radius: 12px;
  background: #ecf5ff;
  color: #409eff;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
}

.ai-citation-section {
  color: #909399;
}

.ai-citation-preview-dialog {
  width: auto !important;
  max-width: 80vw;
}

.ai-citation-preview-frame {
  width: 100%;
  height: 78vh;
  border: 0;
  background: #fff;
}
</style>
