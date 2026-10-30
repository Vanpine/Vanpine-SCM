<template>
  <div>
    <el-card class="content-card">
      <div class="table-toolbar">
        <span class="text-base font-semibold text-gray-700">交付风险详情</span>
        <el-button :icon="ArrowLeft" @click="goBack">返回</el-button>
      </div>

      <div v-loading="loading" style="min-height:150px">
        <template v-if="risk">
          <!-- 风险概要 -->
          <el-descriptions :column="3" border class="risk-overview">
            <el-descriptions-item label="交付单号"><EmptyText :value="risk.deliveryNo" /></el-descriptions-item>
            <el-descriptions-item label="合同标题"><EmptyText :value="risk.contractTitle" /></el-descriptions-item>
            <el-descriptions-item label="采购企业"><EmptyText :value="risk.buyerCompanyName" /></el-descriptions-item>
            <el-descriptions-item label="供应企业"><EmptyText :value="risk.supplierCompanyName" /></el-descriptions-item>
            <el-descriptions-item label="风险类型">
              <el-tag type="danger" size="small">{{ RISK_TYPE_MAP[risk.riskType] || risk.riskType }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="风险等级">
              <el-tag :type="RISK_LEVEL_TAG[risk.riskLevel] || 'info'" size="small">{{ RISK_LEVEL_MAP[risk.riskLevel] || risk.riskLevel }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="风险状态">
              <el-tag :type="RISK_STATUS_TAG[risk.riskStatus] || 'info'" size="small">{{ RISK_STATUS_MAP[risk.riskStatus] || risk.riskStatus }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="风险评分">
              <span style="font-weight:600" :style="{ color: riskScoreColor(risk.riskScore) }"><EmptyText :value="risk.riskScore" /></span>
            </el-descriptions-item>
            <el-descriptions-item label="预计到货时间">{{ formatDateTime(risk.estimatedArrivalTime) || '-' }}</el-descriptions-item>
            <el-descriptions-item label="合同交期"><EmptyText :value="risk.promisedDeliveryDate" /></el-descriptions-item>
            <el-descriptions-item label="生产需求日期"><EmptyText :value="risk.productionNeedDate" /></el-descriptions-item>
            <el-descriptions-item label="影响数量"><EmptyText :value="risk.impactQty" /></el-descriptions-item>
            <el-descriptions-item label="影响金额"><EmptyText :value="risk.impactAmount" /></el-descriptions-item>
            <el-descriptions-item label="触发证据" :span="3">{{ risk.evidence || '-' }}</el-descriptions-item>
          </el-descriptions>

          <div v-if="risk.riskType === 'weather_disruption'" class="section weather-advice">
            <div class="section-title">
              <span class="ai-mark">AI</span>
              天气处置建议
              <el-tag v-if="risk.adviceStatus === 'ready'" size="small" type="success" effect="plain">AI 已生成</el-tag>
              <el-tag v-else-if="risk.adviceStatus === 'generating'" size="small" type="warning" effect="plain">AI 生成中</el-tag>
              <el-tag v-else-if="risk.adviceStatus === 'fallback'" size="small" type="info" effect="plain">规则兜底</el-tag>
            </div>
            <p v-if="risk.adviceStatus === 'generating'" class="advice-pending">正在根据天气证据生成建议，可先看上方触发证据</p>
            <ul v-else-if="risk.adviceLines && risk.adviceLines.length" class="advice-list">
              <li v-for="(line, i) in risk.adviceLines" :key="i">{{ line }}</li>
            </ul>
            <p v-else class="advice-pending">暂无建议</p>
          </div>

          <!-- 物流事件时间线 -->
          <div class="section">
            <div class="section-title">物流事件时间线</div>
            <el-timeline v-if="events.length">
              <el-timeline-item v-for="e in events" :key="e.id" :timestamp="formatDateTime(e.eventTime)" :type="eventTimelineType(e.eventType)">
                <div>
                  {{ LOGISTICS_EVENT_MAP[e.eventType] || e.eventType }}
                  <span v-if="e.location">
                    （<el-button link type="primary" title="点击查看当地天气" @click="openWeather(e.location)">{{ e.location }}</el-button>）
                  </span>
                  <span v-if="e.estimatedArrivalTime" style="color:#409eff;margin-left:8px">ETA {{ formatDateTime(e.estimatedArrivalTime) }}</span>
                </div>
                <div v-if="e.remark" style="color:#909399;font-size:12px">{{ e.remark }}</div>
              </el-timeline-item>
            </el-timeline>
            <el-empty v-else description="暂无物流事件" :image-size="50" />
            <!-- 仅供应商可录物流节点（采购方自录会绕过验收前置） -->
            <el-button v-if="isSupplier" type="primary" plain size="small" style="margin-top:8px" @click="openEventDialog">录入物流事件</el-button>
          </div>

          <!-- 风险因子历史（类型演变轨迹） -->
          <div class="section">
            <div class="section-title">风险因子历史</div>
            <el-timeline v-if="risk.factors && risk.factors.length">
              <el-timeline-item
                v-for="f in risk.factors"
                :key="f.id"
                :timestamp="formatDateTime(f.firstDetectedTime)"
                :type="f.factorStatus === 'closed' ? 'info' : 'primary'"
              >
                <div>
                  {{ RISK_TYPE_MAP[f.riskType] || f.riskType }}
                  <el-tag :type="RISK_LEVEL_TAG[f.riskLevel] || 'info'" size="small" style="margin-left:6px">{{ RISK_LEVEL_MAP[f.riskLevel] || f.riskLevel }}</el-tag>
                  <el-tag :type="f.factorStatus === 'active' ? 'primary' : 'info'" size="small" effect="plain" style="margin-left:6px">{{ FACTOR_STATUS_MAP[f.factorStatus] || f.factorStatus }}</el-tag>
                  <span v-if="f.factorStatus === 'closed' && f.closedTime" style="color:#909399;margin-left:6px">结束于 {{ formatDateTime(f.closedTime) }}</span>
                </div>
                <div v-if="f.evidence" style="color:#909399;font-size:12px">{{ f.evidence }}</div>
              </el-timeline-item>
            </el-timeline>
            <el-empty v-else description="暂无风险因子" :image-size="50" />
          </div>

          <!-- 补救工单 -->
          <div class="section">
            <div class="section-title">补救工单</div>
            <el-table :data="actions" border size="small" style="width:100%">
              <el-table-column label="方案" min-width="180" show-overflow-tooltip>
                <template #default="{ row }">{{ row.actionName || (ACTION_TYPE_MAP[row.actionType] || row.actionType) }}</template>
              </el-table-column>
              <el-table-column label="类型" width="120">
                <template #default="{ row }">{{ ACTION_TYPE_MAP[row.actionType] || row.actionType }}</template>
              </el-table-column>
              <el-table-column label="状态" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="ACTION_STATUS_TAG[row.status] || 'info'" size="small">{{ ACTION_STATUS_MAP[row.status] || row.status }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="预计恢复" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">{{ formatDateTime(row.expectedEta) || '-' }}</template>
              </el-table-column>
              <el-table-column label="操作" width="180" align="center">
                <template #default="{ row }">
                  <el-button v-if="row.status === 'pending'" link type="primary" size="small" @click="handleStart(row)">开始</el-button>
                  <el-button v-if="row.status === 'executing'" link type="success" size="small" @click="openComplete(row)">完成</el-button>
                </template>
              </el-table-column>
              <template #empty>
                <el-empty description="暂无补救工单" :image-size="50" />
              </template>
            </el-table>
            <el-button type="primary" plain size="small" style="margin-top:8px" @click="openActionDialog">新建补救工单</el-button>
          </div>

          <!-- 8D 整改报告 -->
          <div class="section">
            <div class="section-title">8D 整改报告</div>
            <template v-if="report8d">
              <!-- 状态卡：pending 待填写 / submitted 待审核 / approved 已通过 / rejected 已打回 -->
              <el-alert
                :type="REPORT8D_STATUS_TAG[report8d.status] === 'danger' ? 'error' : REPORT8D_STATUS_TAG[report8d.status] === 'success' ? 'success' : REPORT8D_STATUS_TAG[report8d.status] === 'warning' ? 'warning' : 'info'"
                :closable="false"
                show-icon
                style="margin-bottom:12px"
              >
                <template #title>
                  <span>
                    状态：<b>{{ REPORT8D_STATUS_MAP[report8d.status] || report8d.status }}</b>
                    <template v-if="report8d.deadline">　整改期限：{{ report8d.deadline }}</template>
                    <template v-if="report8d.deadline && report8d.status === 'pending' && isOverdue(report8d.deadline)">
                      　<span style="color:#f56c6c">已逾期 {{ overdueDays(report8d.deadline) }} 天</span>
                    </template>
                    <template v-if="report8d.status === 'rejected' && report8d.rejectReason">
                      　打回原因：<span style="color:#f56c6c">{{ report8d.rejectReason }}</span>
                    </template>
                  </span>
                </template>
              </el-alert>
              <!-- 操作按钮按身份：供应商填写/重提，采购方审核 -->
              <div style="margin-bottom:12px">
                <el-button v-if="isSupplier && (report8d.status === 'pending' || report8d.status === 'rejected')" type="primary" plain size="small" @click="open8dDialog">填写 8D 报告</el-button>
                <el-button v-if="isBuyer && report8d.status === 'submitted'" type="success" size="small" :loading="report8dSubmitting" @click="handleApprove8d">审核通过</el-button>
                <el-button v-if="isBuyer && report8d.status === 'submitted'" type="danger" size="small" @click="openReject8d">打回</el-button>
              </div>
              <!-- 已提交/已通过/已打回：只读展示 D1-D8 -->
              <el-descriptions v-if="report8d.status !== 'pending'" :column="1" border size="small">
                <el-descriptions-item v-for="step in REPORT8D_STEPS" :key="step.key" :label="step.label">
                  {{ report8d[step.key] || '-' }}
                </el-descriptions-item>
              </el-descriptions>
              <el-empty v-else description="供应商尚未填写" :image-size="50" />
            </template>
            <!-- 采购方无 8D 时可发起（风险未关闭） -->
            <template v-else>
              <el-empty description="尚未发起 8D 整改" :image-size="50" />
              <el-button v-if="isBuyer && risk.riskStatus !== 'closed'" type="primary" plain size="small" style="margin-top:8px" @click="openCreate8d">发起 8D 整改</el-button>
            </template>
          </div>

          <!-- 关闭风险 -->
          <div class="section" style="text-align:right">
            <el-button v-if="risk.riskStatus !== 'closed'" type="danger" :loading="closing" @click="handleClose">关闭风险</el-button>
          </div>
        </template>
      </div>
    </el-card>

    <!-- 录入物流事件（共享组件，ETA 默认沿用风险快照或合同交期） -->
    <LogisticsEventDialog
      v-model="eventVisible"
      :delivery-id="risk?.deliveryId"
      :default-eta="risk?.estimatedArrivalTime || risk?.promisedDeliveryDate || ''"
      @success="load"
    />
    <NodeWeatherDialog v-model="weatherVisible" :city="weatherCity" />

    <!-- 发起 8D 整改 Dialog（采购方） -->
    <el-dialog v-model="create8dVisible" title="发起 8D 整改" :close-on-click-modal="false" class="risk-dialog">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px" title="8D 是供应商问题整改标准流程：供应商按 D1-D8 八步书面回复，采购方审核通过才闭环" />
      <el-form ref="create8dFormRef" :model="create8dForm" :rules="create8dRules" label-width="85px" label-position="right">
        <el-form-item label="整改期限" prop="deadline">
          <el-date-picker v-model="create8dForm.deadline" type="date" value-format="YYYY-MM-DD" style="width:100%" placeholder="选择整改期限（默认 7 天）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="create8dVisible = false">取消</el-button>
        <el-button type="primary" :loading="create8dSubmitting" @click="handleCreate8d">发起</el-button>
      </template>
    </el-dialog>

    <!-- 填写 8D 报告 Dialog（供应商，D1-D8 八步全必填） -->
    <el-dialog v-model="fill8dVisible" title="填写 8D 整改报告" :close-on-click-modal="false" width="720px" class="risk-dialog-8d">
      <el-form ref="fill8dFormRef" :model="fill8dForm" :rules="fill8dRules" label-width="140px" label-position="right">
        <el-form-item v-for="step in REPORT8D_STEPS" :key="step.key" :label="step.label" :prop="step.key">
          <el-input v-model="fill8dForm[step.key]" type="textarea" :rows="2" :placeholder="step.placeholder" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="fill8dVisible = false">取消</el-button>
        <el-button type="primary" :loading="fill8dSubmitting" @click="handleSubmit8d">提交</el-button>
      </template>
    </el-dialog>

    <!-- 新建补救工单 Dialog -->
    <el-dialog v-model="actionVisible" title="新建补救工单" :close-on-click-modal="false" class="risk-dialog">
      <el-form ref="actionFormRef" :model="actionForm" :rules="actionRules" label-width="100px" label-position="right">
        <el-form-item label="方案类型" prop="actionType">
          <el-select v-model="actionForm.actionType" style="width:100%">
            <el-option v-for="opt in actionTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="方案名称">
          <el-input v-model="actionForm.actionName" placeholder="如：拆分 30% 改空运" />
        </el-form-item>
        <el-form-item label="方案说明">
          <el-input v-model="actionForm.recommendation" type="textarea" :rows="2" placeholder="系统推荐文案" />
        </el-form-item>
        <el-form-item label="预计恢复时间">
          <el-date-picker v-model="actionForm.expectedEta" type="datetime" style="width:100%" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="选填" />
        </el-form-item>
        <el-form-item label="预计成本">
          <el-input v-model="actionForm.expectedCost" placeholder="选填" />
        </el-form-item>
        <el-form-item label="可保障数量">
          <el-input-number v-model="actionForm.expectedRecoveryQty" :min="0" :controls="false" style="width:100%" placeholder="选填" />
        </el-form-item>
        <el-form-item label="截止时间">
          <el-date-picker v-model="actionForm.deadline" type="datetime" style="width:100%" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="actionVisible = false">取消</el-button>
        <el-button type="primary" :loading="actionSubmitting" @click="handleSubmitAction">确认</el-button>
      </template>
    </el-dialog>

    <!-- 完成工单 Dialog -->
    <el-dialog v-model="completeVisible" title="完成补救工单" :close-on-click-modal="false" class="risk-dialog">
      <el-form ref="completeFormRef" :model="completeForm" :rules="completeRules" label-width="100px" label-position="right">
        <el-form-item label="执行回执" prop="executionRemark">
          <el-input v-model="completeForm.executionRemark" type="textarea" :rows="3" placeholder="请填写执行回执（做了什么、结果如何）" />
        </el-form-item>
        <el-form-item label="结果说明" prop="result">
          <el-input v-model="completeForm.result" type="textarea" :rows="2" placeholder="是否解除风险" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="completeVisible = false">取消</el-button>
        <el-button type="primary" :loading="completeSubmitting" @click="handleSubmitComplete">确认完成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import {
  getRiskDetail, listRecoveryActions,
  createRecoveryAction, startRecoveryAction, completeRecoveryAction, closeRisk,
  create8d, submit8d, approve8d, reject8d
} from '@/api/risk'
import {
  RISK_TYPE_MAP, RISK_LEVEL_MAP, RISK_LEVEL_TAG, RISK_STATUS_MAP, RISK_STATUS_TAG,
  FACTOR_STATUS_MAP, LOGISTICS_EVENT_MAP, ACTION_TYPE_MAP, ACTION_TYPE_OPTIONS, ACTION_STATUS_MAP, ACTION_STATUS_TAG,
  REPORT8D_STATUS_MAP, REPORT8D_STATUS_TAG, REPORT8D_STEPS
} from '@/constants/risk'
import { formatDateTime } from '@/utils/datetime'
import { useAuthStore } from '@/stores/auth'
import EmptyText from '@/components/EmptyText.vue'
import LogisticsEventDialog from '@/components/LogisticsEventDialog.vue'
import NodeWeatherDialog from '@/components/NodeWeatherDialog.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
// 录入物流节点严格供应商专属：仅纯供应商身份可见（both/超管不显示）
const isSupplier = computed(() => authStore.profileType === 'supplier')
// 采购方视角可发起/审核 8D
const isBuyer = computed(() => authStore.profileType !== 'supplier')

const loading = ref(true)
const risk = ref(null)
const events = ref([])
const actions = ref([])
// 8D 整改报告（一风险一张，无则 null）
const report8d = ref(null)

// 录入物流事件弹窗（共享组件，成功后整页刷新）
const eventVisible = ref(false)
const weatherVisible = ref(false)
const weatherCity = ref('')

function openWeather(city) {
  weatherCity.value = city
  weatherVisible.value = true
}

const actionVisible = ref(false)
const actionSubmitting = ref(false)
const actionFormRef = ref(null)
const actionForm = reactive({ actionType: '', actionName: '', recommendation: '', expectedEta: '', expectedCost: '', expectedRecoveryQty: null, deadline: '' })
const actionRules = { actionType: [{ required: true, message: '请选择动作类型', trigger: 'change' }] }
const actionTypeOptions = ACTION_TYPE_OPTIONS

const completeVisible = ref(false)
const completeSubmitting = ref(false)
const completeFormRef = ref(null)
const completeTarget = ref(null)
const completeForm = reactive({ executionRemark: '', result: '' })
const completeRules = {
  executionRemark: [{ required: true, message: '请填写执行回执', trigger: 'blur' }],
  result: [{ required: true, message: '请填写结果说明', trigger: 'blur' }]
}

// 8D 弹窗与表单
const create8dVisible = ref(false)
const create8dSubmitting = ref(false)
const create8dFormRef = ref(null)
const create8dForm = reactive({ deadline: '' })
const create8dRules = { deadline: [{ required: true, message: '请选择整改期限', trigger: 'change' }] }

const fill8dVisible = ref(false)
const fill8dSubmitting = ref(false)
const fill8dFormRef = ref(null)
const fill8dForm = reactive({ d1Team: '', d2Desc: '', d3Containment: '', d4RootCause: '', d5Corrective: '', d6Verification: '', d7Prevention: '', d8Summary: '' })
const fill8dRules = Object.fromEntries(REPORT8D_STEPS.map((s) => [s.key, [{ required: true, message: s.label.replace(/^D\d\s*/, '') + '不能为空', trigger: 'blur' }]]))
const report8dSubmitting = ref(false)

const closing = ref(false)
const ADVICE_POLL_MS = 2000
const ADVICE_POLL_MAX = 15
let adviceTimer = null
let advicePolls = 0

onMounted(() => { load() })
onUnmounted(() => { stopAdvicePoll() })

async function load(silent = false) {
  if (!silent) loading.value = true
  try {
    const res = await getRiskDetail(route.params.id)
    const data = res?.data?.risk || res?.risk || res?.data
    risk.value = data
    events.value = data?.events || []
    report8d.value = res?.data?.report8d || res?.report8d || null
    if (!silent) actions.value = await loadActions()
    if (data?.riskType === 'weather_disruption' && data?.adviceStatus === 'generating') {
      startAdvicePoll()
    } else {
      stopAdvicePoll()
    }
  } finally {
    if (!silent) loading.value = false
  }
}

function startAdvicePoll() {
  if (adviceTimer) return
  advicePolls = 0
  adviceTimer = setInterval(async () => {
    advicePolls += 1
    if (advicePolls > ADVICE_POLL_MAX) {
      stopAdvicePoll()
      return
    }
    try { await load(true) } catch { /* 轮询失败不打断页面 */ }
  }, ADVICE_POLL_MS)
}

function stopAdvicePoll() {
  if (adviceTimer) {
    clearInterval(adviceTimer)
    adviceTimer = null
  }
}

async function loadActions() {
  const res = await listRecoveryActions(route.params.id)
  return res?.data || res || []
}

function goBack() { router.push({ name: 'DeliveryRiskDashboard' }) }

function riskScoreColor(score) {
  if (score == null) return '#909399'
  if (score >= 80) return '#f56c6c'
  if (score >= 60) return '#e6a23c'
  return '#409eff'
}

function eventTimelineType(type) {
  if (type === 'delay' || type === 'exception' || type === 'customs_hold') return 'danger'
  if (type === 'arrived_delivered' || type === 'customs_clear') return 'success'
  return 'primary'
}

function openEventDialog() {
  eventVisible.value = true
}

function openActionDialog() {
  Object.assign(actionForm, { actionType: '', actionName: '', recommendation: '', expectedEta: '', expectedCost: '', expectedRecoveryQty: null, deadline: '' })
  actionVisible.value = true
}

async function handleSubmitAction() {
  try { await actionFormRef.value.validate() } catch { return }
  actionSubmitting.value = true
  try {
    await createRecoveryAction({
      riskId: route.params.id,
      actionType: actionForm.actionType,
      actionName: actionForm.actionName || null,
      recommendation: actionForm.recommendation || null,
      expectedEta: actionForm.expectedEta || null,
      expectedCost: actionForm.expectedCost || null,
      expectedRecoveryQty: actionForm.expectedRecoveryQty,
      deadline: actionForm.deadline || null
    })
    ElMessage.success('补救工单已创建')
    actionVisible.value = false
    await load()
  } catch { /* ignore */ }
  finally { actionSubmitting.value = false }
}

async function handleStart(row) {
  try { await startRecoveryAction(row.id) } catch { return }
  ElMessage.success('工单已开始执行')
  await load()
}

function openComplete(row) {
  completeTarget.value = row
  Object.assign(completeForm, { executionRemark: '', result: '' })
  completeVisible.value = true
}

async function handleSubmitComplete() {
  try { await completeFormRef.value.validate() } catch { return }
  completeSubmitting.value = true
  try {
    await completeRecoveryAction(completeTarget.value.id, completeForm)
    ElMessage.success('工单已完成，风险已缓解')
    completeVisible.value = false
    await load()
  } catch { /* ignore */ }
  finally { completeSubmitting.value = false }
}

// ==================== 8D 整改 ====================

/** 逾期判断：当前日期超过整改期限 */
function isOverdue(deadline) {
  if (!deadline) return false
  return new Date(String(deadline).slice(0, 10)) < new Date(new Date().toDateString())
}

function overdueDays(deadline) {
  const diff = new Date(new Date().toDateString()) - new Date(String(deadline).slice(0, 10))
  return Math.max(0, Math.floor(diff / 86400000))
}

/** 采购方发起 8D：默认期限 7 天 */
function openCreate8d() {
  const d = new Date()
  d.setDate(d.getDate() + 7)
  create8dForm.deadline = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  create8dVisible.value = true
}

async function handleCreate8d() {
  try { await create8dFormRef.value.validate() } catch { return }
  create8dSubmitting.value = true
  try {
    await create8d(route.params.id, { deadline: create8dForm.deadline })
    ElMessage.success('8D 整改已发起，等待供应商填写')
    create8dVisible.value = false
    await load()
  } catch { /* ignore */ }
  finally { create8dSubmitting.value = false }
}

/** 供应商填写 8D：pending/rejected 可填写，预填已有内容 */
function open8dDialog() {
  REPORT8D_STEPS.forEach((s) => { fill8dForm[s.key] = report8d.value?.[s.key] || '' })
  fill8dVisible.value = true
}

async function handleSubmit8d() {
  try { await fill8dFormRef.value.validate() } catch { return }
  fill8dSubmitting.value = true
  try {
    await submit8d(report8d.value.id, { ...fill8dForm })
    ElMessage.success('8D 报告已提交，等待采购方审核')
    fill8dVisible.value = false
    await load()
  } catch { /* ignore */ }
  finally { fill8dSubmitting.value = false }
}

/** 采购方审核通过 */
async function handleApprove8d() {
  report8dSubmitting.value = true
  try {
    await approve8d(report8d.value.id)
    ElMessage.success('8D 报告已审核通过')
    await load()
  } catch { /* ignore */ }
  finally { report8dSubmitting.value = false }
}

/** 采购方打回（原因必填） */
async function openReject8d() {
  let reason = ''
  try {
    const { value } = await ElMessageBox.prompt('请填写打回原因', '打回 8D 报告', {
      confirmButtonText: '打回',
      cancelButtonText: '取消',
      inputPlaceholder: '如：D4 根因分析不充分，请补充 5-Why',
      inputValidator: (v) => (v && v.trim() ? true : '打回原因不能为空')
    })
    reason = value.trim()
  } catch { return }
  report8dSubmitting.value = true
  try {
    await reject8d(report8d.value.id, reason)
    ElMessage.success('已打回，供应商可修改重提')
    await load()
  } catch { /* ignore */ }
  finally { report8dSubmitting.value = false }
}

async function handleClose() {
  try {
    await ElMessageBox.confirm(
      '<div style="white-space:nowrap;margin-bottom:4px">确认关闭该交付风险吗？</div><div>关闭前需已有完成的补救工单回执，或风险已被新的物流事件缓解。</div>',
      '关闭风险',
      { confirmButtonText: '确认关闭', cancelButtonText: '取消', type: 'warning', dangerouslyUseHTMLString: true, customClass: 'confirmDdMsgBox' }
    )
  } catch { return }
  closing.value = true
  try {
    await closeRisk(route.params.id)
    ElMessage.success('风险已关闭')
    await load()
  } catch { /* ignore */ }
  finally { closing.value = false }
}
</script>

<style>
.risk-dialog { width: auto !important; max-width: 560px; }
.section { margin-top: 20px; }
.section-title { font-size: 14px; font-weight: 600; color: #303133; margin-bottom: 12px; display: flex; align-items: center; gap: 8px; }
.ai-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 18px;
  padding: 0 6px;
  border-radius: 3px;
  background: #409eff;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
}
.weather-advice { padding: 12px 14px; background: #f5f7fa; border-radius: 6px; }
.advice-list { margin: 0; padding-left: 18px; color: #303133; line-height: 1.8; font-size: 14px; }
.advice-pending { margin: 0; color: #909399; font-size: 13px; }

/* 风险概要：3 列平均分配，label 统一宽度，content 均分剩余空间 */
.risk-overview .el-descriptions__table { table-layout: fixed; }
.risk-overview .el-descriptions__label { width: 120px !important; text-align: center !important; }
</style>
