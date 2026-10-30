<template>
  <div>
    <el-card class="content-card">
      <div class="table-toolbar">
        <span class="text-base font-semibold text-gray-700">物流追踪详情</span>
        <div>
          <!-- 仅供应商可录物流节点（采购方自录会绕过验收前置） -->
          <el-button v-if="isSupplier" type="primary" plain :icon="Plus" @click="eventVisible = true">录入物流节点</el-button>
          <el-button :icon="ArrowLeft" @click="goBack">返回</el-button>
        </div>
      </div>

      <div v-loading="loading" style="min-height:150px">
        <template v-if="detail">
          <!-- 风险联动：该交付单有未关闭风险时展示，点击跳风险详情 -->
          <el-alert
            v-if="activeRisk"
            type="error"
            :closable="false"
            show-icon
            style="margin-bottom:12px"
          >
            <template #title>
              <span>
                该交付单存在{{ RISK_TYPE_MAP[activeRisk.riskType] || activeRisk.riskType }}风险
                <el-tag :type="RISK_LEVEL_TAG[activeRisk.riskLevel] || 'info'" size="small" style="margin-left:6px">{{ RISK_LEVEL_MAP[activeRisk.riskLevel] || activeRisk.riskLevel }}</el-tag>
                <el-button link type="primary" size="small" @click="goRiskDetail">去处置</el-button>
              </span>
            </template>
          </el-alert>

          <!-- ① 交付单 + 承运信息 -->
          <el-descriptions title="交付与承运信息" :column="3" border class="detail-descriptions">
            <el-descriptions-item label="交付单号">{{ detail.deliveryNo }}</el-descriptions-item>
            <el-descriptions-item label="批次">
              第 {{ detail.batchNo || 1 }} 批
              <el-tag v-if="detail.reshipFrom" size="small" type="warning" style="margin-left:4px">补</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="交付状态">
              <el-tag :type="DELIVERY_STATUS_TAG[detail.status] || 'info'">{{ DELIVERY_STATUS_MAP[detail.status] || detail.status }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="合同编号"><EmptyText :value="detail.contractNo" /></el-descriptions-item>
            <el-descriptions-item label="合同标题"><EmptyText :value="detail.contractTitle" /></el-descriptions-item>
            <el-descriptions-item label="采购方"><EmptyText :value="detail.buyerCompanyName" /></el-descriptions-item>
            <el-descriptions-item label="供应方"><EmptyText :value="detail.supplierCompanyName" /></el-descriptions-item>
            <el-descriptions-item label="物流公司"><EmptyText :value="detail.shipCompany" /></el-descriptions-item>
            <el-descriptions-item label="运单号"><EmptyText :value="detail.shipTrackingNo" /></el-descriptions-item>
            <el-descriptions-item label="发货时间">{{ formatDateTime(detail.shipTime) }}</el-descriptions-item>
            <el-descriptions-item label="发货数量"><EmptyText :value="detail.shipQty" /></el-descriptions-item>
            <el-descriptions-item label="交货地点">
              <el-button v-if="detail.deliveryPlace" link type="primary" title="点击查看当地天气" @click="openWeather(detail.deliveryPlace)">{{ detail.deliveryPlace }}</el-button>
              <EmptyText v-else />
            </el-descriptions-item>
          </el-descriptions>

          <!-- ② ETA 对比条：最新 ETA vs 考核基准（已确认承诺 > 合同交期） -->
          <div class="eta-compare" :class="etaCompare.className">
            <template v-if="latestEta">
              <span>预计到货 {{ formatDateTime(latestEta) }}</span>
              <span class="eta-divider">|</span>
              <span>{{ acceptedPromise ? '承诺交期' : '合同交期' }} {{ effectiveDeadline || '-' }}</span>
              <span v-if="etaCompare.diffText" class="eta-diff">{{ etaCompare.diffText }}</span>
            </template>
            <span v-else>暂无预计到货时间（ETA）</span>
          </div>

          <!-- 承诺交期区：待确认承诺（采购方可确认/拒绝）+ 历史记录 -->
          <div v-if="pendingPromise || acceptedPromise" class="section">
            <div class="section-title">承诺交期</div>
            <el-alert
              v-if="pendingPromise"
              type="warning"
              :closable="false"
              show-icon
              style="margin-bottom:12px"
            >
              <template #title>
                <span>
                  供应商提交承诺交期 <b>{{ pendingPromise.promisedDate }}</b>（{{ pendingPromise.reason }}）
                  <template v-if="isBuyer">
                    <el-button link type="success" size="small" :loading="promiseConfirming" @click="handleConfirmPromise">确认</el-button>
                    <el-button link type="danger" size="small" :loading="promiseConfirming" @click="handleRejectPromise">拒绝</el-button>
                  </template>
                  <template v-else>
                    <span style="color:#909399">　等待采购方确认</span>
                  </template>
                </span>
              </template>
            </el-alert>
            <el-alert
              v-if="acceptedPromise"
              type="success"
              :closable="false"
              show-icon
              style="margin-bottom:12px"
              :title="`已确认承诺交期 ${acceptedPromise.promisedDate}（${acceptedPromise.reason}），按此日期考核`"
            />
          </div>

          <!-- ③ 物流节点进度区：6 步顺序里程碑 + 纵向时间线 -->
          <div class="section">
            <div class="section-title">物流节点进度</div>
            <el-steps :active="milestoneActive" align-center finish-status="success" style="margin:16px 0 24px">
              <el-step
                v-for="m in LOGISTICS_MILESTONES"
                :key="m"
                :title="LOGISTICS_EVENT_MAP[m] || m"
              />
            </el-steps>

            <el-timeline v-if="logisticsEvents.length">
              <el-timeline-item
                v-for="e in logisticsEvents"
                :key="e.id"
                :timestamp="formatDateTime(e.eventTime)"
                :type="logisticsEventType(e.eventType)"
              >
                <div>
                  {{ LOGISTICS_EVENT_MAP[e.eventType] || e.eventType }}
                  <!-- 来源标注：manual 人工录入 / tms 系统同步（二期） -->
                  <el-tag v-if="e.source === 'tms'" type="primary" size="small" effect="plain" style="margin-left:6px">系统同步</el-tag>
                  <el-tag v-else type="info" size="small" effect="plain" style="margin-left:6px">人工录入</el-tag>
                  <span v-if="e.location" style="margin-left:6px">
                    （<el-button link type="primary" title="点击查看当地天气" @click="openWeather(e.location)">{{ e.location }}</el-button>）
                  </span>
                  <span v-if="e.estimatedArrivalTime" style="color:#409eff;margin-left:8px">ETA {{ formatDateTime(e.estimatedArrivalTime) }}</span>
                </div>
                <div v-if="e.remark" style="color:#909399;font-size:12px">{{ e.remark }}</div>
              </el-timeline-item>
            </el-timeline>
            <el-empty v-else description="暂无物流节点，点击右上角录入" :image-size="50" />
          </div>
        </template>
      </div>
    </el-card>

    <!-- 录入物流节点（共享组件，ETA 默认沿用最近一次或合同交期） -->
    <LogisticsEventDialog v-model="eventVisible" :delivery-id="route.params.id" :default-eta="defaultEta" @success="load" />
    <NodeWeatherDialog v-model="weatherVisible" :city="weatherCity" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, ArrowLeft } from '@element-plus/icons-vue'
import { getDeliveryDetail, getDeliveryActiveRisk, confirmPromiseDate, rejectPromiseDate } from '@/api/delivery'
import { DELIVERY_STATUS_MAP, DELIVERY_STATUS_TAG } from '@/constants/delivery'
import { LOGISTICS_EVENT_MAP, LOGISTICS_MILESTONES, RISK_TYPE_MAP, RISK_LEVEL_MAP, RISK_LEVEL_TAG } from '@/constants/risk'
import { formatDateTime, formatDate } from '@/utils/datetime'
import { useAuthStore } from '@/stores/auth'
import EmptyText from '@/components/EmptyText.vue'
import LogisticsEventDialog from '@/components/LogisticsEventDialog.vue'
import NodeWeatherDialog from '@/components/NodeWeatherDialog.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
// 确认/拒绝承诺：采购方视角可操作（both 双角色默认采购视角，也能看到按钮）
const isBuyer = computed(() => authStore.profileType !== 'supplier')
// 录入物流节点严格供应商专属：仅纯供应商身份可见（both 的供应商动作在发货管理页做）
const isSupplier = computed(() => authStore.profileType === 'supplier')

const loading = ref(true)
const detail = ref(null)
const logisticsEvents = ref([])
const activeRisk = ref(null)
const promiseDates = ref([])
const eventVisible = ref(false)
const weatherVisible = ref(false)
const weatherCity = ref('')

function openWeather(city) {
  weatherCity.value = city
  weatherVisible.value = true
}
// 承诺确认/拒绝 loading
const promiseConfirming = ref(false)

onMounted(() => { load() })

async function load() {
  loading.value = true
  try {
    // 交付详情（含物流事件时间线，不含履约时间线——履约动作属于交付详情弹窗）
    const res = await getDeliveryDetail(route.params.id)
    const data = res?.data || res
    detail.value = data.delivery
    logisticsEvents.value = data.logisticsEvents || []
    promiseDates.value = data.promiseDates || []
    // 风险联动：查该交付单未关闭风险（无则 data.risk=null）
    const riskRes = await getDeliveryActiveRisk(route.params.id)
    activeRisk.value = riskRes?.data?.risk || null
  } catch { /* ignore */ }
  finally { loading.value = false }
}

/** 待确认承诺（供应商提交后采购方处理） */
const pendingPromise = computed(() =>
  promiseDates.value.find((p) => p.status === 'pending') || null)

/** 已确认承诺（考核基准，最近一条） */
const acceptedPromise = computed(() =>
  [...promiseDates.value].reverse().find((p) => p.status === 'accepted') || null)

/** 考核基准：已确认承诺优先，无则回退合同交期 */
const effectiveDeadline = computed(() =>
  acceptedPromise.value?.promisedDate || detail.value?.deliveryDate || null)

async function handleConfirmPromise() {
  if (!pendingPromise.value) return
  try {
    await ElMessageBox.confirm(
      `<div style="white-space:nowrap;margin-bottom:4px">确认承诺交期 <span class="highlight-name">${pendingPromise.value.promisedDate}</span> 吗？</div><div>确认后按此日期考核交付准时率。</div>`,
      '确认承诺交期',
      { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning', dangerouslyUseHTMLString: true, customClass: 'confirmDdMsgBox' }
    )
  } catch { return }
  promiseConfirming.value = true
  try {
    await confirmPromiseDate(pendingPromise.value.id)
    ElMessage.success('承诺交期已确认')
    await load()
  } catch { /* ignore */ }
  finally { promiseConfirming.value = false }
}

async function handleRejectPromise() {
  if (!pendingPromise.value) return
  try {
    await ElMessageBox.confirm(
      `<div style="white-space:nowrap;margin-bottom:4px">拒绝承诺交期 <span class="highlight-name">${pendingPromise.value.promisedDate}</span> 吗？</div><div>供应商可重新提交新承诺。</div>`,
      '拒绝承诺交期',
      { confirmButtonText: '拒绝', cancelButtonText: '取消', type: 'warning', dangerouslyUseHTMLString: true, customClass: 'confirmDdMsgBox' }
    )
  } catch { return }
  promiseConfirming.value = true
  try {
    await rejectPromiseDate(pendingPromise.value.id)
    ElMessage.success('已拒绝，供应商可重新提交')
    await load()
  } catch { /* ignore */ }
  finally { promiseConfirming.value = false }
}

/** 最新 ETA：取时间线上最后一条带 estimatedArrivalTime 的事件 */
const latestEta = computed(() => {
  const withEta = logisticsEvents.value.filter((e) => e.estimatedArrivalTime)
  return withEta.length ? withEta[withEta.length - 1].estimatedArrivalTime : null
})

/** 录入弹窗 ETA 默认值：最近一次 ETA，无历史时回退合同交期 */
const defaultEta = computed(() => {
  if (latestEta.value) return latestEta.value
  const d = detail.value?.deliveryDate
  return d ? `${String(d).slice(0, 10)}T00:00:00` : ''
})

/** ETA 与考核基准对比：晚于基准红色警示，早于绿色；基准=已确认承诺 > 合同交期 */
const etaCompare = computed(() => {
  if (!latestEta.value || !effectiveDeadline.value) {
    return { className: '', diffText: '' }
  }
  const etaDate = new Date(latestEta.value)
  const deadline = new Date(effectiveDeadline.value)
  const diffDays = Math.ceil((etaDate - deadline) / 86400000)
  const baseLabel = acceptedPromise.value ? '（基准：承诺交期）' : ''
  if (diffDays > 0) {
    return { className: 'eta-late', diffText: `预计晚于基准 ${diffDays} 天${baseLabel}` }
  }
  if (diffDays < 0) {
    return { className: 'eta-early', diffText: `预计提前 ${-diffDays} 天${baseLabel}` }
  }
  return { className: '', diffText: `预计按期到达${baseLabel}` }
})

/** 进度条当前步：最后发生的里程碑下标 + 1（el-steps active=6 时全部点亮；跳跃完成时后面的亮了前面的也视为已走过） */
const milestoneActive = computed(() => {
  let lastIdx = -1
  LOGISTICS_MILESTONES.forEach((m, i) => {
    if (logisticsEvents.value.some((e) => e.eventType === m)) lastIdx = i
  })
  return lastIdx + 1
})

function logisticsEventType(type) {
  if (type === 'delay' || type === 'exception' || type === 'customs_hold') return 'danger'
  if (type === 'arrived_delivered' || type === 'customs_clear') return 'success'
  return 'primary'
}

function goBack() {
  router.push({ name: 'LogisticsTracking' })
}

function goRiskDetail() {
  router.push({ name: 'DeliveryRiskDetail', params: { id: activeRisk.value.id } })
}
</script>

<style>
.eta-compare {
  margin: 12px 0;
  padding: 10px 16px;
  border-radius: 4px;
  background: #f5f7fa;
  font-size: 14px;
  color: #606266;
}
.eta-compare .eta-divider {
  margin: 0 10px;
  color: #dcdfe6;
}
.eta-compare .eta-diff {
  margin-left: 12px;
  font-weight: 600;
}
.eta-compare.eta-late {
  background: #fef0f0;
  color: #f56c6c;
}
.eta-compare.eta-late .eta-diff {
  color: #f56c6c;
}
.eta-compare.eta-early .eta-diff {
  color: #67c23a;
}
</style>
