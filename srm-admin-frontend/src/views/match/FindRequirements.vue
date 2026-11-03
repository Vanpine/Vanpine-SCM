<template>
  <div>
    <el-card class="search-card">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" placeholder="需求标题/品类/地域/资质" clearable style="width:280px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="我的状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width:130px" @change="handleSearch">
            <el-option label="可申请" value="available" />
            <el-option label="待处理" value="pending" />
            <el-option label="已接受" value="accepted" />
            <el-option label="已拒绝" value="rejected" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button type="success" :icon="MagicStick" @click="handleRecommend">智能推荐</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-loading="loading" class="content-card" :element-loading-text="recommendMode ? '智能匹配中，请稍候...' : '加载中...'">
      <div class="table-toolbar">
        <span class="text-base font-semibold text-gray-700">{{ recommendMode ? '智能推荐结果' : '公开需求列表' }}</span>
        <el-button v-if="recommendMode" type="primary" link @click="exitRecommend">返回全部</el-button>
      </div>

      <el-table :data="list" stripe border>
        <el-table-column type="index" :index="indexMethod" label="ID" width="55" align="center" />
        <el-table-column prop="buyerCompanyName" label="采购企业" min-width="280" show-overflow-tooltip />
        <el-table-column prop="title" label="需求标题" min-width="350" show-overflow-tooltip />
        <el-table-column prop="category" label="采购品类" min-width="250" show-overflow-tooltip />
        <el-table-column prop="quantity" label="数量" min-width="100" show-overflow-tooltip />
        <el-table-column prop="unit" label="单位" min-width="80" show-overflow-tooltip />
        <el-table-column prop="budget" label="预算" min-width="200" show-overflow-tooltip>
          <template #default="{ row }"><EmptyText :value="row.budget" /></template>
        </el-table-column>
        <el-table-column label="供应商地域" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.region || '不限' }}</template>
        </el-table-column>
        <el-table-column label="资质要求" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.certRequire || '不限' }}</template>
        </el-table-column>
        <el-table-column prop="deadline" label="截止日期" min-width="120" />
        <el-table-column label="我的状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.myConnectionStatus === 'accepted'" type="success" size="small">已接受</el-tag>
            <el-tag v-else-if="row.myConnectionStatus === 'pending'" type="warning" size="small">待处理</el-tag>
            <el-tag v-else-if="row.myConnectionStatus === 'rejected'" type="danger" size="small">已拒绝</el-tag>
            <el-tag v-else-if="isCompleted(row)" type="info" size="small">已完成</el-tag>
            <el-tag v-else type="primary" size="small">可申请</el-tag>
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
        <el-table-column label="发布时间" min-width="180">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="160" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openDetailDialog(row)">详情</el-button>
            <el-button v-if="!row.myConnectionStatus && !isCompleted(row)" type="primary" link size="small" style="margin-left:4px" @click="openApplyDialog(row)">申请</el-button>
            <el-button v-if="row.myConnectionStatus === 'accepted'" type="success" link size="small" style="margin-left:4px" @click="openContactDialog(row)">查看联系</el-button>
            <el-button v-if="row.myConnectionStatus === 'rejected'" type="primary" link size="small" style="margin-left:4px" @click="openReapplyDialog(row)">重新申请</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && list.length === 0" :description="emptyDescription" />

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="PAGE_SIZE_OPTIONS"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="fetchCurrent"
          @size-change="fetchCurrent"
        />
      </div>
    </el-card>

    <!-- 需求详情弹窗 -->
    <el-dialog v-model="detailVisible" title="需求详情" :close-on-click-modal="false" class="detail-dialog">
      <template v-if="detailRow">
        <el-descriptions :column="1" border class="detail-descriptions">
          <el-descriptions-item label="需求标题">{{ detailRow.title }}</el-descriptions-item>
          <el-descriptions-item label="采购企业"><EmptyText :value="detailRow.buyerCompanyName" /></el-descriptions-item>
          <el-descriptions-item label="采购品类">{{ detailRow.category }}</el-descriptions-item>
          <el-descriptions-item label="数量"><EmptyText :value="detailRow.quantity" /></el-descriptions-item>
          <el-descriptions-item label="单位"><EmptyText :value="detailRow.unit" /></el-descriptions-item>
          <el-descriptions-item label="预算"><EmptyText :value="detailRow.budget" /></el-descriptions-item>
          <el-descriptions-item label="供应商地域">{{ detailRow.region || '不限' }}</el-descriptions-item>
          <el-descriptions-item label="资质要求">{{ detailRow.certRequire || '不限' }}</el-descriptions-item>
          <el-descriptions-item label="截止日期"><EmptyText :value="detailRow.deadline" /></el-descriptions-item>
          <el-descriptions-item label="我的状态">{{ myStatusLabel(detailRow) }}</el-descriptions-item>
          <el-descriptions-item label="已对接">{{ detailRow.connectionCount || 0 }} 家</el-descriptions-item>
          <el-descriptions-item label="目标供应商">{{ detailRow.targetSupplierCount || '不限' }}</el-descriptions-item>
          <el-descriptions-item label="发布时间">{{ formatTime(detailRow.createTime) }}</el-descriptions-item>
          <el-descriptions-item v-if="detailRow.description" label="详细描述">{{ detailRow.description }}</el-descriptions-item>
        </el-descriptions>
      </template>
      <template #footer>
        <el-button type="primary" @click="viewBuyerProfile">查看采购商画像</el-button>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 申请弹窗（首次申请 + 重新申请共用） -->
    <el-dialog v-model="applyVisible" :title="applyTitle" :close-on-click-modal="false" class="apply-dialog">
      <template v-if="applyTarget">
        <!-- 重新申请时展示拒绝原因 -->
        <div v-if="applyTarget.myRejectReason" style="margin-bottom:14px;padding:12px;background:#fef0f0;border-radius:6px;font-size:13px;color:#f56c6c">
          <div style="font-weight:600;margin-bottom:4px">拒绝原因：</div>
          <div>{{ applyTarget.myRejectReason }}</div>
        </div>
        <div style="margin-bottom:12px;color:#606266;font-size:16px;line-height:2;text-align:center">
          向 <span class="highlight-name">{{ applyTarget.buyerCompanyName || '未知企业' }}</span> 发起对接申请
        </div>
        <el-input v-model="applyMessage" type="textarea" :rows="3" placeholder="留言（可选），如：我司产品与贵方需求高度匹配，期望合作" maxlength="500" show-word-limit />
      </template>
      <template #footer>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="primary" :loading="applying" @click="doApply">确认申请</el-button>
      </template>
    </el-dialog>

    <!-- 联系方式弹窗 -->
    <el-dialog v-model="contactVisible" title="采购商联系方式" :close-on-click-modal="false" class="contact-dialog">
      <template v-if="contactData">
        <el-descriptions :column="1" border class="detail-descriptions">
          <el-descriptions-item label="企业名称"><EmptyText :value="contactData.buyerCompanyName" /></el-descriptions-item>
          <el-descriptions-item label="联系人"><EmptyText :value="contactData.buyerContactName" /></el-descriptions-item>
          <el-descriptions-item label="联系电话"><EmptyText :value="contactData.buyerContactPhone" /></el-descriptions-item>
          <el-descriptions-item label="联系邮箱"><EmptyText :value="contactData.buyerContactEmail" /></el-descriptions-item>
        </el-descriptions>
      </template>
      <template #footer>
        <el-button @click="contactVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 查看画像弹窗 -->
    <ProfileViewer v-model="profileVisible" :profile-data="profileData" view-mode="buyer" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, MagicStick } from '@element-plus/icons-vue'
import { listPlaza, recommendPlazaRequirements, applyRequirement, getPublicProfile, getMyConnection } from '@/api/match'
import { PAGE_SIZE_OPTIONS } from '@/constants'
import { formatTime } from '@/utils/format'
import ProfileViewer from '@/components/ProfileViewer.vue'

// ---- 列表 ----
const loading = ref(true)
const list = ref([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const searchForm = reactive({ keyword: '', status: '' })
const recommendMode = ref(false)

const emptyDescription = computed(() => {
  if (recommendMode.value) {
    return searchForm.keyword ? '未找到匹配的需求' : '暂无匹配的公开需求，建议完善企业画像后重试'
  }
  return searchForm.keyword ? '未找到匹配的需求' : '暂无公开需求'
})

function indexMethod(index) {
  return (pageNum.value - 1) * pageSize.value + index + 1
}

function isCompleted(row) {
  return row.targetSupplierCount && row.connectionCount >= row.targetSupplierCount
}

function myStatusLabel(row) {
  if (row.myConnectionStatus === 'accepted') return '已接受'
  if (row.myConnectionStatus === 'pending') return '待处理'
  if (row.myConnectionStatus === 'rejected') return '已拒绝'
  if (isCompleted(row)) return '已完成'
  return '可申请'
}

async function fetchList() {
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize: pageSize.value }
    if (searchForm.keyword) params.keyword = searchForm.keyword
    const { data } = await listPlaza(params)
    applyRecords(data)
  } catch { list.value = [] } finally { loading.value = false }
}

async function fetchRecommend() {
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize: pageSize.value }
    if (searchForm.keyword) params.keyword = searchForm.keyword
    const { data } = await recommendPlazaRequirements(params)
    applyRecords(data)
  } catch { list.value = [] } finally { loading.value = false }
}

function applyRecords(data) {
  let records = data?.records || []
  total.value = data?.total || 0
  if (searchForm.status) {
    records = records.filter(item => {
      if (searchForm.status === 'available') return !item.myConnectionStatus
      return item.myConnectionStatus === searchForm.status
    })
  }
  list.value = records
}

function fetchCurrent() {
  if (recommendMode.value) fetchRecommend()
  else fetchList()
}

function handleSearch() { pageNum.value = 1; fetchCurrent() }

function handleRecommend() {
  recommendMode.value = true
  pageNum.value = 1
  fetchRecommend()
}

function exitRecommend() {
  recommendMode.value = false
  pageNum.value = 1
  fetchList()
}

function handleReset() {
  searchForm.keyword = ''
  searchForm.status = ''
  recommendMode.value = false
  handleSearch()
}

// ---- 详情弹窗 ----
const detailVisible = ref(false)
const detailRow = ref(null)
function openDetailDialog(row) {
  detailRow.value = row
  detailVisible.value = true
}

// ---- 查看画像 ----
const profileVisible = ref(false)
const profileData = ref(null)

async function showProfile(tenantId) {
  profileData.value = null
  profileVisible.value = true
  try {
    const { data } = await getPublicProfile(tenantId)
    profileData.value = data
  } catch { profileData.value = null }
}

function viewBuyerProfile() {
  if (detailRow.value) showProfile(detailRow.value.tenantId)
}

// ---- 申请 / 重新申请 ----
const applyVisible = ref(false)
const applyTarget = ref(null)
const applyMessage = ref('')
const applying = ref(false)
const reapplyMode = ref(false)

const applyTitle = computed(() => {
  if (!applyTarget.value) return '申请对接'
  return reapplyMode.value
    ? `重新申请 · ${applyTarget.value.buyerCompanyName || ''}`
    : `申请对接 · ${applyTarget.value.buyerCompanyName || ''}`
})

function openApplyDialog(row) {
  applyTarget.value = row
  applyMessage.value = ''
  reapplyMode.value = false
  applyVisible.value = true
}

function openReapplyDialog(row) {
  applyTarget.value = row
  applyMessage.value = ''
  reapplyMode.value = true
  applyVisible.value = true
}

async function doApply() {
  applying.value = true
  try {
    await applyRequirement(applyTarget.value.id, applyMessage.value)
    ElMessage.success(reapplyMode.value ? '已重新提交申请' : '申请已发送')
    applyTarget.value.myConnectionStatus = 'pending'
    applyTarget.value.myRejectReason = null
    applyVisible.value = false
  } catch { } finally { applying.value = false }
}

// ---- 查看联系方式 ----
const contactVisible = ref(false)
const contactData = ref(null)

async function openContactDialog(row) {
  contactData.value = null
  contactVisible.value = true
  try {
    const { data } = await getMyConnection(row.id)
    contactData.value = data
  } catch { contactData.value = null }
}

onMounted(() => fetchList())
</script>

<style>

.detail-dialog { width: auto !important; max-width: 560px; }
.apply-dialog { width: auto !important; max-width: 520px; }
.contact-dialog { width: auto !important; max-width: 460px; }
</style>
