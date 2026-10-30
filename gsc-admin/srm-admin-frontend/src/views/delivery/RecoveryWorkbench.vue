<template>
  <div>
    <!-- 搜索卡片 -->
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="风险状态">
          <el-select v-model="searchForm.riskStatus" clearable placeholder="全部" style="width:160px" @change="handleSearch">
            <el-option v-for="opt in RISK_STATUS_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="风险等级">
          <el-select v-model="searchForm.riskLevel" clearable placeholder="全部" style="width:140px" @change="handleSearch">
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
        <span class="text-base font-semibold text-gray-700">补救工作台</span>
      </div>

      <el-table :data="list" stripe border>
        <IdColumn :page-num="pageNum" :page-size="pageSize" />
        <el-table-column prop="deliveryNo" label="交付单号" min-width="200" show-overflow-tooltip>
          <template #default="{ row }"><EmptyText :value="row.deliveryNo" /></template>
        </el-table-column>
        <el-table-column prop="contractTitle" label="合同标题" min-width="220" show-overflow-tooltip>
          <template #default="{ row }"><EmptyText :value="row.contractTitle" /></template>
        </el-table-column>
        <el-table-column label="风险类型" min-width="130" align="center">
          <template #default="{ row }">{{ RISK_TYPE_MAP[row.riskType] || row.riskType }}</template>
        </el-table-column>
        <el-table-column label="风险等级" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="RISK_LEVEL_TAG[row.riskLevel] || 'info'" size="small">{{ RISK_LEVEL_MAP[row.riskLevel] || row.riskLevel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="风险状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="RISK_STATUS_TAG[row.riskStatus] || 'info'" size="small">{{ RISK_STATUS_MAP[row.riskStatus] || row.riskStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处置截止" min-width="160">
          <template #default="{ row }"><EmptyText :value="row.dueTime">{{ formatDateTime(row.dueTime) }}</EmptyText></template>
        </el-table-column>
        <el-table-column label="影响数量" width="100" align="center">
          <template #default="{ row }"><EmptyText :value="row.impactQty" /></template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleDispose(row)">去处理</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty v-if="!loading" description="暂无待处置风险" />
        </template>
      </el-table>

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
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Search, Refresh } from '@element-plus/icons-vue'
import { listRisks } from '@/api/risk'
import { RISK_STATUS_MAP, RISK_STATUS_TAG, RISK_STATUS_OPTIONS, RISK_LEVEL_MAP, RISK_LEVEL_TAG, RISK_LEVEL_OPTIONS, RISK_TYPE_MAP } from '@/constants/risk'
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
    const res = await listRisks({ pageNum: pageNum.value, pageSize: pageSize.value, ...searchForm })
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

function handleDispose(row) {
  router.push({ name: 'DeliveryRiskDetail', params: { id: row.id } })
}
</script>
