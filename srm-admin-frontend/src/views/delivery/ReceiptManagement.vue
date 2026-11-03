<template>
  <div>
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" placeholder="交付单号/运单号" clearable style="width:220px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" clearable placeholder="全部" style="width:160px" @change="handleSearch">
            <el-option v-for="opt in RECEIPT_STATUS_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
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
        <span class="text-base font-semibold text-gray-700">收货确认</span>
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
        <el-table-column label="物流" min-width="180" show-overflow-tooltip>
          <template #default="{ row }"><EmptyText :value="row.shipCompany" /></template>
        </el-table-column>
        <el-table-column label="状态" min-width="110">
          <template #default="{ row }">
            <el-tag :type="DELIVERY_STATUS_TAG[row.status] || 'info'">{{ DELIVERY_STATUS_MAP[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="120" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'shipped'" link type="primary" size="small" @click="openReceive(row)">收货</el-button>
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

    <!-- 收货 Dialog -->
    <el-dialog v-model="receiveVisible" title="确认收货" :close-on-click-modal="false" class="receipt-dialog">
      <el-form ref="receiveFormRef" :model="receiveForm" :rules="receiveRules" label-width="85px" label-position="right">
        <el-form-item label="交付单号">
          <EmptyText :value="receiveTarget?.deliveryNo" />
        </el-form-item>
        <el-form-item label="批次">
          第 {{ receiveTarget?.batchNo || 1 }} 批
        </el-form-item>
        <el-form-item label="物流">
          <EmptyText :value="receiveTarget?.shipCompany" />
        </el-form-item>
        <el-form-item label="本批应发">
          <EmptyText :value="receiveTarget?.quantity" />
        </el-form-item>
        <el-form-item label="发货数量">
          <EmptyText :value="receiveTarget?.shipQty != null ? receiveTarget.shipQty : receiveTarget?.quantity" />
        </el-form-item>
        <el-form-item label="实收数量" prop="receiveQty">
          <el-input-number v-model="receiveForm.receiveQty" :min="0" :controls="false" style="width:100%" />
        </el-form-item>
        <el-form-item v-if="qtyDiffers" label="差异说明" prop="receiveDiffRemark" required>
          <el-input v-model="receiveForm.receiveDiffRemark" type="textarea" :rows="2" placeholder="实收与发货数量不一致，请说明原因" />
        </el-form-item>
        <el-form-item label="到货照片" required>
          <div>
            <el-upload
              v-model:file-list="receiveFileList"
              list-type="picture-card"
              :http-request="handleReceiveUpload"
              :on-success="handleUploadSuccess"
              :before-upload="beforeUpload"
              :limit="9"
              :on-exceed="handleExceed"
              :on-preview="handlePreview"
              accept=".jpg,.jpeg,.png,.webp"
            >
              <el-icon><Plus /></el-icon>
            </el-upload>
            <div class="el-upload__tip" style="margin-top:4px">到货现场照片，最多 9 张，单张不超过 10MB</div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="receiveVisible = false">取消</el-button>
        <el-button type="primary" :loading="receiveSubmitting" @click="handleConfirmReceive">确认收货</el-button>
      </template>
    </el-dialog>

    <DeliveryDetailDialog v-model="detailVisible" :delivery-id="detailId" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { listDeliveries, receiveDelivery } from '@/api/delivery'
import { uploadFile } from '@/api/upload'
import { DELIVERY_STATUS_MAP, DELIVERY_STATUS_TAG, RECEIPT_STATUS_OPTIONS } from '@/constants/delivery'
import { PAGE_SIZE_OPTIONS } from '@/constants'
import { composeFileValue } from '@/utils/file'
import IdColumn from '@/components/IdColumn.vue'
import DeliveryDetailDialog from './DeliveryDetailDialog.vue'

const route = useRoute()

const list = ref([])
const loading = ref(true)
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const searchForm = reactive({ keyword: '', status: '' })

const receiveVisible = ref(false)
const receiveSubmitting = ref(false)
const receiveFormRef = ref(null)
const receiveTarget = ref(null)
const receiveFileList = ref([])
const receiveForm = reactive({ receiveQty: 0, receiveDiffRemark: '' })
const receiveRules = {
  receiveQty: [{ required: true, message: '请填写实收数量', trigger: 'change' }],
  receiveDiffRemark: [{
    validator: (_rule, value, callback) => {
      if (qtyDiffers.value && !String(value || '').trim()) callback(new Error('请填写差异说明'))
      else callback()
    },
    trigger: 'blur'
  }]
}

const qtyDiffers = computed(() => {
  if (!receiveTarget.value) return false
  const expected = receiveTarget.value.shipQty != null ? receiveTarget.value.shipQty : receiveTarget.value.quantity
  return expected != null && receiveForm.receiveQty !== expected
})

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

function openReceive(row) {
  receiveTarget.value = row
  const expected = row.shipQty != null ? row.shipQty : row.quantity
  receiveForm.receiveQty = expected != null ? expected : 0
  receiveForm.receiveDiffRemark = ''
  receiveFileList.value = []
  receiveVisible.value = true
}

function beforeUpload(file) {
  if (file.size > 10 * 1024 * 1024) { ElMessage.error('文件不能超过 10MB'); return false }
  return true
}

function handleExceed() {
  ElMessage.warning('最多上传 9 张到货照片')
}

function handlePreview(file) {
  window.open(file.url, '_blank')
}

async function handleReceiveUpload({ file }) {
  const { data } = await uploadFile(file, 'delivery')
  return data
}

function handleUploadSuccess(response, uploadFile) {
  // 只存 oss_key（签名 URL 会过期）；多张照片仍以逗号拼接，后端按 key 逐项现签
  uploadFile.ossValue = response?.key || ''
}

async function handleConfirmReceive() {
  try { await receiveFormRef.value.validate() } catch { return }
  const attachments = receiveFileList.value
    .filter((f) => f.ossValue)
    .map((f) => f.ossValue)
  if (attachments.length === 0) { ElMessage.error('请上传到货照片'); return }
  if (!receiveTarget.value) return
  receiveSubmitting.value = true
  try {
    await receiveDelivery(receiveTarget.value.id, {
      receiveQty: receiveForm.receiveQty,
      receiveDiffRemark: receiveForm.receiveDiffRemark,
      // 传 oss_key（多张照片逗号拼接），后端 VO 返回时逐项现签 URL
      receiveAttachmentKey: attachments.join(',')
    })
    ElMessage.success('已确认收货')
    receiveVisible.value = false
    fetchList()
  } catch { /* ignore */ }
  finally { receiveSubmitting.value = false }
}
</script>

<style>
.receipt-dialog { width: auto !important; max-width: 640px; }
.receipt-dialog .el-form-item__label { white-space: nowrap; }
.receipt-dialog .el-form-item.is-required .el-form-item__label::before { margin-right: 2px; }
</style>
