<template>
  <div>
    <!-- 搜索 -->
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="关键词">
          <el-input
            v-model="searchForm.keyword"
            placeholder="名单名/别名"
            clearable
            style="width: 240px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card v-loading="loading" class="content-card">
      <div class="table-toolbar">
        <span class="text-base font-semibold text-gray-700">制裁名单库</span>
        <div class="flex items-center gap-3">
          <span v-if="syncStatus" class="text-sm text-gray-500">
            共 {{ syncStatus.totalCount ?? 0 }} 条
            <template v-if="syncStatus.lastSyncedAt"> · 最近同步 {{ formatTime(syncStatus.lastSyncedAt) }}</template>
          </span>
          <el-button type="primary" :icon="Refresh" :loading="syncing" @click="handleSync">同步名单</el-button>
        </div>
      </div>

      <el-alert
        v-if="syncStatus && syncStatus.lastSyncStatus === 'failed'"
        type="error"
        :closable="false"
        class="mb-3"
        :title="'上次同步失败：' + (syncStatus.lastSyncError || '未知错误')"
      />

      <el-table :data="list" border stripe>
        <IdColumn :page-num="pageNum" :page-size="pageSize" />
        <el-table-column prop="name" label="主体名称" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="font-medium">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" min-width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ ENTITY_TYPE_MAP[row.entityType] || row.entityType || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="jurisdiction" label="国别/地区" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">
            {{ countryCn(row.jurisdiction) }}
          </template>
        </el-table-column>
        <el-table-column prop="program" label="名单程序" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.program || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="sourceName" label="来源" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            {{ sourceCn(row.sourceName) }}
          </template>
        </el-table-column>
        <template #empty>
          <el-empty v-if="!loading" :description="searchForm.keyword ? '未找到匹配的名单' : '名单库为空，请点击右上角「同步名单」拉取数据'" />
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
import { ref, reactive, onMounted } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { listSanctionEntries, syncSanctionEntries, getSanctionSyncStatus } from '@/api/sanction'
import { PAGE_SIZE_OPTIONS } from '@/constants'
import { countryCn, sourceCn } from '@/constants/sanction'
import { formatDateTime as formatTime } from '@/utils/datetime'
import IdColumn from '@/components/IdColumn.vue'

const ENTITY_TYPE_MAP = {
  Company: '公司',
  Organization: '组织',
  Person: '个人',
  Vessel: '船舶',
  Aircraft: '飞机'
}

const searchForm = reactive({ keyword: '' })
const list = ref([])
const loading = ref(true)
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const syncStatus = ref(null)

onMounted(() => { fetchList(); fetchSyncStatus() })

async function fetchList() {
  loading.value = true
  try {
    const res = await listSanctionEntries({ pageNum: pageNum.value, pageSize: pageSize.value, keyword: searchForm.keyword || undefined })
    list.value = res?.data?.records || []
    total.value = res?.data?.total || 0
  } catch { /* 拦截器已处理 */ } finally { loading.value = false }
}

async function fetchSyncStatus() {
  try {
    const res = await getSanctionSyncStatus()
    syncStatus.value = res?.data || null
  } catch { /* 拦截器已处理 */ }
}

function handleSearch() { pageNum.value = 1; fetchList() }
function handleReset() { searchForm.keyword = ''; handleSearch() }

async function handleSync() {
  try {
    // 异步触发：立即返回，随后轮询状态直到 running 结束
    await syncSanctionEntries()
    ElMessage.info('同步任务已启动，正在后台拉取名单…')
    await pollSyncStatus()
  } catch { /* 拦截器已处理 */ }
}

/** 轮询同步状态，直到 success / failed / never（非 running） */
async function pollSyncStatus() {
  for (let i = 0; i < 60; i++) {
    await new Promise((r) => setTimeout(r, 5000))
    const res = await getSanctionSyncStatus()
    const s = res?.data || {}
    syncStatus.value = s
    if (s.lastSyncStatus !== 'running') {
      if (s.lastSyncStatus === 'success') {
        ElMessage.success(`同步完成，共 ${s.syncedCount ?? 0} 条`)
        fetchList()
      } else if (s.lastSyncStatus === 'failed') {
        ElMessage.error('同步失败：' + (s.lastSyncError || '未知错误'))
      }
      return
    }
  }
  ElMessage.warning('同步耗时较长，仍在后台进行，稍后可刷新查看结果')
}
</script>

<style scoped>
.search-card { margin-bottom: 16px; }
.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
</style>
