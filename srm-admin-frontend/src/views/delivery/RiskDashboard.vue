<template>
  <div>
    <!-- 搜索卡片 -->
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="风险状态">
          <el-select v-model="searchForm.riskStatus" clearable placeholder="全部" style="width:160px"
            @change="handleSearch">
            <el-option v-for="opt in RISK_STATUS_ACTIVE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="风险等级">
          <el-select v-model="searchForm.riskLevel" clearable placeholder="全部" style="width:140px"
            @change="handleSearch">
            <el-option v-for="opt in RISK_LEVEL_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="isDual" label="视角">
          <el-select v-model="searchForm.role" style="width:120px" @change="fetchList">
            <el-option label="采购方" value="buyer" />
            <el-option label="供应方" value="supplier" />
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
        <span class="text-base font-semibold text-gray-700">供应保障驾驶舱</span>
      </div>

      <el-table :data="list" stripe border>
        <IdColumn :page-num="pageNum" :page-size="pageSize" />
        <el-table-column prop="deliveryNo" label="交付单号" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <EmptyText :value="row.deliveryNo" />
          </template>
        </el-table-column>
        <el-table-column prop="contractTitle" label="合同标题" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <EmptyText :value="row.contractTitle" />
          </template>
        </el-table-column>
        <el-table-column prop="buyerCompanyName" label="采购企业" min-width="250" show-overflow-tooltip>
          <template #default="{ row }">
            <EmptyText :value="row.buyerCompanyName" />
          </template>
        </el-table-column>
        <el-table-column prop="supplierCompanyName" label="供应企业" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <EmptyText :value="row.supplierCompanyName" />
          </template>
        </el-table-column>
        <el-table-column label="风险类型" min-width="180" align="center">
          <template #default="{ row }">
            <template v-if="row.riskTypes && row.riskTypes.length">
              <el-tag v-for="t in row.riskTypes" :key="t" type="danger" size="small" effect="plain" style="margin:0 2px">{{ RISK_TYPE_MAP[t] || t }}</el-tag>
            </template>
            <span v-else style="color:#909399">-</span>
          </template>
        </el-table-column>
        <el-table-column label="风险等级" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="RISK_LEVEL_TAG[row.riskLevel] || 'info'" size="small">{{ RISK_LEVEL_MAP[row.riskLevel] ||
              row.riskLevel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="风险状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="RISK_STATUS_TAG[row.riskStatus] || 'info'" size="small">{{ RISK_STATUS_MAP[row.riskStatus] ||
              row.riskStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="预计到货" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <EmptyText :value="row.estimatedArrivalTime">{{ formatDateTime(row.estimatedArrivalTime) }}</EmptyText>
          </template>
        </el-table-column>
        <el-table-column label="合同交期" min-width="120">
          <template #default="{ row }">
            <EmptyText :value="row.promisedDeliveryDate" />
          </template>
        </el-table-column>
        <el-table-column label="风险分" width="90" align="center">
          <template #default="{ row }">
            <span :style="{ color: riskScoreColor(row.riskScore), fontWeight: 600 }">
              <EmptyText :value="row.riskScore" />
            </span>
          </template>
        </el-table-column>
        <el-table-column label="风险数" width="80" align="center">
          <template #default="{ row }">
            <span v-if="row.riskCount && row.riskCount > 1" style="color:#f56c6c;font-weight:600">{{ row.riskCount }}</span>
            <span v-else style="color:#909399">{{ row.riskCount || 1 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleDetail(row)">详情</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty v-if="!loading" description="暂无交付风险" />
        </template>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize" :page-sizes="PAGE_SIZE_OPTIONS"
          :total="total" layout="total, sizes, prev, pager, next, jumper" @current-change="fetchList"
          @size-change="fetchList" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Search, Refresh } from '@element-plus/icons-vue'
import { listRisksDashboard } from '@/api/risk'
import { RISK_STATUS_MAP, RISK_STATUS_TAG, RISK_STATUS_ACTIVE_OPTIONS, RISK_LEVEL_MAP, RISK_LEVEL_TAG, RISK_LEVEL_OPTIONS, RISK_TYPE_MAP } from '@/constants/risk'
import { PAGE_SIZE_OPTIONS } from '@/constants'
import { formatDateTime } from '@/utils/datetime'
import { useAuthStore } from '@/stores/auth'
import IdColumn from '@/components/IdColumn.vue'

const router = useRouter()
const authStore = useAuthStore()
const isDual = computed(() => authStore.profileType === 'both')

const list = ref([])
const loading = ref(true)
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const searchForm = reactive({ riskStatus: '', riskLevel: '', role: authStore.profileType === 'supplier' ? 'supplier' : 'buyer' })

onMounted(() => { fetchList() })

async function fetchList() {
  loading.value = true
  try {
    const res = await listRisksDashboard({ pageNum: pageNum.value, pageSize: pageSize.value, ...searchForm })
    list.value = res?.data?.records || res?.records || []
    total.value = res?.data?.total || res?.total || 0
  } finally { loading.value = false }
}

function handleSearch() { pageNum.value = 1; fetchList() }
function handleReset() {
  searchForm.riskStatus = ''
  searchForm.riskLevel = ''
  pageNum.value = 1
  fetchList()
}

function handleDetail(row) {
  router.push({ name: 'DeliveryRiskDetail', params: { id: row.id } })
}

function riskScoreColor(score) {
  if (score == null) return '#909399'
  if (score >= 80) return '#f56c6c'
  if (score >= 60) return '#e6a23c'
  return '#409eff'
}
</script>
