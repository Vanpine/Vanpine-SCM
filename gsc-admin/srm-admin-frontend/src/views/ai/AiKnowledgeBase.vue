<template>
  <!--
    AI 知识库 — 双栏资料工作台
    左侧负责“找文档”，右侧负责“看详情与处理异常”，避免单列页面信息拥挤。
    刻意不用 el-table：保持资料清单感，与业务数据列表拉开距离。
  -->
  <div class="ai-kb-page">
    <div class="ai-kb-toolbar">
      <div class="ai-kb-heading">
        <span class="ai-kb-title">AI知识库</span>
        <span v-if="list.length" class="ai-kb-subtitle">
          {{ list.length }} 个文档 · {{ readyCount }} 个可用于问答
        </span>
      </div>
      <el-button
        type="primary"
        :icon="Upload"
        :loading="uploading"
        @click="uploadDialogVisible = true"
      >
        上传文档
      </el-button>
    </div>

    <div v-if="list.length" class="ai-kb-metrics">
      <div class="ai-kb-metric">
        <span class="ai-kb-metric-value">{{ list.length }}</span>
        <span class="ai-kb-metric-label">总文档</span>
      </div>
      <div class="ai-kb-metric">
        <span class="ai-kb-metric-value is-ready">{{ readyCount }}</span>
        <span class="ai-kb-metric-label">可用于问答</span>
      </div>
      <div class="ai-kb-metric">
        <span class="ai-kb-metric-value is-processing">{{ processingCount }}</span>
        <span class="ai-kb-metric-label">解析中</span>
      </div>
      <div class="ai-kb-metric">
        <span class="ai-kb-metric-value is-failed">{{ failedCount }}</span>
        <span class="ai-kb-metric-label">解析失败</span>
      </div>
      <div class="ai-kb-metric">
        <span class="ai-kb-metric-value">{{ chunkCount }}</span>
        <span class="ai-kb-metric-label">知识分块</span>
      </div>
    </div>

    <div v-if="loading" class="ai-kb-skeleton">
      <el-skeleton :rows="6" animated />
    </div>

    <div v-else-if="list.length === 0" class="ai-kb-empty">
      <el-upload
        class="ai-kb-dropzone"
        drag
        :auto-upload="false"
        :show-file-list="false"
        accept=".txt,.md,.pdf,.docx,.xlsx,.xls"
        :disabled="uploading"
        :on-change="handleUpload"
      >
        <el-icon class="ai-kb-dropzone-icon"><UploadFilled /></el-icon>
        <div class="ai-kb-dropzone-main">把文件拖到这里，构建你的知识库</div>
        <div class="ai-kb-dropzone-sub">支持 TXT / MD / PDF / DOCX / XLSX，单文件不超过 10MB</div>
      </el-upload>
    </div>

    <div v-else class="ai-kb-workspace">
      <section class="ai-kb-list-panel">
        <div class="ai-kb-filterbar">
          <el-input
            v-model="keyword"
            class="ai-kb-search"
            placeholder="搜索文件名"
            clearable
            :prefix-icon="Search"
          />
          <el-radio-group v-model="statusFilter" size="small" class="ai-kb-filter">
            <el-radio-button label="all">全部</el-radio-button>
            <el-radio-button label="ready">可用</el-radio-button>
            <el-radio-button label="processing">解析中</el-radio-button>
            <el-radio-button label="failed">失败</el-radio-button>
          </el-radio-group>
        </div>

        <div v-if="filteredList.length === 0" class="ai-kb-noresult">
          <el-empty :description="emptyDescription" :image-size="80" />
        </div>

        <div v-else class="ai-kb-list">
          <div
            v-for="row in filteredList"
            :key="row.id"
            class="ai-kb-row"
            :class="{
              'is-failed': row.status === 'failed',
              'is-selected': String(row.id) === String(activeId),
            }"
            @click="activeId = row.id"
          >
            <div class="ai-kb-row-main">
              <el-icon class="ai-kb-file-icon"><Document /></el-icon>
              <span class="ai-kb-name" :title="row.fileName">{{ row.fileName }}</span>
            </div>
            <div class="ai-kb-row-meta">
              <span>{{ (row.fileType || '').toUpperCase() }}</span>
              <span v-if="row.fileSize != null">{{ formatFileSize(row.fileSize) }}</span>
              <span v-if="row.status === 'ready'">{{ row.chunkCount ?? 0 }} 块</span>
            </div>
            <div class="ai-kb-status">
              <template v-if="row.status === 'failed'">
                <el-tooltip :content="row.errorMessage || '解析失败'" placement="top" effect="light">
                  <span class="ai-kb-status-inner is-failed">
                    <el-icon><WarningFilled /></el-icon>
                    <span>失败</span>
                  </span>
                </el-tooltip>
              </template>
              <span v-else-if="row.status === 'processing'" class="ai-kb-status-inner is-processing">
                <el-icon class="is-spin"><Loading /></el-icon>
                <span>解析中</span>
              </span>
              <span v-else class="ai-kb-status-inner is-ready">
                <el-icon><CircleCheck /></el-icon>
                <span>就绪</span>
              </span>
            </div>
          </div>
        </div>
      </section>

      <aside class="ai-kb-detail-panel">
        <template v-if="selectedDoc">
          <div class="ai-kb-detail-header">
            <el-icon class="ai-kb-detail-icon"><Document /></el-icon>
            <div class="ai-kb-detail-title">
              <div class="ai-kb-detail-name" :title="selectedDoc.fileName">{{ selectedDoc.fileName }}</div>
              <div class="ai-kb-detail-status" :class="statusClass(selectedDoc.status)">
                {{ statusText(selectedDoc.status) }}
              </div>
            </div>
          </div>

          <dl class="ai-kb-detail-fields">
            <div class="ai-kb-field">
              <dt>文件类型</dt>
              <dd>{{ (selectedDoc.fileType || '-').toUpperCase() }}</dd>
            </div>
            <div class="ai-kb-field">
              <dt>文件大小</dt>
              <dd>{{ formatFileSize(selectedDoc.fileSize) }}</dd>
            </div>
            <div class="ai-kb-field">
              <dt>知识分块</dt>
              <dd>{{ selectedDoc.chunkCount ?? 0 }}</dd>
            </div>
            <div class="ai-kb-field">
              <dt>上传时间</dt>
              <dd>{{ formatTime(selectedDoc.createTime) }}</dd>
            </div>
          </dl>

          <div v-if="selectedDoc.status === 'failed'" class="ai-kb-error-box">
            <div class="ai-kb-error-title">
              <el-icon><WarningFilled /></el-icon>
              解析失败原因
            </div>
            <p>{{ selectedDoc.errorMessage || '暂无详细原因，可尝试重新解析。' }}</p>
          </div>

          <div class="ai-kb-detail-actions">
            <el-button
              type="primary"
              :icon="View"
              :loading="previewLoading"
              @click="handlePreview(selectedDoc)"
            >
              预览
            </el-button>
            <el-button
              v-if="selectedDoc.status === 'failed'"
              type="primary"
              plain
              :icon="Refresh"
              :loading="selectedDoc.reparseLoading"
              @click="handleReparse(selectedDoc)"
            >
              重新解析
            </el-button>
            <el-button
              type="danger"
              plain
              :icon="Delete"
              @click="handleDelete(selectedDoc)"
            >
              删除文档
            </el-button>
          </div>

          <div class="ai-kb-usage">
            <div class="ai-kb-usage-title">如何让文档参与问答</div>
            <p>文档状态为“就绪”后，AI 助手会按当前企业租户检索相关内容，并引用命中的知识分块。</p>
          </div>
        </template>

        <div v-else class="ai-kb-detail-empty">
          <el-empty description="选择左侧文档查看详情" :image-size="90" />
        </div>
      </aside>
    </div>

    <el-dialog
      v-model="uploadDialogVisible"
      title="上传文档"
      width="560px"
      :close-on-click-modal="false"
      class="ai-kb-upload-dialog"
    >
      <el-upload
        class="ai-kb-dropzone"
        drag
        :auto-upload="false"
        :show-file-list="false"
        accept=".txt,.md,.pdf,.docx,.xlsx,.xls"
        :disabled="uploading"
        :on-change="handleUpload"
      >
        <el-icon class="ai-kb-dropzone-icon"><UploadFilled /></el-icon>
        <div class="ai-kb-dropzone-main">{{ uploading ? '正在上传并解析…' : '将文件拖拽到此处，或点击选择文件' }}</div>
        <div class="ai-kb-dropzone-sub">支持 TXT / MD / PDF / DOCX / XLSX，单文件不超过 10MB</div>
      </el-upload>
    </el-dialog>

    <!-- 文档预览弹窗：后端现签短效 URL，直接交给 iframe 内联渲染（PDF/文本浏览器原生支持） -->
    <el-dialog
      v-model="previewVisible"
      :title="previewTitle"
      width="80%"
      top="5vh"
      class="ai-kb-preview-dialog"
      destroy-on-close
    >
      <iframe
        v-if="previewUrl"
        :src="previewUrl"
        class="ai-kb-preview-frame"
        frameborder="0"
      />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Upload, Search, Document, UploadFilled, WarningFilled, Loading,
  CircleCheck, Refresh, Delete, View,
} from '@element-plus/icons-vue'
import { listDocuments, uploadDocument, deleteDocument, reparseDocument, previewDocument } from '@/api/ai'

const keyword = ref('')
const statusFilter = ref('all')
const list = ref([])
const activeId = ref(null)
const loading = ref(false)
const uploading = ref(false)
const uploadDialogVisible = ref(false)

// 预览：后端现签短效 URL 后交给 iframe 内联渲染
const previewVisible = ref(false)
const previewUrl = ref('')
const previewTitle = ref('')
const previewLoading = ref(false)

const readyCount = computed(() => list.value.filter((item) => item.status === 'ready').length)
const processingCount = computed(() => list.value.filter((item) => item.status === 'processing').length)
const failedCount = computed(() => list.value.filter((item) => item.status === 'failed').length)
const chunkCount = computed(() =>
  list.value.reduce((total, item) => total + (item.status === 'ready' ? Number(item.chunkCount || 0) : 0), 0),
)

const filteredList = computed(() => {
  const search = keyword.value.trim().toLowerCase()
  return list.value.filter((item) => {
    const statusMatched = statusFilter.value === 'all' || item.status === statusFilter.value
    const nameMatched = !search || (item.fileName || '').toLowerCase().includes(search)
    return statusMatched && nameMatched
  })
})

const selectedDoc = computed(() =>
  list.value.find((item) => String(item.id) === String(activeId.value)) || null,
)

const emptyDescription = computed(() => {
  if (keyword.value.trim()) return '没有找到匹配「' + keyword.value.trim() + '」的文档'
  if (statusFilter.value === 'ready') return '暂无可用于问答的文档'
  if (statusFilter.value === 'processing') return '暂无解析中的文档'
  if (statusFilter.value === 'failed') return '暂无解析失败的文档'
  return '暂无知识库文档'
})

function formatFileSize(bytes) {
  if (bytes == null) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

function formatTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  const pad = (num) => String(num).padStart(2, '0')
  return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate()) + ' ' + pad(date.getHours()) + ':' + pad(date.getMinutes())
}

function statusText(status) {
  return { ready: '就绪', processing: '解析中', failed: '解析失败' }[status] || status || '-'
}

function statusClass(status) {
  return 'is-' + (status || 'ready')
}

function syncSelection() {
  if (!list.value.length) {
    activeId.value = null
    return
  }
  const exists = list.value.some((item) => String(item.id) === String(activeId.value))
  if (!exists) activeId.value = list.value[0].id
}

async function fetchList() {
  loading.value = true
  try {
    const res = await listDocuments()
    list.value = res?.data || []
    syncSelection()
  } catch {
    list.value = []
    activeId.value = null
  } finally {
    loading.value = false
  }
}

async function handleUpload(file) {
  const raw = file.raw
  const name = (raw?.name || '').toLowerCase()
  const okExt = ['.txt', '.md', '.pdf', '.docx', '.xlsx', '.xls']
  if (!okExt.some((ext) => name.endsWith(ext))) {
    ElMessage.warning('仅支持 TXT / MD / PDF / DOCX / XLSX 格式')
    return
  }
  if (raw?.size > 10 * 1024 * 1024) {
    ElMessage.warning('文档大小不能超过 10MB')
    return
  }

  uploading.value = true
  try {
    const res = await uploadDocument(raw)
    const doc = res?.data
    if (doc?.status === 'ready') {
      ElMessage.success('文档「' + doc.fileName + '」已解析，' + doc.chunkCount + ' 块')
    } else if (doc?.status === 'failed') {
      ElMessage.warning('文档上传成功但解析失败，可在右侧查看原因并重试')
    } else {
      ElMessage.success('文档已上传')
    }
    uploadDialogVisible.value = false
    await fetchList()
    if (doc?.id) activeId.value = doc.id
  } catch {
    // 请求异常由 axios 拦截器统一提示
  } finally {
    uploading.value = false
  }
}

async function handleReparse(row) {
  row.reparseLoading = true
  try {
    const res = await reparseDocument(row.id)
    const updated = res?.data
    ElMessage.success(updated?.status === 'ready' ? '重新解析成功' : '已重新提交解析')
    await fetchList()
    if (row.id) activeId.value = row.id
  } catch {
    // 请求异常由 axios 拦截器统一提示
  } finally {
    row.reparseLoading = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      '确认删除文档「' + row.fileName + '」吗？删除后该文档将无法再参与问答。',
      '删除文档',
      { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }

  try {
    await deleteDocument(row.id)
    ElMessage.success('文档已删除')
    activeId.value = null
    await fetchList()
  } catch {
    // 请求异常由 axios 拦截器统一提示
  }
}

/** 打开预览：向后端要一个现签的短效 URL（10 分钟），交给 iframe 内联渲染 */
async function handlePreview(row) {
  previewLoading.value = true
  try {
    const res = await previewDocument(row.id)
    previewUrl.value = res?.data || ''
    if (!previewUrl.value) {
      ElMessage.warning('预览地址获取失败')
      return
    }
    previewTitle.value = row.fileName
    previewVisible.value = true
  } catch {
    // 请求异常由 axios 拦截器统一提示
  } finally {
    previewLoading.value = false
  }
}

onMounted(fetchList)</script>

<style>
.ai-kb-page {
  max-width: 1280px;
  margin: 0 auto;
  padding: 4px 0 40px;
  font-size: 14px;
}
.ai-kb-page * {
  font-size: 14px;
}

.ai-kb-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.ai-kb-heading { min-width: 0; }
.ai-kb-title {
  display: block;
  font-size: 18px !important;
  font-weight: 650;
  color: #17233d;
  letter-spacing: -0.2px;
}
.ai-kb-subtitle {
  display: block;
  margin-top: 4px;
  font-size: 12px !important;
  color: #7a8699;
}

.ai-kb-metrics {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 16px;
  padding: 14px 16px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 12px;
}
.ai-kb-metric { min-width: 0; }
.ai-kb-metric-value {
  display: block;
  font-size: 20px !important;
  font-weight: 700;
  color: #303133;
}
.ai-kb-metric-value.is-ready { color: #16a34a; }
.ai-kb-metric-value.is-processing { color: #2563eb; }
.ai-kb-metric-value.is-failed { color: #dc2626; }
.ai-kb-metric-label {
  display: block;
  margin-top: 3px;
  font-size: 12px !important;
  color: #8a94a6;
}

.ai-kb-skeleton {
  padding: 24px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 12px;
}
.ai-kb-empty,
.ai-kb-noresult { padding: 20px 0; }

.ai-kb-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 16px;
  align-items: start;
}

.ai-kb-list-panel {
  min-width: 0;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 12px;
  overflow: hidden;
}
.ai-kb-filterbar {
  display: grid;
  grid-template-columns: minmax(180px, 280px) auto;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-bottom: 1px solid #f1f3f7;
}
.ai-kb-search .el-input__wrapper {
  box-shadow: none !important;
  background: #f6f8fb;
  border-radius: 8px;
}
.ai-kb-search .el-input__wrapper.is-focus {
  background: #fff;
  box-shadow: 0 0 0 1px #409eff inset !important;
}
.ai-kb-filter .el-radio-button__inner {
  border: none !important;
  background: transparent !important;
  color: #697586 !important;
  box-shadow: none !important;
}
.ai-kb-filter .el-radio-button__original-radio:checked + .el-radio-button__inner {
  background: #ecf5ff !important;
  color: #1d6fd8 !important;
  border-radius: 6px;
}

.ai-kb-list {
  max-height: calc(100vh - 330px);
  min-height: 320px;
  overflow-y: auto;
}
.ai-kb-row {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 56px;
  padding: 10px 16px;
  cursor: pointer;
  border-left: 3px solid transparent;
  transition: background 0.15s, border-color 0.15s;
}
.ai-kb-row:hover { background: #f8fafc; }
.ai-kb-row.is-selected {
  background: #f1f7ff;
  border-left-color: #2f80ed;
}
.ai-kb-row.is-failed { border-left-color: #dc2626; }
.ai-kb-row-main {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.ai-kb-file-icon {
  flex-shrink: 0;
  color: #8a94a6;
  font-size: 17px !important;
}
.ai-kb-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 550;
  color: #303133;
}
.ai-kb-row-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  font-size: 12px !important;
  color: #8a94a6;
}
.ai-kb-status {
  flex-shrink: 0;
  width: 68px;
  display: flex;
  justify-content: flex-end;
}
.ai-kb-status-inner {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px !important;
}
.ai-kb-status-inner .el-icon { font-size: 14px !important; }
.ai-kb-status-inner.is-ready { color: #77839a; }
.ai-kb-status-inner.is-ready .el-icon { color: #16a34a; }
.ai-kb-status-inner.is-processing { color: #2563eb; }
.ai-kb-status-inner.is-failed { color: #dc2626; }
.is-spin { animation: ai-kb-spin 1s linear infinite; }

.ai-kb-detail-panel {
  position: sticky;
  top: 16px;
  min-width: 0;
  padding: 18px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 12px;
  box-shadow: 0 8px 28px rgba(23, 35, 61, 0.04);
}
.ai-kb-detail-header {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding-bottom: 14px;
  border-bottom: 1px solid #f1f3f7;
}
.ai-kb-detail-icon {
  flex-shrink: 0;
  padding: 10px;
  color: #2f6fed;
  background: #f1f6ff;
  border-radius: 10px;
  font-size: 18px !important;
}
.ai-kb-detail-title { min-width: 0; }
.ai-kb-detail-name {
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  font-weight: 650;
  color: #22304a;
}
.ai-kb-detail-status {
  display: inline-flex;
  margin-top: 6px;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px !important;
}
.ai-kb-detail-status.is-ready { color: #15803d; background: #ecfdf3; }
.ai-kb-detail-status.is-processing { color: #1d4ed8; background: #eff5ff; }
.ai-kb-detail-status.is-failed { color: #b91c1c; background: #fef2f2; }

.ai-kb-detail-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin: 16px 0 0;
}
.ai-kb-field {
  padding: 10px 11px;
  background: #f8fafc;
  border-radius: 8px;
}
.ai-kb-field dt { font-size: 12px !important; color: #8a94a6; }
.ai-kb-field dd {
  margin: 4px 0 0;
  font-weight: 600;
  color: #303133;
}

.ai-kb-error-box {
  margin-top: 14px;
  padding: 11px 12px;
  background: #fff5f5;
  border: 1px solid #fecaca;
  border-radius: 8px;
}
.ai-kb-error-title {
  display: flex;
  align-items: center;
  gap: 5px;
  color: #b91c1c;
  font-weight: 600;
}
.ai-kb-error-title .el-icon { font-size: 14px !important; }
.ai-kb-error-box p {
  margin: 6px 0 0;
  color: #7f1d1d;
  line-height: 1.6;
  word-break: break-word;
}

.ai-kb-detail-actions {
  display: flex;
  gap: 8px;
  margin-top: 16px;
}
.ai-kb-detail-actions .el-button { flex: 1; }

.ai-kb-usage {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px dashed #e3e8ef;
}
.ai-kb-usage-title {
  margin-bottom: 5px;
  color: #526070;
  font-weight: 600;
}
.ai-kb-usage p {
  margin: 0;
  color: #8a94a6;
  font-size: 12px !important;
  line-height: 1.7;
}
.ai-kb-detail-empty { padding: 20px 0; }

.ai-kb-dropzone { width: 100%; }
.ai-kb-dropzone .el-upload,
.ai-kb-dropzone .el-upload-dragger { width: 100% !important; }
.ai-kb-dropzone .el-upload-dragger {
  border: 1px dashed #d8dfeb;
  border-radius: 12px;
  background: #fafbfd;
  padding: 40px 20px;
  transition: all 0.2s;
}
.ai-kb-dropzone .el-upload-dragger:hover {
  border-color: #2f80ed;
  background: #f5f9ff;
}
.ai-kb-dropzone-icon {
  font-size: 46px !important;
  color: #b7c1d0;
  margin-bottom: 12px;
}
.ai-kb-dropzone-main { color: #526070; }
.ai-kb-dropzone-sub {
  margin-top: 5px;
  font-size: 12px !important;
  color: #8a94a6;
}
.ai-kb-upload-dialog .ai-kb-dropzone .el-upload-dragger { padding: 32px 20px; }

@media (max-width: 1180px) {
  .ai-kb-workspace { grid-template-columns: 1fr; }
  .ai-kb-detail-panel { position: static; }
  .ai-kb-metrics { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .ai-kb-list { max-height: 480px; }
}

@media (max-width: 720px) {
  .ai-kb-toolbar { align-items: flex-start; flex-direction: column; }
  .ai-kb-toolbar .el-button { width: 100%; }
  .ai-kb-filterbar { grid-template-columns: 1fr; }
  .ai-kb-filter { width: 100%; overflow-x: auto; }
  .ai-kb-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .ai-kb-row { align-items: flex-start; flex-direction: column; gap: 7px; }
  .ai-kb-status { width: auto; justify-content: flex-start; }
  .ai-kb-detail-actions { flex-direction: column; }
}

@keyframes ai-kb-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 预览弹窗：iframe 撑满可视高度，浏览器原生渲染 PDF/文本 */
.ai-kb-preview-frame {
  width: 100%;
  height: 78vh;
  border: 0;
  background: #fff;
}
</style>

