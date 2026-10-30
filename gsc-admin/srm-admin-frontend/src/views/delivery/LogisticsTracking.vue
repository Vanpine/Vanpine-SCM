<template>
  <div>
    <!-- 搜索卡片 -->
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="关键词">
          <el-input
            v-model="searchForm.keyword"
            placeholder="交付单号/运单号"
            style="width:240px"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="交付状态">
          <el-select
            v-model="searchForm.status"
            clearable
            placeholder="全部"
            style="width:160px"
            @change="handleSearch"
          >
            <el-option v-for="opt in TRACKING_STATUS_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
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
        <span class="text-base font-semibold text-gray-700">物流追踪</span>
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
        <el-table-column prop="shipCompany" label="物流公司" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <EmptyText :value="row.shipCompany" />
          </template>
        </el-table-column>
        <el-table-column prop="shipTrackingNo" label="运单号" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <EmptyText :value="row.shipTrackingNo" />
          </template>
        </el-table-column>
        <!-- 最新物流节点：异常类标红 -->
        <el-table-column label="最新物流节点" min-width="150" align="center">
          <template #default="{ row }">
            <template v-if="row.latestLogisticsEvent">
              <el-tag
                :type="logisticsTagType(row.latestLogisticsEvent)"
                size="small"
              >{{ LOGISTICS_EVENT_MAP[row.latestLogisticsEvent] || row.latestLogisticsEvent }}</el-tag>
            </template>
            <span v-else style="color:#909399">-</span>
          </template>
        </el-table-column>
        <el-table-column label="预计到货(ETA)" min-width="200">
          <template #default="{ row }">
            <EmptyText :value="row.latestEta">{{ formatDateTime(row.latestEta) }}</EmptyText>
          </template>
        </el-table-column>
        <el-table-column prop="deliveryDate" label="合同交期" min-width="120">
          <template #default="{ row }">
            <EmptyText :value="row.deliveryDate" />
          </template>
        </el-table-column>
        <el-table-column label="交付状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="DELIVERY_STATUS_TAG[row.status] || 'info'">{{ DELIVERY_STATUS_MAP[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <!-- 承诺交期：待确认橙色提醒，已确认直接展示日期，无承诺为空 -->
        <el-table-column v-if="isBuyer" label="承诺交期" min-width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.pendingPromiseId" type="warning" size="small">待确认</el-tag>
            <template v-else-if="row.confirmedDeliveryDate">{{ row.confirmedDeliveryDate }}</template>
            <span v-else style="color:#909399">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleTracking(row)">追踪</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty v-if="!loading" :description="searchForm.keyword || searchForm.status ? '未找到匹配的交付单' : '暂无在途交付单'" />
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
import { listDeliveries } from '@/api/delivery'
import { DELIVERY_STATUS_MAP, DELIVERY_STATUS_TAG, TRACKING_STATUS_OPTIONS, TRACKING_STATUS_RANGE } from '@/constants/delivery'
import { LOGISTICS_EVENT_MAP } from '@/constants/risk'
import { PAGE_SIZE_OPTIONS } from '@/constants'
import { formatDateTime } from '@/utils/datetime'
import { useAuthStore } from '@/stores/auth'
import IdColumn from '@/components/IdColumn.vue'
import EmptyText from '@/components/EmptyText.vue'

const router = useRouter()
const authStore = useAuthStore()
const isDual = computed(() => authStore.profileType === 'both')
const searchForm = reactive({ keyword: '', status: '', role: authStore.profileType === 'supplier' ? 'supplier' : 'buyer' })
// 承诺状态列仅采购方视角可见（承诺由采购方确认；双角色按当前视角切换）
const isBuyer = computed(() => {
  if (isDual.value) return searchForm.role !== 'supplier'
  return authStore.profileType !== 'supplier'
})

const list = ref([])
const loading = ref(true)
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

onMounted(() => { fetchList() })

async function fetchList() {
  loading.value = true
  try {
    // 追踪范围固定为已发货及之后，叠加用户选择的状态筛选（status 放最后覆盖 searchForm 里的空值）
    const status = searchForm.status ? searchForm.status : TRACKING_STATUS_RANGE
    const res = await listDeliveries({ pageNum: pageNum.value, pageSize: pageSize.value, ...searchForm, status })
    list.value = res?.data?.records || res?.records || []
    total.value = res?.data?.total || res?.total || 0
  } finally { loading.value = false }
}

function handleSearch() { pageNum.value = 1; fetchList() }
function handleReset() {
  searchForm.keyword = ''
  searchForm.status = ''
  pageNum.value = 1
  fetchList()
}

/** 最新物流节点标签色：异常类红色，到达/清关完成绿色 */
function logisticsTagType(eventType) {
  if (eventType === 'delay' || eventType === 'exception' || eventType === 'customs_hold') return 'danger'
  if (eventType === 'arrived_delivered' || eventType === 'customs_clear') return 'success'
  return 'primary'
}

function handleTracking(row) {
  router.push({ name: 'LogisticsTrackingDetail', params: { id: row.id } })
}
</script>
