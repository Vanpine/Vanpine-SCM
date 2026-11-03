<template>
  <div>
    <!-- 搜索卡片 -->
    <el-card class="search-card">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" placeholder="需求标题/对方企业" clearable style="width:220px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width:120px" @change="handleSearch">
            <el-option label="待处理" value="pending" />
            <el-option label="已接受" value="accepted" />
            <el-option label="已拒绝" value="rejected" />
            <el-option label="已取消" value="closed" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="isDual" label="视角">
          <el-select v-model="searchForm.role" placeholder="全部" clearable style="width:110px" @change="handleSearch">
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
        <span class="text-base font-semibold text-gray-700">对接记录</span>
      </div>

      <el-empty v-if="!loading && list.length === 0" :description="searchForm.keyword || searchForm.status || (isDual && searchForm.role) ? '未找到匹配的对接记录' : '暂无对接记录'" />

      <el-table :data="list" stripe border>
        <el-table-column type="index" :index="(idx) => (pageNum - 1) * pageSize + idx + 1" label="ID" width="55" align="center" />
        <el-table-column label="对方企业" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <EmptyText :value="isBuyer(row) ? row.supplierCompanyName : row.buyerCompanyName" />
          </template>
        </el-table-column>
        <el-table-column prop="requirementTitle" label="需求标题" min-width="220" show-overflow-tooltip />
        <el-table-column v-if="isDual" label="角色" width="70" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" :type="isBuyer(row) ? '' : 'warning'">
              {{ isBuyer(row) ? '采购' : '供应' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="方向" width="70" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" :type="row.createBy === userId ? 'primary' : 'info'">
              {{ row.createBy === userId ? '发起' : '收到' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="95" align="center">
          <template #default="{ row }">
            <el-tooltip v-if="row.status === 'rejected' && row.replyMessage" :content="row.replyMessage" placement="top">
              <el-tag :type="connStatusTag(row)" size="small">{{ connStatusLabel(row) }}</el-tag>
            </el-tooltip>
            <el-tag v-else :type="connStatusTag(row)" size="small">{{ connStatusLabel(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发起时间" min-width="140">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="100" fixed="right" align="center">
          <template #default="{ row }">
            <!-- 只外露 1 个主操作；其余进三点。待处理：审核 = 接受/拒绝，与合同审核一致 -->
            <el-dropdown v-if="connPrimaryAction(row) === 'review'" trigger="click" @command="(cmd) => handleConnCmd(cmd, row)">
              <el-button type="success" link size="small">审核</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="accept">接受</el-dropdown-item>
                  <el-dropdown-item command="reject" divided style="color:#f56c6c">拒绝</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button v-else-if="connPrimaryAction(row) === 'close'" type="danger" link size="small" @click="handleClose(row)">取消对接</el-button>
            <el-button v-else-if="connPrimaryAction(row) === 'inquiry'" type="primary" link size="small" @click="handleCreateInquiry(row)">发起询盘</el-button>
            <el-button v-else-if="connPrimaryAction(row) === 'awarded'" type="success" link size="small" disabled>已授标</el-button>
            <el-button v-else-if="connPrimaryAction(row) === 'inquired'" type="primary" link size="small" disabled>已发起询盘</el-button>
            <el-button v-else-if="connPrimaryAction(row) === 'contact'" type="success" link size="small" @click="showContact(row)">查看联系</el-button>
            <el-button v-else-if="connPrimaryAction(row) === 'reapply'" type="primary" link size="small" @click="handleReapply(row)">重新申请</el-button>
            <el-button v-else type="primary" link size="small" @click="showProfile(row)">查看画像</el-button>
            <el-dropdown v-if="connMenuItems(row).length" trigger="click" @command="(cmd) => handleConnCmd(cmd, row)">
              <el-icon :size="16" style="cursor:pointer;color:#409eff;margin-left:4px;margin-top:5px"><MoreFilled /></el-icon>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="connMenuItems(row).includes('profile')" command="profile">查看画像</el-dropdown-item>
                  <el-dropdown-item v-if="connMenuItems(row).includes('contact')" command="contact">查看联系</el-dropdown-item>
                  <el-dropdown-item v-if="connMenuItems(row).includes('viewMessage')" command="viewMessage">查看留言</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
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

    <!-- 留言详情弹窗 -->
    <el-dialog v-model="messageVisible" title="留言详情" :close-on-click-modal="false" class="message-dialog">
      <div style="white-space:pre-wrap;word-break:break-word;line-height:1.8;max-height:60vh;overflow-y:auto">{{ messageContent }}</div>
      <template #footer>
        <el-button @click="messageVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 拒绝原因弹窗 -->
    <el-dialog v-model="rejectVisible" title="拒绝对接" :close-on-click-modal="false" class="reject-dialog">
      <el-form label-width="80px" label-position="right">
        <el-form-item label="拒绝原因">
          <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="可选填写拒绝原因" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="doReject">确认拒绝</el-button>
      </template>
    </el-dialog>

    <!-- 重新申请弹窗 -->
    <el-dialog v-model="reapplyVisible" title="重新申请" :close-on-click-modal="false" class="reapply-dialog">
      <template v-if="reapplyTarget">
        <div style="margin-bottom:16px;color:#606266;font-size:14px;line-height:2;text-align:center">
          重新申请需求 <span class="highlight-name">{{ reapplyTarget.requirementTitle || '' }}</span>
          <br />对方企业：<EmptyText :value="isBuyer(reapplyTarget) ? reapplyTarget.supplierCompanyName : reapplyTarget.buyerCompanyName" />
        </div>
        <el-input v-model="reapplyMessage" type="textarea" :rows="3" placeholder="留言（可选），如：我司产品与贵方需求高度匹配，期望合作" maxlength="500" show-word-limit />
      </template>
      <template #footer>
        <el-button @click="reapplyVisible = false">取消</el-button>
        <el-button type="primary" :loading="reapplying" @click="doReapply">确认申请</el-button>
      </template>
    </el-dialog>

    <!-- 联系方式弹窗 -->
    <el-dialog v-model="contactVisible" title="对方联系方式" :close-on-click-modal="false" class="contact-dialog">
      <template v-if="contactTarget">
        <el-descriptions :column="1" border class="detail-descriptions">
          <el-descriptions-item label="企业名称"><EmptyText :value="isBuyer(contactTarget) ? contactTarget.supplierCompanyName : contactTarget.buyerCompanyName" /></el-descriptions-item>
          <el-descriptions-item label="联系人"><EmptyText :value="isBuyer(contactTarget) ? contactTarget.supplierContactName : contactTarget.buyerContactName" /></el-descriptions-item>
          <el-descriptions-item label="联系电话"><EmptyText :value="isBuyer(contactTarget) ? contactTarget.supplierContactPhone : contactTarget.buyerContactPhone" /></el-descriptions-item>
          <el-descriptions-item label="联系邮箱"><EmptyText :value="isBuyer(contactTarget) ? contactTarget.supplierContactEmail : contactTarget.buyerContactEmail" /></el-descriptions-item>
        </el-descriptions>
      </template>
      <template #footer>
        <el-button @click="contactVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <ProfileViewer v-model="profileVisible" :profile-data="profileData" :view-mode="profileViewMode" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, MoreFilled } from '@element-plus/icons-vue'
import { listConnections, acceptConnection, rejectConnection, applyRequirement, createConnection, closeConnection, getPublicProfile } from '@/api/match'
import { checkActiveInquiries } from '@/api/procurement'
import { useAuthStore } from '@/stores/auth'
import { PAGE_SIZE_OPTIONS } from '@/constants'
import { formatTime } from '@/utils/format'
import ProfileViewer from '@/components/ProfileViewer.vue'

const authStore = useAuthStore()
const router = useRouter()
const tenantId = authStore.user?.tenantId
const userId = authStore.user?.id
const isDual = computed(() => authStore.profileType === 'both')
const lockedRole = computed(() => {
  if (authStore.profileType === 'supplier') return 'supplier'
  if (authStore.profileType === 'buyer') return 'buyer'
  return ''
})

/** 发起方视角标签：自己发起的请求等待对方处理 */
const INITIATOR_LABEL = { pending: '等待回应', accepted: '已接受', rejected: '已拒绝', closed: '已取消' }
const INITIATOR_TAG = { pending: 'primary', accepted: 'success', rejected: 'danger', closed: 'info' }
/** 接收方视角标签：需要自己去处理 */
const RECEIVER_LABEL = { pending: '待处理', accepted: '已接受', rejected: '已拒绝', closed: '已取消' }
const RECEIVER_TAG = { pending: 'primary', accepted: 'success', rejected: 'danger', closed: 'info' }

function isBuyer(row) { return String(row.buyerTenantId) === String(tenantId) }
function isInitiator(row) { return String(row.createBy) === String(userId) }
function connStatusLabel(row) { return isInitiator(row) ? (INITIATOR_LABEL[row.status] || row.status) : (RECEIVER_LABEL[row.status] || row.status) }
function connStatusTag(row) { return isInitiator(row) ? (INITIATOR_TAG[row.status] || 'info') : (RECEIVER_TAG[row.status] || 'info') }
/** 取对方发给我的留言（自己写的不展示） */
function getMessage(row) {
  if (row.createBy !== userId) return row.message || ''
  if (row.status === 'rejected' && row.replyMessage) return row.replyMessage
  return ''
}

/** 对接记录：只外露 1 个主操作 */
function connPrimaryAction(row) {
  if (row.status === 'pending' && row.createBy !== userId) return 'review'
  if (row.status === 'pending' && row.createBy === userId) return 'close'
  if (row.status === 'accepted' && isBuyer(row)) {
    if (activeInquiryMap.value[row.id] === 'awarded') return 'awarded'
    if (activeInquiryMap.value[row.id]) return 'inquired'
    return 'inquiry'
  }
  if (row.status === 'accepted' && !isBuyer(row)) return 'contact'
  if ((row.status === 'rejected' || row.status === 'closed') && row.createBy === userId) return 'reapply'
  return 'profile'
}

/** 未外露的操作进三点 */
function connMenuItems(row) {
  const primary = connPrimaryAction(row)
  const items = []
  if (primary !== 'profile') items.push('profile')
  if (row.status === 'accepted' && primary !== 'contact') items.push('contact')
  if (getMessage(row)) items.push('viewMessage')
  return items
}

// 留言详情弹窗
const messageVisible = ref(false)
const messageContent = ref('')

function openFullMessage(row) {
  const msg = getMessage(row)
  if (!msg) return
  messageContent.value = msg
  messageVisible.value = true
}

// ---- 列表 ----
const loading = ref(true)
const list = ref([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const searchForm = reactive({ keyword: '', status: '', role: lockedRole.value })

/** 记录哪些对接已有活跃询盘：{ connectionId: inquiryId } */
const activeInquiryMap = ref({})

async function fetchList() {
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize: pageSize.value }
    if (searchForm.status) params.status = searchForm.status
    if (searchForm.keyword) params.keyword = searchForm.keyword
    if (searchForm.role) params.role = searchForm.role
    const { data } = await listConnections(params)
    list.value = data?.records || []
    total.value = data?.total || 0
    // 批量查哪些 accepted 对接已有活跃询盘
    const acceptedConnIds = list.value.filter(r => r.status === 'accepted' && isBuyer(r)).map(r => r.id)
    if (acceptedConnIds.length > 0) {
      const res = await checkActiveInquiries(acceptedConnIds)
      activeInquiryMap.value = res?.data || res || {}
    }
  } catch { list.value = [] } finally { loading.value = false }
}

function handleSearch() { pageNum.value = 1; fetchList() }
function handleReset() {
  searchForm.keyword = ''
  searchForm.status = ''
  searchForm.role = isDual.value ? '' : lockedRole.value
  handleSearch()
}

// ---- 接受 ----
async function handleAccept(row) {
  const otherName = isBuyer(row) ? (row.supplierCompanyName || '对方') : (row.buyerCompanyName || '对方')
  try {
    await ElMessageBox.confirm(
      `<div style="white-space:nowrap;margin-bottom:4px">确认接受 <span class="highlight-name">${otherName}</span> 的对接意向吗？</div><div>接受后双方可查看彼此联系方式，进一步沟通合作。</div>`,
      '接受对接',
      { confirmButtonText: '确认接受', cancelButtonText: '取消', type: 'success', dangerouslyUseHTMLString: true, customClass: 'confirmDdMsgBox' }
    )
  } catch { return }
  try {
    await acceptConnection(row.id)
    ElMessage.success('已接受对接')
    fetchList()
  } catch { }
}

// ---- 拒绝 ----
const rejectVisible = ref(false)
const rejectTarget = ref(null)
const rejectReason = ref('')
const submitting = ref(false)

function handleReject(row) {
  rejectTarget.value = row
  rejectReason.value = ''
  rejectVisible.value = true
}

// ---- 重新申请 ----
const reapplyVisible = ref(false)
const reapplyTarget = ref(null)
const reapplyMessage = ref('')
const reapplying = ref(false)

function handleReapply(row) {
  reapplyTarget.value = row
  reapplyMessage.value = ''
  reapplyVisible.value = true
}

async function doReapply() {
  reapplying.value = true
  try {
    const row = reapplyTarget.value
    if (isBuyer(row)) {
      // 采购方重新发起 → 调 createConnection
      await createConnection({
        requirementId: row.requirementId,
        supplierTenantId: row.supplierTenantId,
        message: reapplyMessage.value,
      })
    } else {
      // 供应方重新申请 → 调 applyRequirement
      await applyRequirement(row.requirementId, reapplyMessage.value)
    }
    ElMessage.success('已重新提交申请')
    reapplyVisible.value = false
    fetchList()
  } catch { } finally { reapplying.value = false }
}

// ---- 取消对接 ----
async function handleClose(row) {
  const otherName = isBuyer(row) ? (row.supplierCompanyName || '对方') : (row.buyerCompanyName || '对方')
  try {
    await ElMessageBox.confirm(
      `<div style="white-space:nowrap;margin-bottom:4px">确认取消对 <span class="highlight-name">${otherName}</span> 的对接意向吗？</div><div>取消后如需再次对接需重新发起。</div>`,
      '取消对接',
      { confirmButtonText: '确认取消', cancelButtonText: '返回', type: 'warning', dangerouslyUseHTMLString: true, customClass: 'confirmDdMsgBox' }
    )
  } catch { return }
  try {
    await closeConnection(row.id)
    ElMessage.success('已取消对接')
    fetchList()
  } catch { }
}

// ---- 查看画像 ----
const profileVisible = ref(false)
const profileData = ref(null)
const profileViewMode = ref('supplier')

async function showProfile(row) {
  // 当前用户是买方 → 看的是供应商；当前用户是供应商 → 看的是采购商
  profileViewMode.value = isBuyer(row) ? 'supplier' : 'buyer'
  const otherTenantId = isBuyer(row) ? row.supplierTenantId : row.buyerTenantId
  profileData.value = null
  profileVisible.value = true
  try {
    const { data } = await getPublicProfile(otherTenantId)
    profileData.value = data
  } catch { profileData.value = null }
}

// ---- 查看联系方式 ----
const contactVisible = ref(false)
const contactTarget = ref(null)

function showContact(row) {
  contactTarget.value = row
  contactVisible.value = true
}

/** 从已接受的对接桥接到询盘管理 — 预填供应商和需求信息 */
function handleCreateInquiry(row) {
  const supplierTenantId = isBuyer(row) ? row.supplierTenantId : row.buyerTenantId
  const supplierName = isBuyer(row) ? row.supplierCompanyName : row.buyerCompanyName
  router.push({
    name: 'InquiryManagement',
    query: {
      connectionId: row.id,
      requirementId: row.requirementId,
      supplierTenantId,
      supplierName,
      requirementTitle: row.requirementTitle
    }
  })
}

function handleConnCmd(cmd, row) {
  if (cmd === 'profile') showProfile(row)
  else if (cmd === 'contact') showContact(row)
  else if (cmd === 'viewMessage') openFullMessage(row)
  else if (cmd === 'accept') handleAccept(row)
  else if (cmd === 'reject') handleReject(row)
}

async function doReject() {
  submitting.value = true
  try {
    await rejectConnection(rejectTarget.value.id, rejectReason.value)
    ElMessage.success('已拒绝对接')
    rejectVisible.value = false
    fetchList()
  } catch { } finally { submitting.value = false }
}

onMounted(() => fetchList())
</script>

<style>
.reject-dialog { width: auto !important; max-width: 480px; }
.reapply-dialog { width: auto !important; max-width: 480px; }
.message-dialog { width: auto !important; max-width: 560px; }
.contact-dialog { width: auto !important; max-width: 460px; }
.profile-dialog { width: auto !important; max-width: 640px; }
.preview-dialog { width: 90% !important; max-width: 1100px; }
</style>
