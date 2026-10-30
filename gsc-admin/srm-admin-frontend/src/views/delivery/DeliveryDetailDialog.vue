<template>
  <el-dialog v-model="visible" title="交付详情" :close-on-click-modal="false" class="delivery-dialog">
    <div v-loading="loading" style="min-height:150px">
      <template v-if="detail">
        <el-alert
          v-if="returnAlert"
          :type="returnAlert.type"
          :closable="false"
          show-icon
          style="margin-bottom:12px"
          :title="returnAlert.title"
        />

        <el-descriptions title="交付信息" :column="2" border class="detail-descriptions">
          <el-descriptions-item label="交付单号">{{ detail.deliveryNo }}</el-descriptions-item>
          <el-descriptions-item label="批次">
            第 {{ detail.batchNo || 1 }} 批
            <el-tag v-if="detail.reshipFrom" size="small" type="warning" style="margin-left:4px">补</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="DELIVERY_STATUS_TAG[detail.status] || 'info'">{{ DELIVERY_STATUS_MAP[detail.status] || detail.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="合同编号"><EmptyText :value="detail.contractNo" /></el-descriptions-item>
          <el-descriptions-item label="合同标题"><EmptyText :value="detail.contractTitle" /></el-descriptions-item>
          <el-descriptions-item label="采购方"><EmptyText :value="detail.buyerCompanyName" /></el-descriptions-item>
          <el-descriptions-item label="供应方"><EmptyText :value="detail.supplierCompanyName" /></el-descriptions-item>
          <el-descriptions-item label="本批应发"><EmptyText :value="detail.quantity" /></el-descriptions-item>
          <el-descriptions-item label="发货数量"><EmptyText :value="detail.shipQty" /></el-descriptions-item>
          <el-descriptions-item label="实收数量"><EmptyText :value="detail.receiveQty" /></el-descriptions-item>
          <el-descriptions-item label="本批金额"><EmptyText :value="detail.amount" /></el-descriptions-item>
          <el-descriptions-item label="币种"><EmptyText :value="detail.currency" /></el-descriptions-item>
          <el-descriptions-item label="交付条款"><EmptyText :value="detail.deliveryTerms" /></el-descriptions-item>
          <el-descriptions-item label="交货地点"><EmptyText :value="detail.deliveryPlace" /></el-descriptions-item>
          <el-descriptions-item label="交付日期"><EmptyText :value="detail.deliveryDate" /></el-descriptions-item>
        </el-descriptions>

        <el-descriptions title="物流信息" :column="2" border class="detail-descriptions">
          <el-descriptions-item label="物流公司"><EmptyText :value="detail.shipCompany" /></el-descriptions-item>
          <el-descriptions-item label="运单号"><EmptyText :value="detail.shipTrackingNo" /></el-descriptions-item>
          <el-descriptions-item label="发货日期"><EmptyText :value="detail.shipDate" /></el-descriptions-item>
          <el-descriptions-item label="发货时间">{{ formatDateTime(detail.shipTime) }}</el-descriptions-item>
          <el-descriptions-item label="发货凭证">
            <a v-if="detail.shipAttachmentUrl" :href="getFileUrl(detail.shipAttachmentUrl)" target="_blank" style="color:#409eff">{{ getFileName(detail.shipAttachmentUrl) }}</a>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="收货时间">{{ formatDateTime(detail.receiveTime) }}</el-descriptions-item>
          <el-descriptions-item label="到货照片" :span="2">
            <div v-if="receivePhotos.length" class="receive-photos">
              <el-image
                v-for="(item, idx) in receivePhotos"
                :key="idx"
                :src="getFileUrl(item)"
                :preview-src-list="receivePhotoUrls"
                :initial-index="idx"
                fit="cover"
                class="receive-photo"
              />
            </div>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.receiveDiffRemark" label="收货差异" :span="2">{{ detail.receiveDiffRemark }}</el-descriptions-item>
        </el-descriptions>

        <!-- 违约金（验收时计算快照；合同未配条款则不展示） -->
        <el-descriptions v-if="detail.penaltyCalculatedAt || detail.penaltyDays != null" title="违约金" :column="2" border class="detail-descriptions">
          <el-descriptions-item label="延迟天数">
            <span :style="detail.penaltyDays > 0 ? 'color:#f56c6c;font-weight:600' : ''">{{ detail.penaltyDays }} 天</span>
          </el-descriptions-item>
          <el-descriptions-item label="违约金">
            <span :style="detail.penaltyDays > 0 ? 'color:#f56c6c;font-weight:600' : ''">{{ detail.penaltyAmount != null ? detail.penaltyAmount + ' ' + (detail.currency || 'CNY') : '-' }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <el-descriptions v-if="detail.acceptResult || detail.returnReason" title="验收信息" :column="2" border class="detail-descriptions">
          <el-descriptions-item label="验收结果"><EmptyText :value="acceptResultLabel" /></el-descriptions-item>
          <el-descriptions-item label="验收时间">{{ formatDateTime(detail.acceptTime) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.acceptRemark" label="验收备注" :span="2">{{ detail.acceptRemark }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.returnReason" label="退回原因" :span="2">
            <span style="color:#f56c6c;font-weight:600">{{ detail.returnReason }}</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.returnAttachmentUrl" label="退回凭证" :span="2">
            <a :href="getFileUrl(detail.returnAttachmentUrl)" target="_blank" style="color:#f56c6c">{{ getFileName(detail.returnAttachmentUrl) }}</a>
          </el-descriptions-item>
          <el-descriptions-item v-if="(detail.status === 'return_shipping' || detail.status === 'returned') && detail.returnShipCompany" label="退货物流" :span="2">
            {{ detail.returnShipCompany }}<template v-if="detail.returnShipTrackingNo"> / {{ detail.returnShipTrackingNo }}</template>
          </el-descriptions-item>
        </el-descriptions>

        <div class="trace-section">
          <div class="trace-title">物流节点</div>
          <el-timeline v-if="logisticsEvents.length">
            <el-timeline-item v-for="e in logisticsEvents" :key="e.id" :timestamp="formatDateTime(e.eventTime)" :type="logisticsEventType(e.eventType)">
              {{ LOGISTICS_EVENT_MAP[e.eventType] || e.eventType }}
              <span v-if="e.location">
                （<el-button link type="primary" title="点击查看当地天气" @click="openWeather(e.location)">{{ e.location }}</el-button>）
              </span>
              <span v-if="e.estimatedArrivalTime" style="color:#409eff">　ETA {{ formatDateTime(e.estimatedArrivalTime) }}</span>
              <span v-if="e.remark" style="color:#909399;display:block;font-size:12px">{{ e.remark }}</span>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无物流节点" :image-size="50" />
        </div>

        <div class="trace-section">
          <div class="trace-title">履约时间线</div>
          <el-timeline v-if="traces.length">
            <el-timeline-item v-for="t in traces" :key="t.id" :timestamp="formatDateTime(t.createTime)" :type="traceType(t.action)">
              {{ t.actionDesc || DELIVERY_TRACE_ACTION_MAP[t.action] || t.action }}
              <span v-if="t.remark" :style="{ color: (t.action === 'return_request' || t.action === 'return_dispute') ? '#f56c6c' : '#909399' }">（{{ t.remark }}）</span>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无记录" :image-size="50" />
        </div>
      </template>
    </div>
    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
  <NodeWeatherDialog v-model="weatherVisible" :city="weatherCity" />
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { getDeliveryDetail } from '@/api/delivery'
import { DELIVERY_STATUS_MAP, DELIVERY_STATUS_TAG, DELIVERY_TRACE_ACTION_MAP } from '@/constants/delivery'
import { LOGISTICS_EVENT_MAP } from '@/constants/risk'
import { formatDateTime } from '@/utils/datetime'
import { getFileUrl, getFileName, getAttachmentList } from '@/utils/file'
import NodeWeatherDialog from '@/components/NodeWeatherDialog.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  deliveryId: { type: [Number, String], default: null }
})
const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const weatherVisible = ref(false)
const weatherCity = ref('')

function openWeather(city) {
  weatherCity.value = city
  weatherVisible.value = true
}

const loading = ref(false)
const detail = ref(null)
const traces = ref([])
const logisticsEvents = ref([])
const receivePhotos = computed(() => getAttachmentList(detail.value?.receiveAttachmentUrl))
const receivePhotoUrls = computed(() => receivePhotos.value.map(getFileUrl))
const acceptResultLabel = computed(() => {
  const r = detail.value?.acceptResult
  if (r === 'accepted') return '验收合格'
  if (r === 'returned') return '验收不合格'
  if (r === 'partial') return `部分验收（残次 ${detail.value?.defectiveQty ?? '-'} 件）`
  return null
})
const returnAlert = computed(() => {
  const d = detail.value
  if (!d || !d.returnReason) return null
  const reason = d.returnReason
  if (d.acceptResult === 'partial') return { type: 'warning', title: `部分验收：残次 ${d.defectiveQty ?? '-'} 件已发起补发` }
  if (d.status === 'return_pending') return { type: 'warning', title: '验收不合格：' + reason + '，等待供应商确认' }
  if (d.status === 'return_confirmed') return { type: 'warning', title: '验收不合格：' + reason + '，供应商已确认，待填写退货物流' }
  if (d.status === 'return_shipping') return { type: 'warning', title: '验收不合格：' + reason + '，退货中，待供应商确认收货' }
  return { type: 'error', title: '验收不合格：' + reason }
})

watch(visible, (v) => {
  if (v && props.deliveryId != null) {
    load(props.deliveryId)
  }
})

async function load(id) {
  loading.value = true
  detail.value = null
  traces.value = []
  logisticsEvents.value = []
  try {
    const res = await getDeliveryDetail(id)
    const data = res?.data || res
    detail.value = data.delivery
    traces.value = data.traces || []
    logisticsEvents.value = data.logisticsEvents || []
  } catch { /* ignore */ }
  finally { loading.value = false }
}

function traceType(action) {
  if (action === 'return_request' || action === 'return_dispute') return 'danger'
  if (action === 'accept') return 'success'
  if (action === 'return_confirm' || action === 'return_logistics' || action === 'return_receipt' || action === 'correct_tracking' || action === 'split' || action === 'partial_reject' || action === 'delay_warning') return 'warning'
  return 'primary'
}

function logisticsEventType(type) {
  if (type === 'delay' || type === 'exception' || type === 'customs_hold') return 'danger'
  if (type === 'arrived_delivered' || type === 'customs_clear') return 'success'
  return 'primary'
}
</script>

<style>
.delivery-dialog { width: auto !important; max-width: 720px; }
.delivery-dialog .detail-descriptions:last-child { margin-bottom: 0; }
.delivery-dialog .receive-photos { display: flex; flex-wrap: wrap; gap: 8px; }
.delivery-dialog .receive-photo { width: 80px; height: 80px; border-radius: 4px; cursor: pointer; }
.delivery-dialog .trace-section { margin-top: 16px; }
.delivery-dialog .trace-title { font-size: 14px; font-weight: 600; color: #303133; margin-bottom: 12px; }
</style>
