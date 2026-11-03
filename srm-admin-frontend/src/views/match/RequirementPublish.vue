<template>
  <div>
    <!-- 搜索卡片 -->
    <el-card class="search-card">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" placeholder="需求标题/品类/地域/预算" clearable style="width:240px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width:130px" @change="handleSearch">
            <el-option label="进行中" value="open" />
            <el-option label="已完成" value="completed" />
            <el-option label="已关闭" value="closed" />
            <el-option label="已过期" value="expired" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 内容卡片 -->
    <el-card v-loading="loading" class="content-card">
      <div class="table-toolbar">
        <span class="text-base font-semibold text-gray-700">我的采购需求</span>
        <el-button type="primary" :icon="Plus" @click="openPublishDialog">发布需求</el-button>
      </div>

      <el-table :data="list" stripe border>
        <el-table-column type="index" :index="(idx) => (pageNum - 1) * pageSize + idx + 1" label="ID" width="55" align="center" />
        <el-table-column prop="title" label="需求标题" min-width="350" show-overflow-tooltip />
        <el-table-column prop="category" label="采购品类" min-width="250" show-overflow-tooltip />
        <el-table-column prop="quantity" label="数量" min-width="100" show-overflow-tooltip />
        <el-table-column prop="unit" label="单位" min-width="80" show-overflow-tooltip />
        <el-table-column prop="budget" label="预算" min-width="200" show-overflow-tooltip>
          <template #default="{ row }"><EmptyText :value="row.budget" /></template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row)" size="small">{{ statusLabel(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="公开" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isPublic" type="success" effect="plain" size="small">公开</el-tag>
            <span v-else style="color:#909399">不公开</span>
          </template>
        </el-table-column>
        <el-table-column label="已对接" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.connectionCount > 0" type="success" effect="plain" size="small">{{ row.connectionCount }} 家</el-tag>
            <span v-else style="color:#909399">0</span>
          </template>
        </el-table-column>
        <el-table-column label="目标供应商" width="150" align="center">
          <template #default="{ row }">
            <span v-if="row.targetSupplierCount">{{ row.targetSupplierCount }}</span>
            <span v-else style="color:#c0c4cc">不限</span>
          </template>
        </el-table-column>
        <el-table-column prop="deadline" label="截止日期" min-width="120" />
        <el-table-column label="发布时间" min-width="180">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="160" fixed="right" align="center">
          <template #default="{ row }">
            <!-- 超过 3 个才进三点；2 个平铺 -->
            <template v-if="row.status === 'open'">
              <el-button type="success" link size="small" @click="goMatch(row)">智能匹配</el-button>
              <el-dropdown trigger="click" @command="(cmd) => handleReqCmd(cmd, row)">
                <el-icon :size="16" style="cursor:pointer;color:#409eff;margin-left:4px;margin-top:5px;transform:rotate(90deg)"><MoreFilled /></el-icon>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="detail">详情</el-dropdown-item>
                    <el-dropdown-item command="edit">编辑</el-dropdown-item>
                    <el-dropdown-item command="close" divided style="color:#f56c6c">关闭</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
            <template v-else-if="row.status === 'closed' || row.status === 'expired'">
              <el-button type="primary" link size="small" @click="openDetailDialog(row)">详情</el-button>
              <el-button type="primary" link size="small" @click="handleRepublish(row)">重新发布</el-button>
            </template>
            <template v-else>
              <el-button type="primary" link size="small" @click="openDetailDialog(row)">详情</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && list.length === 0" :description="searchForm.keyword ? '未找到匹配的需求' : '暂无采购需求'" />

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="PAGE_SIZE_OPTIONS"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="fetchList"
          @size-change="fetchList"
        />
      </div>
    </el-card>

    <!-- 发布需求弹窗 -->
    <el-dialog v-model="pubVisible" :title="republishingId ? '重新发布需求' : editingId ? '编辑采购需求' : '发布采购需求'" :close-on-click-modal="false" class="pub-dialog">
      <el-form ref="pubFormRef" :model="pubForm" :rules="pubRules" label-width="110px" label-position="right" class="pub-form">
        <el-form-item label="需求标题" prop="title" required>
          <el-input v-model="pubForm.title" placeholder="如：2025年度钢材采购需求" maxlength="200" />
        </el-form-item>
        <el-form-item label="采购品类" prop="category" required>
          <el-input v-model="pubForm.category" placeholder="多个品类用逗号分隔，如：钢材,铝材,铜材" maxlength="500" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="数量" prop="quantity" required>
              <el-input v-model="pubForm.quantity" placeholder="如：5000" maxlength="128" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单位" prop="unit" required>
              <el-input v-model="pubForm.unit" placeholder="如：吨、件、套" maxlength="32" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="目标供应商">
          <el-input-number v-model="pubForm.targetSupplierCount" :min="1" :max="99" placeholder="选填，用于撮合，达标后显示已完成" style="width:100%" />
        </el-form-item>
        <el-form-item label="需求广场">
          <el-switch v-model="pubForm.isPublic" active-text="公开" inactive-text="不公开" />
        </el-form-item>
        <el-form-item label="供应商地域">
          <el-input v-model="pubForm.region" placeholder="期望省份/城市，空=不限" maxlength="200" />
        </el-form-item>
        <el-form-item label="资质要求">
          <el-input v-model="pubForm.certRequire" placeholder="如：ISO9001,CE认证，空=不限" maxlength="500" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="截止日期">
              <el-date-picker v-model="pubForm.deadline" type="date" placeholder="选填" style="width:100%" value-format="YYYY-MM-DD" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="预算">
              <el-input v-model="pubForm.budget" placeholder="如：500万-800万，选填" maxlength="128" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="详细描述">
          <el-input v-model="pubForm.description" type="textarea" :rows="4" placeholder="选填，越详细 AI 匹配越准" maxlength="2000" show-word-limit />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="pubForm.contactName" placeholder="默认取自企业画像" maxlength="100" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="pubForm.contactPhone" placeholder="默认取自企业画像" maxlength="50" />
        </el-form-item>
        <el-form-item label="联系邮箱">
          <el-input v-model="pubForm.contactEmail" placeholder="默认取自企业画像" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pubVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handlePublish">{{ republishingId ? '重新发布' : editingId ? '保存修改' : '确认发布' }}</el-button>
      </template>
    </el-dialog>

    <!-- 需求详情弹窗 -->
    <el-dialog v-model="detailVisible" title="需求详情" :close-on-click-modal="false" class="detail-dialog">
      <template v-if="currentRow">
        <el-descriptions :column="1" border class="detail-descriptions">
          <el-descriptions-item label="需求标题">{{ currentRow.title }}</el-descriptions-item>
          <el-descriptions-item label="采购品类">{{ currentRow.category }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTag(currentRow)" size="small">{{ statusLabel(currentRow) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="数量"><EmptyText :value="currentRow.quantity" /></el-descriptions-item>
          <el-descriptions-item label="单位"><EmptyText :value="currentRow.unit" /></el-descriptions-item>
          <el-descriptions-item label="需求广场">{{ currentRow.isPublic ? '公开' : '不公开' }}</el-descriptions-item>
          <el-descriptions-item label="已对接">{{ currentRow.connectionCount || 0 }} 家</el-descriptions-item>
          <el-descriptions-item label="目标供应商">{{ currentRow.targetSupplierCount || '不限' }}</el-descriptions-item>
          <el-descriptions-item label="截止日期"><EmptyText :value="currentRow.deadline" /></el-descriptions-item>
          <el-descriptions-item label="供应商地域">{{ currentRow.region || '不限' }}</el-descriptions-item>
          <el-descriptions-item label="资质要求">{{ currentRow.certRequire || '不限' }}</el-descriptions-item>
          <el-descriptions-item label="预算"><EmptyText :value="currentRow.budget" /></el-descriptions-item>
          <el-descriptions-item label="联系人"><EmptyText :value="currentRow.contactName" /></el-descriptions-item>
          <el-descriptions-item label="联系电话"><EmptyText :value="currentRow.contactPhone" /></el-descriptions-item>
          <el-descriptions-item label="联系邮箱"><EmptyText :value="currentRow.contactEmail" /></el-descriptions-item>
          <el-descriptions-item label="发布时间">{{ formatTime(currentRow.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ formatTime(currentRow.updateTime) }}</el-descriptions-item>
          <el-descriptions-item label="详细描述"><EmptyText :value="currentRow.description" /></el-descriptions-item>
        </el-descriptions>
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, MoreFilled } from '@element-plus/icons-vue'
import { publishRequirement, updateRequirement, republishRequirement, listMyRequirements, closeRequirement } from '@/api/match'
import { PAGE_SIZE_OPTIONS } from '@/constants'
import { formatTime } from '@/utils/format'

const router = useRouter()
const STATUS_MAP = { completed: '已完成', closed: '已关闭', expired: '已过期' }
const STATUS_TAG = { completed: 'success', closed: 'info', expired: 'warning' }
/** 目标数是否达标 */
function isCompleted(row) {
  return row.targetSupplierCount && row.connectionCount >= row.targetSupplierCount
}

/** 状态标签：open + 目标数达标 → 已完成，否则 → 进行中 */
function statusLabel(row) {
  if (row.status !== 'open') return STATUS_MAP[row.status] || row.status
  return isCompleted(row) ? '已完成' : '进行中'
}
function statusTag(row) {
  if (row.status !== 'open') return STATUS_TAG[row.status] || 'info'
  return isCompleted(row) ? 'success' : 'primary'
}

function handleReqCmd(cmd, row) {
  if (cmd === 'detail') openDetailDialog(row)
  else if (cmd === 'edit') openEditDialog(row)
  else if (cmd === 'close') handleClose(row)
}
// ---- 列表 ----
const loading = ref(true)
const list = ref([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const searchForm = reactive({ keyword: '', status: '' })

async function fetchList() {
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize: pageSize.value }
    if (searchForm.status) params.status = searchForm.status
    if (searchForm.keyword) params.keyword = searchForm.keyword
    const { data } = await listMyRequirements(params)
    list.value = data?.records || []
    total.value = data?.total || 0
  } catch { list.value = [] } finally { loading.value = false }
}

function handleSearch() {
  pageNum.value = 1
  fetchList()
}

function handleReset() {
  searchForm.keyword = ''
  searchForm.status = ''
  handleSearch()
}

// ---- 发布/编辑/重新发布 ----
const pubVisible = ref(false)
const editingId = ref(null)
const republishingId = ref(null)
const submitting = ref(false)
const pubFormRef = ref(null)
const pubForm = reactive({
  title: '', category: '', quantity: '', unit: '',
  targetSupplierCount: null, isPublic: false,
  region: '', certRequire: '', deadline: '', budget: '',
  description: '', contactName: '', contactPhone: '', contactEmail: '',
})
const pubRules = {
  title: [{ required: true, message: '请输入需求标题', trigger: 'blur' }],
  category: [{ required: true, message: '请输入采购品类', trigger: 'blur' }],
  quantity: [{ required: true, message: '请输入数量', trigger: 'blur' }],
  unit: [{ required: true, message: '请输入单位', trigger: 'blur' }],
}

function resetPubForm() {
  Object.keys(pubForm).forEach(k => pubForm[k] = '')
  pubForm.targetSupplierCount = null
  pubForm.isPublic = false
  pubFormRef.value?.resetFields()
}

function openPublishDialog() {
  editingId.value = null
  republishingId.value = null
  resetPubForm()
  pubVisible.value = true
}

function openEditDialog(row) {
  editingId.value = row.id
  republishingId.value = null
  Object.keys(pubForm).forEach(k => {
    pubForm[k] = (k === 'targetSupplierCount') ? (row[k] || null) : (k === 'isPublic') ? !!row[k] : (row[k] || '')
  })
  // deadline 是日期格式，兼容处理
  if (row.deadline) pubForm.deadline = row.deadline
  pubVisible.value = true
}

async function handlePublish() {
  const valid = await pubFormRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (republishingId.value) {
      await republishRequirement(republishingId.value, { ...pubForm })
      ElMessage.success('已重新发布')
    } else if (editingId.value) {
      await updateRequirement(editingId.value, { ...pubForm })
      ElMessage.success('编辑成功')
    } else {
      await publishRequirement({ ...pubForm })
      ElMessage.success('需求发布成功')
    }
    pubVisible.value = false
    fetchList()
  } catch { } finally { submitting.value = false }
}

// ---- 详情 ----
const detailVisible = ref(false)
const currentRow = ref(null)

function openDetailDialog(row) {
  currentRow.value = row
  detailVisible.value = true
}

// ---- 智能匹配 ----
function goMatch(row) {
  router.push({ name: 'MatchResult', params: { id: row.id } })
}

// ---- 重新发布 ----
function handleRepublish(row) {
  editingId.value = null
  republishingId.value = row.id
  // 回填原需求数据
  Object.keys(pubForm).forEach(k => {
    pubForm[k] = (k === 'targetSupplierCount') ? (row[k] || null) : (k === 'isPublic') ? !!row[k] : (row[k] || '')
  })
  if (row.deadline) pubForm.deadline = row.deadline
  pubVisible.value = true
}

// ---- 关闭 ----
async function handleClose(row) {
  try {
    await ElMessageBox.confirm(
      `<div style="white-space:nowrap;margin-bottom:4px">确认关闭需求 <span class="highlight-name">${row.title}</span> 吗？</div><div>关闭后将不再出现在需求广场，可重新发布。</div>`,
      '关闭需求',
      {
        confirmButtonText: '确认关闭',
        cancelButtonText: '取消',
        type: 'warning',
        dangerouslyUseHTMLString: true,
        customClass: 'confirmDdMsgBox',
      }
    )
  } catch { return }
  try {
    await closeRequirement(row.id)
    ElMessage.success('需求已关闭')
    fetchList()
  } catch { }
}

onMounted(() => fetchList())
</script>

<style scoped>
.pub-form :deep(.el-form-item__label) { white-space: nowrap; }
.pub-form :deep(.el-form-item.is-required .el-form-item__label::before) { margin-right: 2px; }
</style>

<style>
.pub-dialog { width: auto !important; max-width: 640px; }
.detail-dialog { width: auto !important; max-width: 640px; }
</style>
