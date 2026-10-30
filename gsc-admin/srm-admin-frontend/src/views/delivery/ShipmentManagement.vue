<template>
  <div>
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" placeholder="合同编号/标题" clearable style="width:220px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" clearable placeholder="全部" style="width:140px" @change="handleSearch">
            <el-option v-for="opt in SHIPMENT_PLAN_STATUS_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-loading="loading" class="content-card">
      <div class="table-toolbar">
        <span class="text-base font-semibold text-gray-700">发货管理</span>
      </div>

      <el-table :data="list" stripe border :span-method="spanMethod">
        <el-table-column type="index" :index="(idx) => (pageNum - 1) * pageSize + idx + 1" label="ID" width="55" align="center" />
        <el-table-column prop="contractNo" label="合同编号" min-width="200" show-overflow-tooltip>
          <template #default="{ row }"><EmptyText :value="row.contractNo" /></template>
        </el-table-column>
        <el-table-column prop="contractTitle" label="合同标题" min-width="180" show-overflow-tooltip>
          <template #default="{ row }"><EmptyText :value="row.contractTitle" /></template>
        </el-table-column>
        <el-table-column label="采购方" min-width="200" show-overflow-tooltip>
          <template #default="{ row }"><EmptyText :value="row.buyerCompanyName" /></template>
        </el-table-column>
        <el-table-column label="批次" min-width="80">
          <template #default="{ row }">
            第 {{ row.batchNo || 1 }} 批
            <el-tag v-if="row.batchPlanId == null" size="small" type="warning" style="margin-left:4px">补</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发货单号" min-width="180" show-overflow-tooltip>
          <template #default="{ row }"><EmptyText :value="row.deliveryNo" /></template>
        </el-table-column>
        <el-table-column label="应发数量" min-width="100">
          <template #default="{ row }"><EmptyText :value="row.planQty" /></template>
        </el-table-column>
        <el-table-column label="交货日期" min-width="120">
          <template #default="{ row }">
            <span :style="isUnshippedOverdue(row) ? 'color:#f56c6c;font-weight:600' : ''">{{ formatDate(row.planDeliveryDate) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="110">
          <template #default="{ row }">
            <el-tag v-if="row.deliveryStatus" :type="DELIVERY_STATUS_TAG[row.deliveryStatus] || 'info'">{{ DELIVERY_STATUS_MAP[row.deliveryStatus] || row.deliveryStatus }}</el-tag>
            <el-tag v-else type="primary">待发货</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-if="!row.deliveryStatus" link type="primary" size="small" @click="openShip(row)">发货</el-button>
            <el-button v-if="row.deliveryStatus === 'returned'" link type="warning" size="small" @click="openShip(row)">重新发货</el-button>
            <el-button v-if="row.deliveryStatus === 'reship_pending'" link type="warning" size="small" @click="openShip(row)">补发</el-button>
            <el-button v-if="row.deliveryStatus === 'shipped'" link type="primary" size="small" @click="openCorrect(row)">更正运单</el-button>
            <el-button v-if="row.deliveryStatus === 'shipped'" link type="primary" size="small" @click="openLogisticsEvent(row)">物流节点</el-button>
            <template v-if="row.deliveryStatus === 'return_pending'">
              <el-button link type="success" size="small" @click="handleConfirmReturn(row)">确认退回</el-button>
              <el-dropdown trigger="click" @command="(cmd) => handleReturnPendingCmd(cmd, row)" style="vertical-align:middle">
                <el-icon :size="16" style="cursor:pointer;color:#409eff;margin-left:4px;margin-top:5px;transform:rotate(90deg)">
                  <MoreFilled />
                </el-icon>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="dispute">提出异议</el-dropdown-item>
                    <el-dropdown-item command="detail" divided>详情</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
            <el-button v-if="row.deliveryStatus === 'return_shipping'" link type="success" size="small" @click="handleConfirmReceipt(row)">确认收货</el-button>
            <!-- 承诺交期：供应商对未闭环交付单随时可提交（页面数据本身是供应商视角，采购方列表为空） -->
            <el-button v-if="row.deliveryId && !['accepted', 'returned'].includes(row.deliveryStatus)" link type="warning" size="small" @click="openPromise(row)">承诺交期</el-button>
            <el-button v-if="row.deliveryId && row.deliveryStatus !== 'return_pending'" link type="primary" size="small" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty v-if="!loading" :description="searchForm.keyword ? '未找到匹配的记录' : '暂无发货排期'" />
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

    <!-- 发货 Dialog -->
    <el-dialog v-model="shipVisible" :title="shipTarget?.deliveryStatus === 'reship_pending' ? '补发' : shipTarget?.deliveryStatus === 'returned' ? '重新发货' : '发货'" :close-on-click-modal="false" class="ship-dialog">
      <el-alert v-if="shipTarget?.deliveryStatus === 'returned' && shipTarget?.returnReason" type="error" :closable="false" show-icon style="margin-bottom:12px" :title="'上次退回：' + shipTarget.returnReason" />
      <el-form ref="shipFormRef" :model="shipForm" :rules="shipRules" label-width="85px" label-position="right">
        <el-form-item label="采购方">
          <el-input :model-value="shipTarget?.buyerCompanyName || '无'" disabled />
        </el-form-item>
        <el-form-item label="本批应发">
          <el-input :model-value="shipBaseQty != null ? String(shipBaseQty) : '无'" disabled>
            <template #prepend>第 {{ shipTarget?.batchNo || 1 }} 批</template>
          </el-input>
        </el-form-item>
        <el-form-item label="交货地点">
          <el-input :model-value="shipTarget?.deliveryPlace || '无'" disabled />
        </el-form-item>
        <el-form-item label="合同交期">
          <el-input :model-value="formatDate(shipTarget?.planDeliveryDate)" disabled>
            <template v-if="isDateOverdue(shipTarget?.planDeliveryDate)" #append>
              <span style="color:#f56c6c">已过期</span>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="交付条款">
          <el-input :model-value="shipTarget?.deliveryTerms || '无'" disabled />
        </el-form-item>
        <el-form-item label="发货数量" prop="shipQty">
          <el-input-number v-if="shipTarget?.deliveryStatus === 'returned'" v-model="shipForm.shipQty" :min="1" :max="shipBaseQty || 1" :controls="false" style="width:100%" />
          <el-input v-else :model-value="String(shipForm.shipQty)" disabled>
            <template #append>{{ shipTarget?.deliveryStatus === 'reship_pending' ? '按残次数量补发' : '按计划数量发货' }}</template>
          </el-input>
        </el-form-item>
        <el-form-item label="发货日期" prop="shipDate">
          <el-date-picker v-model="shipForm.shipDate" type="date" value-format="YYYY-MM-DD" placeholder="选择发货日期" style="width:100%" />
        </el-form-item>
        <el-form-item label="物流公司" prop="shipCompany">
          <el-input v-model="shipForm.shipCompany" placeholder="如：顺丰速运" />
        </el-form-item>
        <el-form-item label="运单号" prop="shipTrackingNo">
          <el-input v-model="shipForm.shipTrackingNo" placeholder="如：SF1234567890" />
        </el-form-item>
        <el-form-item label="发货凭证" prop="shipAttachmentKey">
          <div>
            <el-upload
              :show-file-list="false"
              :http-request="handleShipUpload"
              :before-upload="beforeUpload"
              accept=".pdf,.jpg,.jpeg,.png,.webp"
            >
              <el-button type="primary" plain :loading="shipUploading">
                <el-icon><Plus /></el-icon> {{ shipForm.shipAttachmentKey ? '重新上传' : '上传凭证' }}
              </el-button>
            </el-upload>
            <div v-if="shipForm.shipAttachmentKey" style="margin-top:6px;color:#67c23a;font-size:13px">{{ getFileName(shipForm.shipAttachmentKey) }}</div>
            <div v-else class="el-upload__tip" style="margin-top:4px">装箱单/运单照片，支持 PDF/图片，不超过 10MB</div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="shipVisible = false">取消</el-button>
        <el-button type="primary" :loading="shipSubmitting" @click="handleConfirmShip">确认发货</el-button>
      </template>
    </el-dialog>

    <!-- 更正运单 Dialog -->
    <el-dialog v-model="correctVisible" title="更正运单" :close-on-click-modal="false" class="correct-dialog">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px" title="更正会写入履约时间线，不会覆盖历史记录" />
      <el-form ref="correctFormRef" :model="correctForm" :rules="correctRules" label-width="85px" label-position="right">
        <el-form-item label="物流公司" prop="shipCompany">
          <el-input v-model="correctForm.shipCompany" />
        </el-form-item>
        <el-form-item label="运单号" prop="shipTrackingNo">
          <el-input v-model="correctForm.shipTrackingNo" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="correctVisible = false">取消</el-button>
        <el-button type="primary" :loading="correctSubmitting" @click="handleConfirmCorrect">确认更正</el-button>
      </template>
    </el-dialog>

    <!-- 录入物流节点（共享组件，ETA 默认沿用排期交期） -->
    <LogisticsEventDialog
      v-model="logisticsEventVisible"
      :delivery-id="logisticsEventTarget?.deliveryId"
      :default-eta="logisticsEventTarget?.planDeliveryDate || ''"
      @success="fetchList"
    />

    <!-- 提交承诺交期 Dialog（供应商） -->
    <el-dialog v-model="promiseVisible" title="提交承诺交期" :close-on-click-modal="false" class="correct-dialog">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px" title="提交后由采购方确认，确认后按新承诺交期考核" />
      <el-form ref="promiseFormRef" :model="promiseForm" :rules="promiseRules" label-width="85px" label-position="right">
        <el-form-item label="原交期">
          <el-input :model-value="formatDate(promiseTarget?.planDeliveryDate) || '-'" disabled />
        </el-form-item>
        <el-form-item label="承诺交期" prop="promisedDate">
          <el-date-picker v-model="promiseForm.promisedDate" type="date" value-format="YYYY-MM-DD" style="width:100%" placeholder="选择新承诺交期" />
        </el-form-item>
        <el-form-item label="原因" prop="reason">
          <el-input v-model="promiseForm.reason" type="textarea" :rows="2" placeholder="如：原材料延迟、产能紧张（必填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="promiseVisible = false">取消</el-button>
        <el-button type="primary" :loading="promiseSubmitting" @click="handleConfirmPromise">提交</el-button>
      </template>
    </el-dialog>

    <!-- 提出异议 Dialog -->
    <el-dialog v-model="disputeVisible" title="提出异议" :close-on-click-modal="false" class="correct-dialog">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px" title="提出异议后，交付单将回到待验收，由采购商重新判断" />
      <el-form ref="disputeFormRef" :model="disputeForm" label-width="85px" label-position="right">
        <el-form-item label="异议原因">
          <el-input v-model="disputeForm.remark" type="textarea" :rows="3" placeholder="请说明对验收不合格判定的异议理由（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="disputeVisible = false">取消</el-button>
        <el-button type="primary" :loading="disputeSubmitting" @click="handleConfirmDispute">提交异议</el-button>
      </template>
    </el-dialog>

    <DeliveryDetailDialog v-model="detailVisible" :delivery-id="detailId" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, MoreFilled } from '@element-plus/icons-vue'
import { listShipmentPlan, shipBatch, shipDelivery, correctTracking, confirmReturn, disputeReturn, confirmReturnReceipt, submitPromiseDate } from '@/api/delivery'
import { uploadFile } from '@/api/upload'
import { DELIVERY_STATUS_MAP, DELIVERY_STATUS_TAG, SHIPMENT_PLAN_STATUS_OPTIONS } from '@/constants/delivery'
import { PAGE_SIZE_OPTIONS } from '@/constants'
import { composeFileValue, getFileName } from '@/utils/file'
import { formatDate } from '@/utils/datetime'
import DeliveryDetailDialog from './DeliveryDetailDialog.vue'
import LogisticsEventDialog from '@/components/LogisticsEventDialog.vue'

const route = useRoute()

const list = ref([])
const loading = ref(true)
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const searchForm = reactive({ keyword: '', status: '' })

const shipVisible = ref(false)
const shipSubmitting = ref(false)
const shipUploading = ref(false)
const shipFormRef = ref(null)
const shipForm = reactive({
  shipCompany: '',
  shipTrackingNo: '',
  shipQty: 1,
  shipDate: '',
  shipAttachmentKey: ''
})
const shipRules = {
  shipQty: [{ required: true, message: '请输入发货数量', trigger: 'change' }],
  shipDate: [{ required: true, message: '请选择发货日期', trigger: 'change' }],
  shipCompany: [{ required: true, message: '请输入物流公司', trigger: 'blur' }],
  shipTrackingNo: [{ required: true, message: '请输入运单号', trigger: 'blur' }],
  shipAttachmentKey: [{ required: true, message: '请上传发货凭证', trigger: 'change' }]
}
const shipTarget = ref(null)
const shipBaseQty = computed(() => {
  if (!shipTarget.value) return null
  if (shipTarget.value.deliveryStatus === 'returned') {
    return shipTarget.value.deliveryQty != null ? shipTarget.value.deliveryQty : shipTarget.value.planQty
  }
  return shipTarget.value.planQty
})

const correctVisible = ref(false)
const correctSubmitting = ref(false)
const correctFormRef = ref(null)
const correctForm = reactive({ shipCompany: '', shipTrackingNo: '' })
const correctRules = {
  shipCompany: [{ required: true, message: '请输入物流公司', trigger: 'blur' }],
  shipTrackingNo: [{ required: true, message: '请输入运单号', trigger: 'blur' }]
}
let correctTarget = null

const disputeVisible = ref(false)
const disputeSubmitting = ref(false)
const disputeFormRef = ref(null)
const disputeForm = reactive({ remark: '' })
let disputeTarget = null

const detailVisible = ref(false)
const detailId = ref(null)

const logisticsEventVisible = ref(false)
const logisticsEventTarget = ref(null)

// 提交承诺交期（供应商）
const promiseVisible = ref(false)
const promiseSubmitting = ref(false)
const promiseFormRef = ref(null)
const promiseTarget = ref(null)
const promiseForm = reactive({ promisedDate: '', reason: '' })
const promiseRules = {
  promisedDate: [{ required: true, message: '请选择承诺交期', trigger: 'change' }],
  reason: [{ required: true, message: '请填写原因', trigger: 'blur' }]
}

onMounted(() => {
  fetchList()
  const qDeliveryId = route.query.deliveryId
  if (qDeliveryId) {
    detailId.value = Number(qDeliveryId)
    detailVisible.value = true
  }
})

function todayStr() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function isDateOverdue(dateStr) {
  if (!dateStr) return false
  return String(dateStr).slice(0, 10) < todayStr()
}

function isUnshippedOverdue(row) {
  return (!row.deliveryStatus || row.deliveryStatus === 'returned') && isDateOverdue(row.planDeliveryDate)
}

/** 合并同一合同的合同级列（ID/合同编号/合同标题/采购方），批次级列保持独立；String() 兼容 Long→String */
function spanMethod({ row, rowIndex, columnIndex }) {
  if (columnIndex <= 3) {
    const prev = rowIndex > 0 ? list.value[rowIndex - 1] : null
    if (prev && String(prev.contractId) === String(row.contractId)) {
      return { rowspan: 0, colspan: 0 }
    }
    let span = 1
    for (let i = rowIndex + 1; i < list.value.length; i++) {
      if (String(list.value[i].contractId) === String(row.contractId)) span++
      else break
    }
    return { rowspan: span, colspan: 1 }
  }
}

async function fetchList() {
  loading.value = true
  try {
    const res = await listShipmentPlan({ pageNum: pageNum.value, pageSize: pageSize.value, ...searchForm })
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

function openDetail(row) {
  detailId.value = row.deliveryId
  detailVisible.value = true
}

function openShip(row) {
  shipTarget.value = row
  shipForm.shipCompany = ''
  shipForm.shipTrackingNo = ''
  const baseQty = row.deliveryStatus === 'returned'
    ? (row.deliveryQty != null ? row.deliveryQty : row.planQty)
    : row.planQty
  shipForm.shipQty = baseQty != null ? baseQty : 1
  shipForm.shipDate = todayStr()
  shipForm.shipAttachmentKey = ''
  shipVisible.value = true
  nextTick(() => shipFormRef.value?.clearValidate())
}

function beforeUpload(file) {
  if (file.size > 10 * 1024 * 1024) { ElMessage.error('文件不能超过 10MB'); return false }
  return true
}

async function handleShipUpload({ file }) {
  shipUploading.value = true
  try {
    const { data } = await uploadFile(file, 'delivery')
    // 只存 oss_key：签名 URL 会过期，存库必然变死链；后端返回 VO 时按 key 现签
    shipForm.shipAttachmentKey = data?.key || ''
  } catch { /* ignore */ }
  finally { shipUploading.value = false }
}

async function handleConfirmShip() {
  try { await shipFormRef.value.validate() } catch { return }
  if (!shipTarget.value) return
  const isReship = shipTarget.value.deliveryStatus === 'reship_pending'
  const isReturned = shipTarget.value.deliveryStatus === 'returned'
  shipSubmitting.value = true
  try {
    if (isReturned || isReship) {
      await shipDelivery(shipTarget.value.deliveryId, { ...shipForm })
    } else {
      await shipBatch(shipTarget.value.contractId, shipTarget.value.batchPlanId, { ...shipForm })
    }
    ElMessage.success(isReship ? '已补发' : '已发货')
    shipVisible.value = false
    fetchList()
  } catch { /* ignore */ }
  finally { shipSubmitting.value = false }
}

function openCorrect(row) {
  correctTarget = row
  correctForm.shipCompany = row.shipCompany || ''
  correctForm.shipTrackingNo = row.shipTrackingNo || ''
  correctVisible.value = true
}

async function handleConfirmCorrect() {
  try { await correctFormRef.value.validate() } catch { return }
  if (!correctTarget) return
  correctSubmitting.value = true
  try {
    await correctTracking(correctTarget.deliveryId, { ...correctForm })
    ElMessage.success('运单已更正')
    correctVisible.value = false
    fetchList()
  } catch { /* ignore */ }
  finally { correctSubmitting.value = false }
}

async function handleConfirmReturn(row) {
  try {
    await ElMessageBox.confirm(
      `<div style="white-space:nowrap;margin-bottom:4px">确认交付单 <span class="highlight-name">${row.deliveryNo}</span> 退回吗？</div><div>确认后采购商将填写退货物流。</div>`,
      '确认退回',
      { confirmButtonText: '确认退回', cancelButtonText: '取消', type: 'warning', dangerouslyUseHTMLString: true, customClass: 'confirmDdMsgBox' }
    )
  } catch { return }
  try {
    await confirmReturn(row.deliveryId)
    ElMessage.success('已确认退回')
    fetchList()
  } catch { /* ignore */ }
}

async function handleConfirmReceipt(row) {
  try {
    await ElMessageBox.confirm(
      `<div style="white-space:nowrap;margin-bottom:4px">确认已收到交付单 <span class="highlight-name">${row.deliveryNo}</span> 的退货吗？</div><div>确认后可重新发货。</div>`,
      '确认收货',
      { confirmButtonText: '确认收货', cancelButtonText: '取消', type: 'warning', dangerouslyUseHTMLString: true, customClass: 'confirmDdMsgBox' }
    )
  } catch { return }
  try {
    await confirmReturnReceipt(row.deliveryId)
    ElMessage.success('已确认收货')
    fetchList()
  } catch { /* ignore */ }
}

function handleReturnPendingCmd(cmd, row) {
  if (cmd === 'dispute') openDispute(row)
  else if (cmd === 'detail') openDetail(row)
}

function openDispute(row) {
  disputeTarget = row
  disputeForm.remark = ''
  disputeVisible.value = true
}

async function handleConfirmDispute() {
  if (!disputeTarget) return
  disputeSubmitting.value = true
  try {
    await disputeReturn(disputeTarget.deliveryId, disputeForm.remark || null)
    ElMessage.success('已提交异议')
    disputeVisible.value = false
    fetchList()
  } catch { /* ignore */ }
  finally { disputeSubmitting.value = false }
}

function openLogisticsEvent(row) {
  logisticsEventTarget.value = row
  logisticsEventVisible.value = true
}

function openPromise(row) {
  promiseTarget.value = row
  Object.assign(promiseForm, { promisedDate: '', reason: '' })
  promiseVisible.value = true
  nextTick(() => promiseFormRef.value?.clearValidate())
}

async function handleConfirmPromise() {
  try { await promiseFormRef.value.validate() } catch { return }
  if (!promiseTarget.value?.deliveryId) return
  promiseSubmitting.value = true
  try {
    await submitPromiseDate(promiseTarget.value.deliveryId, {
      promisedDate: promiseForm.promisedDate,
      reason: promiseForm.reason
    })
    ElMessage.success('承诺交期已提交，等待采购方确认')
    promiseVisible.value = false
    fetchList()
  } catch { /* ignore */ }
  finally { promiseSubmitting.value = false }
}
</script>

<style scoped>
.table-toolbar {
  justify-content: space-between;
}
.ship-qty-input :deep(.el-input__inner) {
  text-align: left;
}
.split-hint {
  margin-top: 4px;
  color: #e6a23c;
  font-size: 12px;
  line-height: 18px;
}
.form-tip {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
  line-height: 18px;
}
</style>

<style>
.ship-dialog { width: auto !important; max-width: 520px; }
.ship-dialog .el-form-item__label { white-space: nowrap; }
.ship-dialog .el-form-item.is-required .el-form-item__label::before { margin-right: 2px; }
.correct-dialog { width: auto !important; max-width: 460px; }
.correct-dialog .el-form-item__label { white-space: nowrap; }
.correct-dialog .el-form-item.is-required .el-form-item__label::before { margin-right: 2px; }
</style>
