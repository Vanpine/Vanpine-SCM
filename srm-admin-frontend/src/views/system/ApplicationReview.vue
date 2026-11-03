<template>
  <div>
    <!-- 搜索栏 -->
    <el-card class="search-card">
      <el-form
        :inline="true"
        :model="searchForm"
        class="search-form"
      >
        <el-form-item label="公司名称">
          <el-input
            v-model="searchForm.companyName"
            placeholder="请输入公司名称"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input
            v-model="searchForm.contactName"
            placeholder="请输入联系人"
            clearable
            style="width: 160px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="searchForm.status"
            placeholder="全部"
            clearable
            style="width: 140px"
            @change="handleSearch"
          >
            <el-option
              v-for="(item, key) in APPLICATION_STATUS_MAP"
              :key="key"
              :label="item.label"
              :value="key"
            />
          </el-select>
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
        <span class="text-base font-semibold text-gray-700">入驻预审列表</span>
      </div>

      <el-table :data="tableData"
        border
        stripe
      >
        <el-table-column
          type="index"
          :index="(idx) => pagination.total - (pagination.pageNum - 1) * pagination.pageSize - idx"
          label="ID"
          width="50"
          align="center"
        />
        <el-table-column
          prop="companyName"
          label="公司名称"
          min-width="180"
        >
          <template #default="{ row }">
            <span class="font-medium">{{ row.companyName }}</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="contactName"
          label="联系人"
          min-width="100"
        />
        <el-table-column
          prop="contactEmail"
          label="联系邮箱"
          min-width="180"
        />
        <el-table-column
          prop="applicantRoleLabel"
          label="申请身份"
          min-width="130"
          align="center"
        />
        <el-table-column
          prop="country"
          label="国家/地区"
          min-width="110"
          align="center"
        />
        <el-table-column
          label="制裁筛查"
          min-width="110"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              :type="SANCTION_STATUS_MAP[row.sanctionStatus]?.type || 'info'"
              size="small"
            >
              {{ SANCTION_STATUS_MAP[row.sanctionStatus]?.label || '未筛查' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="status"
          label="状态"
          min-width="100"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              :type="APPLICATION_STATUS_MAP[row.status]?.type || 'info'"
              size="small"
            >
              {{ APPLICATION_STATUS_MAP[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="createTime"
          label="提交时间"
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
            <template v-if="row.status === 'pending_review'">
              <el-dropdown trigger="click" @command="(cmd) => handleAppCmd(cmd, row)">
                <el-icon :size="16" style="cursor:pointer;color:#409eff;margin-left:4px;margin-top:5px;transform:rotate(90deg)"><MoreFilled /></el-icon>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="approve" :icon="Select">通过</el-dropdown-item>
                    <el-dropdown-item command="reject" divided style="color:#f56c6c" :icon="CloseBold">驳回</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
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

    <!-- 详情弹窗 -->
    <el-dialog
      v-model="detailVisible"
      title="入驻预审详情"
      width="700px"
    >
      <template v-if="detailData">
        <el-descriptions
          title="基本信息"
          :column="2"
          border
          class="detail-descriptions"
        >
          <el-descriptions-item
            label="公司名称"
            :span="2"
          >
            {{ detailData.companyName }}
          </el-descriptions-item>
          <el-descriptions-item label="申请身份">
            {{ detailData.applicantRoleLabel }}
          </el-descriptions-item>
          <el-descriptions-item label="所在国家">
            {{ detailData.country }}
          </el-descriptions-item>
          <el-descriptions-item label="企业注册号">
            {{ detailData.regNumber }}
          </el-descriptions-item>
          <el-descriptions-item label="成立年份">
            {{ detailData.establishedYear || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="联系人">
            {{ detailData.contactName }}
          </el-descriptions-item>
          <el-descriptions-item label="职位">
            {{ detailData.contactTitle || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="邮箱">
            {{ detailData.contactEmail }}
          </el-descriptions-item>
          <el-descriptions-item label="电话">
            {{ detailData.contactPhone }}
          </el-descriptions-item>
        </el-descriptions>

        <!-- 采购方信息 -->
        <template v-if="detailData.applicantRole?.includes('buyer')">
          <el-descriptions
            title="采购方信息"
            :column="2"
            border
            class="detail-descriptions"
          >
            <el-descriptions-item
              label="采购品类"
              :span="2"
            >
              {{ detailData.buyCategory || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="年采购额">
              {{ currencyAmountLabel(detailData.buyVolumeAmount, detailData.buyVolumeCurrency) || detailData.buyVolumeLabel || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="目标市场">
              {{ detailData.buyMarket || '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </template>

        <!-- 供应方信息 -->
        <template v-if="detailData.applicantRole?.includes('supplier')">
          <el-descriptions
            title="供应方信息"
            :column="2"
            border
            class="detail-descriptions"
          >
            <el-descriptions-item
              label="生产品类"
              :span="2"
            >
              {{ detailData.supCategory || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="年产能">
              {{ detailData.supCapacity || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="出口经验">
              {{ detailData.supExportLabel || '-' }}
            </el-descriptions-item>
            <el-descriptions-item
              label="资质认证"
              :span="2"
            >
              {{ detailData.supCerts || '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </template>

        <!-- 制裁筛查 -->
        <el-descriptions
          title="制裁筛查"
          :column="1"
          border
          class="detail-descriptions"
        >
          <el-descriptions-item label="筛查结果">
            <div class="flex items-center gap-3">
              <el-tag
                :type="SANCTION_STATUS_MAP[screenResult?.status]?.type || 'info'"
                size="small"
              >
                {{ SANCTION_STATUS_MAP[screenResult?.status]?.label || '未筛查' }}
              </el-tag>
              <el-button
                type="primary"
                size="small"
                :loading="screening"
                @click="handleScreen"
              >
                筛查/重查
              </el-button>
            </div>
          </el-descriptions-item>
          <el-descriptions-item v-if="screenResult?.status === 'warning'" label="命中明细">
            <el-alert
              type="warning"
              :closable="false"
              :title="`命中 ${screenResult.hitCount} 条制裁名单，请人工复核后决定是否通过`"
            />
            <div
              v-for="(m, i) in screenResult.matches"
              :key="i"
              class="sanction-match"
            >
              <span class="font-medium">{{ m.entryName }}</span>
              <span class="text-gray-500">{{ m.program || '-' }} · {{ m.jurisdiction || '-' }}</span>
            </div>
          </el-descriptions-item>
          <el-descriptions-item v-else-if="screenResult?.status === 'clean'" label="说明">
            未命中制裁名单
          </el-descriptions-item>
          <el-descriptions-item v-else-if="screenResult?.status === 'pending'" label="说明">
            名单库为空，请先在「制裁名单库」同步数据后再筛查
          </el-descriptions-item>
        </el-descriptions>

        <!-- 审核信息 -->
        <template v-if="detailData.status !== 'pending_review'">
          <el-descriptions
            title="审核信息"
            :column="2"
            border
            class="detail-descriptions"
          >
            <el-descriptions-item label="状态">
              <el-tag
                :type="APPLICATION_STATUS_MAP[detailData.status]?.type"
                size="small"
              >
                {{ APPLICATION_STATUS_MAP[detailData.status]?.label }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="审核意见">
              {{ detailData.reviewComment || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="审核时间">
              {{ formatTime(detailData.reviewedAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="关联租户ID">
              {{ detailData.tenantId || '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </template>
      </template>
    </el-dialog>

    <!-- 审核通过弹窗 -->
    <el-dialog
      v-model="approveVisible"
      title="审核通过 - 创建租户"
      width="500px"
    >
      <p class="mb-4">
        为 <span class="highlight-name">{{ approveTarget?.companyName }}</span> 创建租户和管理员账号
      </p>
      <el-form
        :model="approveForm"
        label-width="100px"
      >
        <el-form-item
          label="租户编码"
          required
        >
          <el-input
            v-model="approveForm.tenantCode"
            placeholder="英文简称，登录时使用"
            :maxlength="32"
          />
        </el-form-item>
        <el-form-item label="管理员账号">
          <el-input
            v-model="approveForm.adminUsername"
            placeholder="admin"
            :maxlength="64"
          />
        </el-form-item>
        <el-form-item label="管理员密码">
          <el-input
            v-model="approveForm.adminPassword"
            placeholder="admin123"
            :maxlength="32"
            show-password
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveVisible = false">
          取消
        </el-button>
        <el-button
          type="success"
          :loading="submitting"
          @click="confirmApprove"
        >
          确认通过
        </el-button>
      </template>
    </el-dialog>

    <!-- 驳回弹窗 -->
    <el-dialog
      v-model="rejectVisible"
      title="驳回入驻预审"
      width="500px"
    >
      <p class="mb-4">
        确认驳回 <span class="highlight-name">{{ rejectTarget?.companyName }}</span> 的入驻申请？
      </p>
      <el-input
        v-model="rejectComment"
        type="textarea"
        :rows="3"
        placeholder="请输入驳回原因（必填）"
        :maxlength="512"
        show-word-limit
      />
      <template #footer>
        <el-button @click="rejectVisible = false">
          取消
        </el-button>
        <el-button
          type="danger"
          :loading="submitting"
          @click="confirmReject"
        >
          确认驳回
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getApplicationList, getApplicationDetail, reviewApplication } from '@/api/application'
import { screenApplication } from '@/api/sanction'
import { APPLICATION_STATUS_MAP, SANCTION_STATUS_MAP, PAGE_SIZE_OPTIONS, currencyAmountLabel } from '@/constants'
import { formatDateTime as formatTime } from '@/utils/datetime'
import { ElMessage } from 'element-plus'
import { Search, RefreshRight, View, Select, CloseBold, MoreFilled } from '@element-plus/icons-vue'

// ---- 搜索 ----
const searchForm = reactive({ companyName: '', contactName: '', status: '' })

// ---- 分页 ----
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

// ---- 列表 ----
const loading = ref(true)
const tableData = ref([])

// ---- 详情 ----
const detailVisible = ref(false)
const detailData = ref(null)

// ---- 制裁筛查 ----
const screening = ref(false)
const screenResult = ref(null)

// ---- 审核通过 ----
const approveVisible = ref(false)
const approveTarget = ref(null)
const approveForm = reactive({ tenantCode: '', adminUsername: 'admin', adminPassword: 'admin123' })

// ---- 驳回 ----
const rejectVisible = ref(false)
const rejectTarget = ref(null)
const rejectComment = ref('')
const submitting = ref(false)

/** 从邮箱提取域名建议租户编码 */
function suggestTenantCode(email) {
  if (!email) return ''
  const atIdx = email.lastIndexOf('@')
  if (atIdx < 0) return ''
  const domain = email.substring(atIdx + 1)
  // 取第一个点之前的部分，去掉非字母
  const part = domain.split('.')[0]
  return part.replace(/[^a-zA-Z0-9]/g, '').toLowerCase()
}

/** 加载列表 */
async function fetchList() {
  loading.value = true
  try {
    const { data } = await getApplicationList({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      companyName: searchForm.companyName || undefined,
      contactName: searchForm.contactName || undefined,
      status: searchForm.status || undefined,
    })
    tableData.value = data.records || []
    pagination.total = data.total || 0
  } catch {
    tableData.value = []
  } finally {
    loading.value = false
  }
}

/** 搜索 */
function handleSearch() {
  pagination.pageNum = 1
  fetchList()
}

/** 重置 */
function handleReset() {
  searchForm.companyName = ''
  searchForm.contactName = ''
  searchForm.status = ''
  handleSearch()
}

/** 查看详情 */
async function handleDetail(row) {
  try {
    const { data } = await getApplicationDetail(row.id)
    detailData.value = data
    detailVisible.value = true
    // 已有筛查记录则直接展示状态，无则待用户点「筛查」
    if (data?.sanctionStatus) {
      screenResult.value = { status: data.sanctionStatus, hitCount: data.sanctionHitCount || 0, matches: [] }
    } else {
      screenResult.value = null
    }
  } catch { /* 拦截器已处理 */ }
}

/** 对当前详情申请做筛查/重查 */
async function handleScreen() {
  if (!detailData.value) return
  screening.value = true
  try {
    const { data } = await screenApplication(detailData.value.id)
    screenResult.value = data
    ElMessage.success(data.status === 'warning' ? `命中 ${data.hitCount} 条制裁名单` : '筛查完成')
    fetchList()
  } catch { /* 拦截器已处理 */ } finally {
    screening.value = false
  }
}

/** 审核通过：打开表单弹窗 */
/** 下拉菜单命令分发 */
function handleAppCmd(cmd, row) {
  if (cmd === 'approve') handleApprove(row)
  else if (cmd === 'reject') handleReject(row)
}

function handleApprove(row) {
  approveTarget.value = row
  // 从邮箱自动建议租户编码
  approveForm.tenantCode = suggestTenantCode(row.contactEmail)
  approveForm.adminUsername = 'admin'
  approveForm.adminPassword = 'admin123'
  approveVisible.value = true
}

/** 确认通过 */
async function confirmApprove() {
  if (!approveForm.tenantCode.trim()) {
    ElMessage.warning('请输入租户编码')
    return
  }
  if (!approveForm.adminUsername.trim()) {
    ElMessage.warning('请输入管理员账号')
    return
  }
  if (!approveForm.adminPassword.trim()) {
    ElMessage.warning('请输入管理员密码')
    return
  }
  submitting.value = true
  try {
    await reviewApplication(approveTarget.value.id, {
      status: 'approved',
      tenantCode: approveForm.tenantCode.trim(),
      adminUsername: approveForm.adminUsername.trim(),
      adminPassword: approveForm.adminPassword.trim(),
    })
    ElMessage.success('审核通过，租户和管理员账号已创建')
    approveVisible.value = false
    fetchList()
  } catch { /* 拦截器已处理 */ } finally {
    submitting.value = false
  }
}

/** 打开驳回弹窗 */
function handleReject(row) {
  rejectTarget.value = row
  rejectComment.value = ''
  rejectVisible.value = true
}

/** 确认驳回 */
async function confirmReject() {
  if (!rejectComment.value.trim()) {
    ElMessage.warning('请输入驳回原因')
    return
  }
  submitting.value = true
  try {
    await reviewApplication(rejectTarget.value.id, { status: 'rejected', reviewComment: rejectComment.value })
    ElMessage.success('已驳回该入驻申请')
    rejectVisible.value = false
    fetchList()
  } catch { /* 拦截器已处理 */ } finally {
    submitting.value = false
  }
}

onMounted(() => { fetchList() })
</script>

<style scoped>
.search-card { margin-bottom: 16px; }
.search-form { margin-bottom: 0; }
.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.pagination-wrap {
  justify-content: center;
}
.detail-descriptions { margin-bottom: 16px; }
.sanction-match {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 0;
  border-bottom: 1px dashed #ebeef5;
}
.sanction-match:last-child { border-bottom: none; }
</style>
