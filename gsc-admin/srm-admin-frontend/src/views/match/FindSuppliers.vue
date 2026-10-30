<template>
  <div>
    <el-card class="search-card">
      <el-form :inline="true" :model="supplierSearch">
        <el-form-item label="关键词">
          <el-input v-model="supplierSearch.keyword" placeholder="企业名/行业/地区/品类" clearable style="width:260px" @keyup.enter="searchSuppliers" />
        </el-form-item>
        <el-form-item label="匹配需求">
          <el-select v-model="recommendRequirementId" placeholder="选择进行中的采购需求" clearable filterable style="width:240px" :loading="reqOptionsLoading">
            <el-option v-for="req in myOpenRequirements" :key="req.id" :label="req.title" :value="req.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="searchSuppliers">搜索</el-button>
          <el-button type="success" :icon="MagicStick" @click="handleRecommend">智能推荐</el-button>
          <el-button :icon="Refresh" @click="resetSuppliers">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-loading="supplierLoading" class="content-card" :element-loading-text="recommendMode ? '智能匹配中，请稍候...' : '加载中...'">
      <div class="table-toolbar">
        <span class="text-base font-semibold text-gray-700">
          {{ recommendMode ? '智能推荐结果' : '供应商列表' }}
          <span v-if="recommendMode && currentRecommendTitle" class="recommend-req-title"> · {{ currentRecommendTitle }}</span>
        </span>
        <el-button v-if="recommendMode" type="primary" link @click="exitRecommend">返回全部</el-button>
      </div>

      <el-table :data="supplierList" stripe border>
        <el-table-column type="index" :index="(idx) => (supplierPageNum - 1) * supplierPageSize + idx + 1" label="ID" width="55" align="center" />
        <el-table-column prop="companyName" label="企业名称" min-width="280" show-overflow-tooltip />
        <el-table-column v-if="recommendMode" label="匹配度" width="90" align="center">
          <template #default="{ row }">
            <span style="font-weight:700" :style="{ color: scoreColor(row.matchScore) }">{{ row.matchScore ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="生产品类" min-width="300" show-overflow-tooltip>
          <template #default="{ row }"><EmptyText :value="row.supCategories" /></template>
        </el-table-column>
        <el-table-column prop="industry" label="所属行业" min-width="200" show-overflow-tooltip>
          <template #default="{ row }"><EmptyText :value="row.industry" /></template>
        </el-table-column>
        <el-table-column label="所在地区" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.province || '' }}{{ row.city ? ' · ' + row.city : '' }}</template>
        </el-table-column>
        <el-table-column label="年产能" min-width="200" show-overflow-tooltip>
          <template #default="{ row }"><EmptyText :value="row.supCapacity" /></template>
        </el-table-column>
        <el-table-column label="交货周期" width="100" align="center">
          <template #default="{ row }">{{ leadTimeLabel(row.leadTime) }}</template>
        </el-table-column>
        <el-table-column label="出口经验" min-width="120" align="center">
          <template #default="{ row }">{{ supExportLabel(row.supExport) }}</template>
        </el-table-column>
        <el-table-column label="资质认证" min-width="160" show-overflow-tooltip>
          <template #default="{ row }"><EmptyText :value="row.supCerts" /></template>
        </el-table-column>
        <el-table-column label="主要客户" min-width="200" show-overflow-tooltip>
          <template #default="{ row }"><EmptyText :value="row.keyClients" /></template>
        </el-table-column>
        <el-table-column label="对接状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.myConnectionStatus === 'accepted'" type="success" size="small">已接受</el-tag>
            <el-tag v-else-if="row.myConnectionStatus === 'pending'" type="warning" size="small">待处理</el-tag>
            <el-tag v-else-if="row.myConnectionStatus === 'rejected'" type="danger" size="small">已拒绝</el-tag>
            <span v-else style="color:#909399">未对接</span>
          </template>
        </el-table-column>
        <el-table-column label="已对接" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.myConnectionCount > 0" type="success" effect="plain" size="small">{{ row.myConnectionCount }}</el-tag>
            <span v-else style="color:#909399">0</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="180" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="showProfile(row)">查看画像</el-button>
            <el-button type="primary" link size="small" style="margin-left:4px" @click="openConnect(row)">发起对接</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!supplierLoading && supplierList.length === 0" :description="emptyDescription" />

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="supplierPageNum"
          v-model:page-size="supplierPageSize"
          :page-sizes="PAGE_SIZE_OPTIONS"
          :total="supplierTotal"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="fetchCurrent"
          @size-change="fetchCurrent"
        />
      </div>
    </el-card>

    <!-- 查看画像弹窗 -->
    <ProfileViewer v-model="profileVisible" :profile-data="profileData" view-mode="supplier" />

    <!-- 发起对接弹窗 -->
    <el-dialog v-model="connectVisible" title="发起对接意向" :close-on-click-modal="false" class="connect-dialog">
      <template v-if="connectTarget">
        <div style="margin-bottom:16px;color:#606266;font-size:16px;line-height:2;text-align:center">
          向 <span class="highlight-name">{{ connectTarget.companyName }}</span> 发起对接意向
        </div>
        <el-form label-width="110px" label-position="right" class="connect-form">
          <el-form-item label="采购需求" required>
            <el-select v-model="selectedRequirementId" placeholder="请选择采购需求（仅进行中）" style="width:100%" :loading="reqLoading">
              <el-option v-if="myRequirements.length === 0 && !reqLoading" value="" disabled label="暂无采购需求，请先发布需求" />
              <el-option v-for="req in myRequirements" :key="req.id" :label="req.title" :value="req.id" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-input v-model="connectMessage" type="textarea" :rows="3" placeholder="留言（可选），如：贵司产品与我方需求高度匹配，期待进一步沟通" maxlength="500" show-word-limit />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="connectVisible = false">取消</el-button>
        <el-button type="primary" :loading="connecting" :disabled="!selectedRequirementId" @click="doConnect">确认发起</el-button>
      </template>
    </el-dialog>

    <!-- 尽调报告预览 -->
    <el-dialog v-model="previewVisible" title="尽调报告预览" :close-on-click-modal="false" class="preview-dialog" top="5vh">
      <iframe v-if="previewUrl" :src="previewUrl" style="width:100%;height:80vh;border:none" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, MagicStick } from '@element-plus/icons-vue'
import { listSuppliers, recommendPlazaSuppliers, getPublicProfile, listMyRequirements, listConnections, createConnection } from '@/api/match'
import { PAGE_SIZE_OPTIONS, supExportLabel, leadTimeLabel } from '@/constants'
import ProfileViewer from '@/components/ProfileViewer.vue'

const supplierSearch = reactive({ keyword: '' })
const supplierList = ref([])
const supplierLoading = ref(true)
const supplierPageNum = ref(1)
const supplierPageSize = ref(10)
const supplierTotal = ref(0)
const recommendMode = ref(false)
const recommendRequirementId = ref(null)
const myOpenRequirements = ref([])
const reqOptionsLoading = ref(false)

const currentRecommendTitle = computed(() => {
  const req = myOpenRequirements.value.find(r => String(r.id) === String(recommendRequirementId.value))
  return req?.title || ''
})

const emptyDescription = computed(() => {
  if (recommendMode.value) {
    return supplierSearch.keyword ? '未找到匹配的供应商' : '暂无匹配的供应商，建议放宽需求条件后重试'
  }
  return supplierSearch.keyword ? '未找到匹配的供应商' : '暂无供应商'
})

async function loadOpenRequirements() {
  reqOptionsLoading.value = true
  try {
    const { data } = await listMyRequirements({ pageNum: 1, pageSize: 100, status: 'open' })
    myOpenRequirements.value = data?.records || []
    if (myOpenRequirements.value.length === 1 && !recommendRequirementId.value) {
      recommendRequirementId.value = myOpenRequirements.value[0].id
    }
  } catch { myOpenRequirements.value = [] } finally { reqOptionsLoading.value = false }
}

async function fetchSuppliers() {
  supplierLoading.value = true
  try {
    const params = { pageNum: supplierPageNum.value, pageSize: supplierPageSize.value }
    if (supplierSearch.keyword) params.keyword = supplierSearch.keyword
    const { data } = await listSuppliers(params)
    supplierList.value = data?.records || []
    supplierTotal.value = data?.total || 0
  } catch { supplierList.value = [] } finally { supplierLoading.value = false }
}

async function fetchRecommend() {
  supplierLoading.value = true
  try {
    const params = { pageNum: supplierPageNum.value, pageSize: supplierPageSize.value }
    if (supplierSearch.keyword) params.keyword = supplierSearch.keyword
    const { data } = await recommendPlazaSuppliers(recommendRequirementId.value, params)
    supplierList.value = data?.records || []
    supplierTotal.value = data?.total || 0
  } catch { supplierList.value = [] } finally { supplierLoading.value = false }
}

function fetchCurrent() {
  if (recommendMode.value) fetchRecommend()
  else fetchSuppliers()
}

function searchSuppliers() {
  supplierPageNum.value = 1
  fetchCurrent()
}

function handleRecommend() {
  if (!recommendRequirementId.value) {
    ElMessage.warning(myOpenRequirements.value.length === 0 ? '请先发布进行中的采购需求' : '请先选择一条采购需求')
    return
  }
  recommendMode.value = true
  supplierPageNum.value = 1
  fetchRecommend()
}

function exitRecommend() {
  recommendMode.value = false
  supplierPageNum.value = 1
  fetchSuppliers()
}

function resetSuppliers() {
  supplierSearch.keyword = ''
  recommendMode.value = false
  supplierPageNum.value = 1
  fetchSuppliers()
}

function scoreColor(score) {
  if (score == null) return '#909399'
  if (score >= 80) return '#67c23a'
  if (score >= 60) return '#e6a23c'
  return '#f56c6c'
}

// 查看画像
const profileVisible = ref(false)
const profileData = ref(null)

async function showProfile(row) {
  profileData.value = null
  profileVisible.value = true
  try {
    const { data } = await getPublicProfile(row.tenantId)
    profileData.value = data
  } catch { profileData.value = null }
}

// 发起联系
const connectVisible = ref(false)
const connectTarget = ref(null)
const selectedRequirementId = ref(null)
const connectMessage = ref('')
const connecting = ref(false)
const myRequirements = ref([])
const reqLoading = ref(false)

async function openConnect(row) {
  connectTarget.value = row
  selectedRequirementId.value = null
  connectMessage.value = ''
  connectVisible.value = true
  // 加载采购需求列表（每次都重新加载，确保过滤最新状态）
  reqLoading.value = true
  try {
    const [reqRes, connRes] = await Promise.all([
      listMyRequirements({ pageNum: 1, pageSize: 100, status: 'open' }),
      listConnections({ pageSize: 999 }),
    ])
    // 该供应商已对接的需求ID集合（只过滤活跃对接 pending/accepted；被拒绝 rejected、已撤销 closed 的需求可重新发起；String() 兼容 Long→String）
    const records = connRes?.data?.records || []
    const connectedReqIds = new Set(
      records.filter(c => String(c.supplierTenantId) === String(row.tenantId) && (c.status === 'pending' || c.status === 'accepted')).map(c => c.requirementId)
    )
    // 过滤：已完成 + 已与该供应商对接过的需求
    myRequirements.value = (reqRes?.data?.records || [])
      .filter(r => (!r.targetSupplierCount || r.connectionCount < r.targetSupplierCount))
      .filter(r => !connectedReqIds.has(r.id))
    if (recommendMode.value && myRequirements.value.some(r => String(r.id) === String(recommendRequirementId.value))) {
      selectedRequirementId.value = recommendRequirementId.value
    }
  } catch { myRequirements.value = [] } finally { reqLoading.value = false }
}

async function doConnect() {
  if (!selectedRequirementId.value) return
  connecting.value = true
  try {
    await createConnection({
      requirementId: selectedRequirementId.value,
      supplierTenantId: connectTarget.value.tenantId,
      message: connectMessage.value,
    })
    ElMessage.success('对接意向已发起')
    connectVisible.value = false
    fetchCurrent()
  } catch { } finally { connecting.value = false }
}

onMounted(() => {
  loadOpenRequirements()
  fetchSuppliers()
})
</script>

<style>
.connect-dialog { width: auto !important; max-width: 520px; }
.recommend-req-title { color: #909399; font-weight: 400; }
</style>

<style scoped>
.connect-form :deep(.el-form-item__label) { white-space: nowrap; }
.connect-form :deep(.el-form-item.is-required .el-form-item__label::before) { margin-right: 2px; }
</style>
