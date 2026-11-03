<template>
  <div>
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" placeholder="交付单号/运单号" clearable style="width:220px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" clearable placeholder="全部" style="width:160px" @change="handleSearch">
            <el-option v-for="opt in ACCEPTANCE_STATUS_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
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
        <span class="text-base font-semibold text-gray-700">验收管理</span>
      </div>

      <el-table :data="list" stripe border>
        <IdColumn :page-num="pageNum" :page-size="pageSize" />
        <el-table-column prop="deliveryNo" label="交付单号" min-width="200" show-overflow-tooltip />
        <el-table-column label="批次" min-width="80">
          <template #default="{ row }">
            第 {{ row.batchNo || 1 }} 批
            <el-tag v-if="row.reshipFrom" size="small" type="warning" style="margin-left:4px">补</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="contractNo" label="合同编号" min-width="200" show-overflow-tooltip>
          <template #default="{ row }"><EmptyText :value="row.contractNo" /></template>
        </el-table-column>
        <el-table-column prop="contractTitle" label="合同标题" min-width="180" show-overflow-tooltip>
          <template #default="{ row }"><EmptyText :value="row.contractTitle" /></template>
        </el-table-column>
        <el-table-column label="供应方" min-width="150" show-overflow-tooltip>
          <template #default="{ row }"><EmptyText :value="row.supplierCompanyName" /></template>
        </el-table-column>
        <el-table-column label="数量" min-width="90">
          <template #default="{ row }"><EmptyText :value="row.quantity" /></template>
        </el-table-column>
        <el-table-column label="金额" min-width="120">
          <template #default="{ row }"><EmptyText :value="row.amount" /></template>
        </el-table-column>
        <el-table-column label="币种" min-width="80">
          <template #default="{ row }"><EmptyText :value="row.currency" /></template>
        </el-table-column>
        <el-table-column label="状态" min-width="110">
          <template #default="{ row }">
            <el-tag :type="DELIVERY_STATUS_TAG[row.status] || 'info'">{{ DELIVERY_STATUS_MAP[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="120" fixed="right">
          <template #default="{ row }">
            <el-dropdown v-if="row.status === 'received'" trigger="click" @command="(cmd) => handleAcceptCmd(cmd, row)" style="vertical-align:middle">
              <el-button link type="success" size="small">验收</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="accept">验收合格</el-dropdown-item>
                  <el-dropdown-item v-if="canPartial(row)" command="partial">部分残次补发</el-dropdown-item>
                  <el-dropdown-item command="return" divided style="color:#f56c6c">验收不合格</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button v-if="row.status === 'return_confirmed'" link type="warning" size="small" @click="openLogistics(row)">退货</el-button>
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty v-if="!loading" :description="searchForm.keyword ? '未找到匹配的交付单' : '暂无交付单'" />
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

    <!-- 验收不合格 Dialog -->
    <el-dialog v-model="returnVisible" title="验收不合格" :close-on-click-modal="false" class="return-dialog">
      <el-form ref="returnFormRef" :model="returnForm" :rules="returnRules" label-width="85px" label-position="right">
        <el-form-item label="退回原因" prop="reason">
          <el-input v-model="returnForm.reason" type="textarea" :rows="3" placeholder="请说明不合格原因" />
        </el-form-item>
        <el-form-item label="退回凭证" prop="returnAttachmentKey">
          <div>
            <el-upload
              :show-file-list="false"
              :http-request="handleReturnUpload"
              :before-upload="beforeUpload"
              accept=".pdf,.jpg,.jpeg,.png,.webp"
            >
              <el-button type="primary" plain :loading="returnUploading">
                <el-icon><Plus /></el-icon> {{ returnForm.returnAttachmentKey ? '重新上传' : '上传凭证' }}
              </el-button>
            </el-upload>
            <div v-if="returnForm.returnAttachmentKey" style="margin-top:6px;color:#67c23a;font-size:13px">{{ getFileName(returnForm.returnAttachmentKey) }}</div>
            <div v-else class="el-upload__tip" style="margin-top:4px">不合格照片/检测单，支持 PDF/图片，不超过 10MB</div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="returnVisible = false">取消</el-button>
        <el-button type="danger" :loading="returnSubmitting" @click="handleConfirmReturn">提交</el-button>
      </template>
    </el-dialog>

    <!-- 部分残次补发 Dialog -->
    <el-dialog v-model="partialVisible" title="部分残次补发" :close-on-click-modal="false" class="return-dialog">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px" :title="`发货 ${partialTarget?.shipQty ?? partialTarget?.quantity ?? 0} 件、实收 ${partialTarget?.receiveQty ?? 0} 件，差额 ${partialMaxQty} 件将生成补发申请`" />
      <el-form ref="partialFormRef" :model="partialForm" :rules="partialRules" label-width="85px" label-position="right">
        <el-form-item label="残次数量" prop="defectiveQty">
          <el-input-number v-model="partialForm.defectiveQty" :min="1" :max="partialMaxQty" :controls="false" style="width:100%" />
        </el-form-item>
        <el-form-item label="残次原因" prop="reason">
          <el-input v-model="partialForm.reason" type="textarea" :rows="3" placeholder="请说明残次原因" />
        </el-form-item>
        <el-form-item label="残次凭证" prop="returnAttachmentKey">
          <div>
            <el-upload
              :show-file-list="false"
              :http-request="handlePartialUpload"
              :before-upload="beforeUpload"
              accept=".pdf,.jpg,.jpeg,.png,.webp"
            >
              <el-button type="primary" plain :loading="partialUploading">
                <el-icon><Plus /></el-icon> {{ partialForm.returnAttachmentKey ? '重新上传' : '上传凭证' }}
              </el-button>
            </el-upload>
            <div v-if="partialForm.returnAttachmentKey" style="margin-top:6px;color:#67c23a;font-size:13px">{{ getFileName(partialForm.returnAttachmentKey) }}</div>
            <div v-else class="el-upload__tip" style="margin-top:4px">残次品照片/检测单，支持 PDF/图片，不超过 10MB</div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="partialVisible = false">取消</el-button>
        <el-button type="primary" :loading="partialSubmitting" @click="handleConfirmPartial">提交补发申请</el-button>
      </template>
    </el-dialog>

    <!-- 填写退货物流 Dialog -->
    <el-dialog v-model="logisticsVisible" title="退货" :close-on-click-modal="false" class="return-dialog">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px" title="供应商已确认退回，请填写退货物流信息" />
      <el-form ref="logisticsFormRef" :model="logisticsForm" :rules="logisticsRules" label-width="85px" label-position="right">
        <el-form-item label="退货物流" prop="returnShipCompany">
          <el-input v-model="logisticsForm.returnShipCompany" placeholder="如：顺丰速运" />
        </el-form-item>
        <el-form-item label="退货单号" prop="returnShipTrackingNo">
          <el-input v-model="logisticsForm.returnShipTrackingNo" placeholder="如：SF1234567890" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="logisticsVisible = false">取消</el-button>
        <el-button type="primary" :loading="logisticsSubmitting" @click="handleConfirmLogistics">确认退回</el-button>
      </template>
    </el-dialog>

    <DeliveryDetailDialog v-model="detailVisible" :delivery-id="detailId" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { listDeliveries, acceptDelivery, rejectDelivery, fillReturnLogistics, partialReject } from '@/api/delivery'
import { uploadFile } from '@/api/upload'
import { DELIVERY_STATUS_MAP, DELIVERY_STATUS_TAG, ACCEPTANCE_STATUS_OPTIONS } from '@/constants/delivery'
import { PAGE_SIZE_OPTIONS } from '@/constants'
import { composeFileValue, getFileName } from '@/utils/file'
import IdColumn from '@/components/IdColumn.vue'
import DeliveryDetailDialog from './DeliveryDetailDialog.vue'

const route = useRoute()

const list = ref([])
const loading = ref(true)
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const searchForm = reactive({ keyword: '', status: '' })

const returnVisible = ref(false)
const returnSubmitting = ref(false)
const returnUploading = ref(false)
const returnFormRef = ref(null)
const returnForm = reactive({ reason: '', returnAttachmentKey: '' })
const returnRules = {
  reason: [{ required: true, message: '退回原因不能为空', trigger: 'blur' }],
  returnAttachmentKey: [{ required: true, message: '请上传退回凭证', trigger: 'change' }]
}
let returnTarget = null

const partialVisible = ref(false)
const partialSubmitting = ref(false)
const partialUploading = ref(false)
const partialFormRef = ref(null)
const partialForm = reactive({ defectiveQty: 1, reason: '', returnAttachmentKey: '' })
const partialRules = {
  defectiveQty: [{ required: true, message: '请输入残次数量', trigger: 'change' }],
  reason: [{ required: true, message: '残次原因不能为空', trigger: 'blur' }],
  returnAttachmentKey: [{ required: true, message: '请上传残次凭证', trigger: 'change' }]
}
let partialTarget = null
const partialMaxQty = ref(1)

const logisticsVisible = ref(false)
const logisticsSubmitting = ref(false)
const logisticsFormRef = ref(null)
const logisticsForm = reactive({ returnShipCompany: '', returnShipTrackingNo: '' })
const logisticsRules = {
  returnShipCompany: [{ required: true, message: '请填写退货物流公司', trigger: 'blur' }],
  returnShipTrackingNo: [{ required: true, message: '请填写退货单号', trigger: 'blur' }]
}
let logisticsTarget = null

const detailVisible = ref(false)
const detailId = ref(null)

onMounted(() => {
  fetchList()
  const qDeliveryId = route.query.deliveryId
  if (qDeliveryId) {
    detailId.value = Number(qDeliveryId)
    detailVisible.value = true
  }
})

async function fetchList() {
  loading.value = true
  try {
    const res = await listDeliveries({ pageNum: pageNum.value, pageSize: pageSize.value, role: 'buyer', ...searchForm })
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
  detailId.value = row.id
  detailVisible.value = true
}

function handleAcceptCmd(cmd, row) {
  if (cmd === 'accept') handleAccept(row)
  else if (cmd === 'partial') openPartial(row)
  else if (cmd === 'return') openReturn(row)
}

async function handleAccept(row) {
  try {
    await ElMessageBox.confirm(
      `<div style="white-space:nowrap;margin-bottom:4px">确认交付单 <span class="highlight-name">${row.deliveryNo}</span> 验收合格吗？</div><div>验收合格后合同将自动标记为已完成。</div>`,
      '验收合格',
      { confirmButtonText: '确认验收', cancelButtonText: '取消', type: 'warning', dangerouslyUseHTMLString: true, customClass: 'confirmDdMsgBox' }
    )
  } catch { return }
  try {
    await acceptDelivery(row.id)
    ElMessage.success('验收合格')
    fetchList()
  } catch { /* ignore */ }
}

function openReturn(row) {
  returnTarget = row
  returnForm.reason = ''
  returnForm.returnAttachmentKey = ''
  returnVisible.value = true
  nextTick(() => returnFormRef.value?.clearValidate())
}

function beforeUpload(file) {
  if (file.size > 10 * 1024 * 1024) { ElMessage.error('文件不能超过 10MB'); return false }
  return true
}

async function handleReturnUpload({ file }) {
  returnUploading.value = true
  try {
    const { data } = await uploadFile(file, 'delivery')
    returnForm.returnAttachmentKey = data?.key || ''
  } catch { /* ignore */ }
  finally { returnUploading.value = false }
}

async function handleConfirmReturn() {
  try { await returnFormRef.value.validate() } catch { return }
  if (!returnTarget) return
  returnSubmitting.value = true
  try {
    await rejectDelivery(returnTarget.id, { ...returnForm })
    ElMessage.success('已提交，等待供应商确认')
    returnVisible.value = false
    fetchList()
  } catch { /* ignore */ }
  finally { returnSubmitting.value = false }
}

function canPartial(row) {
  const shipQty = row.shipQty != null ? row.shipQty : row.quantity
  const receiveQty = row.receiveQty != null ? row.receiveQty : 0
  return shipQty != null && shipQty - receiveQty >= 1
}

function openPartial(row) {
  partialTarget = row
  const shipQty = row.shipQty != null ? row.shipQty : (row.quantity != null ? row.quantity : 0)
  const receiveQty = row.receiveQty != null ? row.receiveQty : 0
  const diff = shipQty - receiveQty
  partialForm.defectiveQty = diff > 0 ? diff : 1
  partialForm.reason = ''
  partialForm.returnAttachmentKey = ''
  partialMaxQty.value = diff > 0 ? diff : 1
  partialVisible.value = true
  nextTick(() => partialFormRef.value?.clearValidate())
}

async function handlePartialUpload({ file }) {
  partialUploading.value = true
  try {
    const { data } = await uploadFile(file, 'delivery')
    partialForm.returnAttachmentKey = data?.key || ''
  } catch { /* ignore */ }
  finally { partialUploading.value = false }
}

async function handleConfirmPartial() {
  try { await partialFormRef.value.validate() } catch { return }
  if (!partialTarget) return
  partialSubmitting.value = true
  try {
    await partialReject(partialTarget.id, { ...partialForm })
    ElMessage.success('已提交补发申请')
    partialVisible.value = false
    fetchList()
  } catch { /* ignore */ }
  finally { partialSubmitting.value = false }
}

function openLogistics(row) {
  logisticsTarget = row
  logisticsForm.returnShipCompany = ''
  logisticsForm.returnShipTrackingNo = ''
  logisticsVisible.value = true
  nextTick(() => logisticsFormRef.value?.clearValidate())
}

async function handleConfirmLogistics() {
  try { await logisticsFormRef.value.validate() } catch { return }
  if (!logisticsTarget) return
  logisticsSubmitting.value = true
  try {
    await fillReturnLogistics(logisticsTarget.id, { ...logisticsForm })
    ElMessage.success('已退回供应商')
    logisticsVisible.value = false
    fetchList()
  } catch { /* ignore */ }
  finally { logisticsSubmitting.value = false }
}
</script>

<style>
.return-dialog { width: auto !important; max-width: 520px; }
.return-dialog .el-form-item__label { white-space: nowrap; }
.return-dialog .el-form-item.is-required .el-form-item__label::before { margin-right: 2px; }
</style>
