<template>
  <div>
    <!-- 搜索栏 -->
    <el-card class="search-card">
      <el-form
        :inline="true"
        :model="searchForm"
        class="search-form"
        label-width="64px"
      >
        <el-form-item label="模块">
          <el-select
            v-model="searchForm.module"
            placeholder="全部"
            clearable
            class="filter-ctl filter-ctl-md"
            @change="handleSearch"
          >
            <el-option
              v-for="item in MODULE_OPTIONS"
              :key="item"
              :label="item"
              :value="item"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="操作人">
          <el-input
            v-model="searchForm.username"
            placeholder="请输入姓名"
            clearable
            class="filter-ctl filter-ctl-md"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="searchForm.status"
            placeholder="全部"
            clearable
            class="filter-ctl filter-ctl-sm"
            @change="handleSearch"
          >
            <el-option
              label="成功"
              value="success"
            />
            <el-option
              label="失败"
              value="fail"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="时间">
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            range-separator="~"
            start-placeholder="开始"
            end-placeholder="结束"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DD HH:mm:ss"
            class="filter-ctl filter-ctl-date"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :icon="Search"
            @click="handleSearch"
          >
            搜索
          </el-button>
          <el-button
            :icon="RefreshRight"
            @click="handleReset"
          >
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card v-loading="loading" class="content-card">
      <div class="table-toolbar">
        <span class="text-base font-semibold text-gray-700">操作日志列表</span>
      </div>

      <el-table :data="tableData"
        border
        stripe
        :cell-style="{ padding: '10px 0' }"
      >
        <IdColumn :page-num="pagination.pageNum" :page-size="pagination.pageSize" />
        <el-table-column
          label="操作人"
          min-width="140"
        >
          <template #default="{ row }">
            {{ row.realName || row.username || '—' }}
          </template>
        </el-table-column>
        <el-table-column
          prop="module"
          label="模块"
          min-width="120"
        />
        <el-table-column
          prop="description"
          label="操作描述"
          min-width="220"
          show-overflow-tooltip
        />
        <el-table-column
          prop="status"
          label="状态"
          min-width="90"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              :type="row.status === 'success' ? 'success' : 'danger'"
              size="small"
            >
              {{ row.status === 'success' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="createTime"
          label="操作时间"
          min-width="170"
          align="center"
        >
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column
          label="操作" min-width="80"
          class-name="ops-col"
          align="center"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              :icon="View"
              @click="handleDetail(row)"
            >
              详情
            </el-button>
          </template>
        </el-table-column>
              <template #empty>
          <el-empty description="暂无数据" />
        </template>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :page-sizes="PAGE_SIZE_OPTIONS"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchList"
          @current-change="fetchList"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="detailVisible"
      title="操作日志详情"
      width="680px"
    >
      <template v-if="detailData">
        <el-descriptions
          title="业务信息"
          :column="2"
          border
          label-width="88px"
          class="detail-desc"
        >
          <el-descriptions-item label="操作人">
            {{ detailData.realName || detailData.username || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="模块">
            {{ detailData.module || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="操作类型">
            <el-tag
              size="small"
              :type="operationTypeTag(detailData.operation)"
            >
              {{ detailData.operation || '—' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag
              :type="detailData.status === 'success' ? 'success' : 'danger'"
              size="small"
            >
              {{ detailData.status === 'success' ? '成功' : '失败' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="操作描述" :span="2">
            {{ detailData.description || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="操作时间">
            {{ formatTime(detailData.createTime) }}
          </el-descriptions-item>
        </el-descriptions>

        <el-descriptions
          title="请求信息"
          :column="2"
          border
          label-width="88px"
          class="detail-desc"
        >
          <el-descriptions-item label="请求" :span="2">
            <span class="req-line">
              <template v-if="detailData.requestMethod">{{ detailData.requestMethod }} </template>
              {{ detailData.requestUrl || '—' }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="客户端 IP">
            {{ detailData.ip || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="耗时">
            {{ detailData.costTimeMs != null ? `${detailData.costTimeMs} ms` : '—' }}
          </el-descriptions-item>
          <el-descriptions-item
            v-if="detailData.requestParams"
            label="请求参数"
            :span="2"
          >
            <pre class="json-block">{{ formatJson(detailData.requestParams) }}</pre>
          </el-descriptions-item>
        </el-descriptions>

        <el-descriptions
          v-if="detailData.status === 'fail' && detailData.errorMsg"
          title="失败原因"
          :column="1"
          border
          label-width="88px"
          class="detail-desc detail-desc-fail"
        >
          <el-descriptions-item label="错误信息">
            {{ detailData.errorMsg }}
          </el-descriptions-item>
        </el-descriptions>
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">
          关闭
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Search, RefreshRight, View } from '@element-plus/icons-vue'
import { getAuditLogList } from '@/api/audit-log'
import { PAGE_SIZE_OPTIONS } from '@/constants'
import { formatDateTime as formatTime } from '@/utils/datetime'
import IdColumn from '@/components/IdColumn.vue'

// ---- 搜索 ----
const dateRange = ref(null)
const searchForm = reactive({
  module: '',
  operation: '',
  username: '',
  status: '',
})

const MODULE_OPTIONS = ['租户管理', '用户管理', '角色管理', '菜单管理', '入驻审核', '尽调管理', '询盘管理', '报价管理', '合同管理']

// ---- 列表 ----
const loading = ref(true)
const tableData = ref([])
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

async function fetchList() {
  loading.value = true
  try {
    const params = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      ...searchForm,
      startTime: dateRange.value?.[0] || undefined,
      endTime: dateRange.value?.[1] || undefined,
    }
    const res = await getAuditLogList(params)
    tableData.value = res.data.records || []
    pagination.total = res.data.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.pageNum = 1
  fetchList()
}

function handleReset() {
  searchForm.module = ''
  searchForm.operation = ''
  searchForm.username = ''
  searchForm.status = ''
  dateRange.value = null
  pagination.pageNum = 1
  fetchList()
}

// ---- 详情 ----
const detailVisible = ref(false)
const detailData = ref(null)

function handleDetail(row) {
  detailData.value = row
  detailVisible.value = true
}

/** 格式化 JSON 字符串 */
function formatJson(str) {
  if (!str) return '-'
  try {
    return JSON.stringify(JSON.parse(str), null, 2)
  } catch {
    return str
  }
}

function operationTypeTag(operation) {
  const map = {
    CREATE: 'success',
    UPDATE: 'warning',
    DELETE: 'danger',
  }
  return map[operation] || 'info'
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped>
.search-form {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0 8px;
}
.search-form :deep(.el-form-item) {
  margin-right: 8px;
  margin-bottom: 8px;
}
.search-form :deep(.el-form-item__label) {
  font-size: 13px;
  color: #606266;
  padding-right: 8px;
}
.filter-ctl-sm { width: 110px; }
.filter-ctl-md { width: 140px; }
.filter-ctl-date { width: 340px; }

.detail-desc {
  margin-bottom: 16px;
}
.detail-desc :deep(.el-descriptions__title) {
  font-size: 14px;
  font-weight: 600;
}
.detail-desc :deep(.el-descriptions__label) {
  width: 88px;
  text-align: center;
  justify-content: center;
}
.req-line {
  word-break: break-all;
  font-family: Menlo, Consolas, monospace;
  font-size: 12px;
}
.json-block {
  margin: 0;
  max-height: 220px;
  overflow: auto;
  font-family: Menlo, Consolas, monospace;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}
.detail-desc-fail :deep(.el-descriptions__content) {
  color: #dc2626;
}
</style>
