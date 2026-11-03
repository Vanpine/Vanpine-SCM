<template>
  <el-dialog
    v-model="dialogVisible"
    :title="isEdit ? t('inquiry.editInquiry') : t('inquiry.createInquiry')"
    :close-on-click-modal="false"
    class="inquiry-dialog"
    @closed="onClosed"
  >
    <div v-loading="!formReady" style="min-height:200px">
    <el-form v-show="formReady" ref="formRef" :model="form" :rules="rules" :label-width="labelWidth" label-position="right" class="inquiry-form">
      <el-form-item :label="t('inquiry.requirement')" prop="requirementId">
        <el-select
          v-model="form.requirementId"
          :placeholder="t('inquiry.requirementPlaceholder')"
          :disabled="isEdit || !!initialRequirementId"
          filterable
          style="width:100%"
        >
          <el-option v-for="r in myRequirements" :key="r.id" :label="`${r.title}（${r.category || t('inquiry.noCategory')}）`" :value="r.id" />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('inquiry.inquiryTitle')" prop="title">
        <el-input v-model="form.title" :maxlength="200" :placeholder="t('inquiry.titlePlaceholder')" />
      </el-form-item>
      <el-form-item :label="t('inquiry.quoteCurrency')" prop="currency">
        <el-select v-model="form.currency" style="width:100%">
          <el-option label="CNY" value="CNY" />
          <el-option label="USD" value="USD" />
          <el-option label="EUR" value="EUR" />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('inquiry.category')" prop="category">
        <div style="display:flex;gap:8px;align-items:center">
          <el-input v-model="form.category" :maxlength="100" :placeholder="t('inquiry.categoryPlaceholder')" style="flex:1" />
          <el-button link type="primary" size="small" :loading="aiSuggestLoading" @click="handleAiSuggest">{{ t('inquiry.aiAssist') }}</el-button>
        </div>
      </el-form-item>

      <!-- AI 建议面板 -->
      <div v-if="aiSuggestResult" class="ai-suggest-panel">
        <div class="ai-suggest-header" @click="aiSuggestExpanded = !aiSuggestExpanded" style="cursor:pointer">
          <span style="display:flex;align-items:center;gap:6px">
            <el-icon><component :is="aiSuggestExpanded ? ArrowDown : ArrowRight" /></el-icon>
            {{ t('inquiry.aiSuggestTitle') }}（{{ t('inquiry.category') }}：{{ form.category }}）
          </span>
          <el-button link type="primary" size="small" :loading="aiSuggestLoading" @click.stop="handleAiSuggest">{{ t('inquiry.refresh') }}</el-button>
        </div>
        <div v-show="aiSuggestExpanded" class="ai-suggest-body">
          <div v-if="aiSuggestResult.specTemplate" class="ai-suggest-item">
            <div class="ai-suggest-row">
              <span class="ai-suggest-label">{{ t('inquiry.specSuggest') }}</span>
              <el-button link type="primary" size="small" @click="applySuggestion('spec')">{{ t('inquiry.adopt') }}</el-button>
            </div>
            <div class="ai-suggest-text">{{ aiSuggestResult.specTemplate }}</div>
          </div>
          <div v-if="aiSuggestResult.paymentSuggestions?.length" class="ai-suggest-item">
            <div class="ai-suggest-row">
              <span class="ai-suggest-label">{{ t('inquiry.paymentTerms') }}</span>
              <el-button link type="primary" size="small" @click="applySuggestion('payment')">{{ t('inquiry.adoptPreferred') }}</el-button>
            </div>
            <div class="ai-suggest-text">
              <el-tag v-for="(p, idx) in aiSuggestResult.paymentSuggestions" :key="idx" size="small" type="info" style="margin-right:6px;margin-bottom:4px;cursor:pointer" @click="form.paymentTerms = p">{{ p }}</el-tag>
            </div>
          </div>
          <div v-if="aiSuggestResult.deliverySuggestions" class="ai-suggest-item">
            <div class="ai-suggest-row">
              <span class="ai-suggest-label">{{ t('inquiry.deliverySuggest') }}</span>
            </div>
            <div class="ai-suggest-text">{{ aiSuggestResult.deliverySuggestions }}</div>
          </div>
          <div v-if="aiSuggestResult.tips" class="ai-suggest-item">
            <div class="ai-suggest-row">
              <span class="ai-suggest-label">{{ t('inquiry.purchaseTip') }}</span>
            </div>
            <div class="ai-suggest-text">{{ aiSuggestResult.tips }}</div>
          </div>
        </div>
      </div>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item :label="t('inquiry.quantity')" prop="quantity">
            <el-input v-model="form.quantity" :placeholder="t('inquiry.quantityPlaceholder')" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item :label="t('inquiry.unit')" prop="unit">
            <el-input v-model="form.unit" :maxlength="20" :placeholder="t('inquiry.unitPlaceholder')" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item :label="t('inquiry.deliveryPlace')">
        <el-input v-model="form.deliveryPlace" :maxlength="200" :placeholder="t('inquiry.deliveryPlacePlaceholder')" />
      </el-form-item>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item :label="t('inquiry.deliveryDate')">
            <el-date-picker v-model="form.deliveryDate" type="date" style="width:100%" value-format="YYYY-MM-DD" :placeholder="t('inquiry.optional')" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item :label="t('inquiry.quoteDeadline')" prop="quoteDeadline">
            <el-date-picker v-model="form.quoteDeadline" type="datetime" style="width:100%" value-format="YYYY-MM-DDTHH:mm:ss" :placeholder="t('inquiry.quoteDeadlinePlaceholder')" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item :label="t('inquiry.paymentTerms')">
        <el-autocomplete v-model="form.paymentTerms" :fetch-suggestions="filterPaymentTerms" :placeholder="t('inquiry.paymentTermsPlaceholder')" style="width:100%" />
      </el-form-item>

      <el-form-item :label="t('inquiry.specTerms')">
        <el-input v-model="form.specText" type="textarea" :rows="3" :placeholder="t('inquiry.specTextPlaceholder')" />
      </el-form-item>
      <el-form-item :label="t('inquiry.attachment')">
        <div>
          <el-upload
            :show-file-list="false"
            :http-request="handleAttachmentUpload"
            :before-upload="beforeAttachmentUpload"
            accept=".pdf,.doc,.docx,.xls,.xlsx,.jpg,.png,.dwg,.step,.stp"
          >
            <el-button type="primary" plain :loading="attachmentUploading">
              <el-icon><Plus /></el-icon> {{ form.attachmentUrl ? t('inquiry.reupload') : t('inquiry.uploadFile') }}
            </el-button>
          </el-upload>
          <div v-if="form.attachmentUrl" style="margin-top:6px;display:flex;align-items:center;gap:4px;color:#67c23a;font-size:13px">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/></svg>
            {{ getFileName(form.attachmentUrl) }}
          </div>
          <div v-if="!form.attachmentUrl" class="el-upload__tip" style="margin-top:4px">{{ t('inquiry.uploadTipForm') }}</div>
        </div>
      </el-form-item>

      <!-- 供应商选择器（双 Tab） -->
      <el-form-item :label="t('inquiry.inviteSuppliers')" prop="supplierTenantIds" class="inquiry-supplier-item">
        <el-tabs v-model="supplierTab" type="card" style="width:100%">
          <!-- Tab 1: 撮合结果 -->
          <el-tab-pane name="match">
            <template #label>
              <span style="display:inline-flex;align-items:center;gap:4px">{{ t('inquiry.matchResult') }}<sup v-if="matchedSuppliers.length" class="tab-badge">{{ matchedSuppliers.length }}</sup></span>
            </template>
            <div v-if="matchedSuppliers.length === 0" style="color:#909399;font-size:13px;padding:16px 0;text-align:center">
              <el-icon :size="32" style="color:#dcdfe6"><FolderOpened /></el-icon>
              <div style="margin-top:8px">{{ t('inquiry.noMatchedConnections') }}</div>
            </div>
            <div v-else class="supplier-check-list">
              <el-checkbox v-for="s in matchedSuppliers" :key="s.tenantId" v-model="selectedFromMatch" :label="s.tenantId" :value="s.tenantId" :disabled="isSupplierDisabled(s)" class="supplier-check-item">
                <div class="supplier-row">
                  <span class="supplier-name">{{ s.companyName }}</span>
                  <el-tag v-if="invitedSupplierIds.has(s.tenantId)" size="small" type="info">{{ t('inquiry.awardedTag') }}</el-tag>
                  <el-tag v-else-if="activeInquirySupplierIds.has(s.tenantId)" size="small" type="warning">{{ t('inquiry.inquiring') }}</el-tag>
                  <el-tag v-else-if="String(s.tenantId) === String(myTenantId)" size="small" type="danger">{{ t('inquiry.myCompany') }}</el-tag>
                </div>
              </el-checkbox>
            </div>
          </el-tab-pane>

          <!-- Tab 2: 手动添加 -->
          <el-tab-pane name="manual">
            <template #label>
              <span>{{ t('inquiry.manualAdd') }}</span>
            </template>
            <div style="display:flex;gap:8px;margin-bottom:12px">
              <el-input v-model="supplierKeyword" :placeholder="t('inquiry.searchSupplierPlaceholder')" @keyup.enter="doSearchSupplier">
                <template #prefix><el-icon><Search /></el-icon></template>
              </el-input>
              <el-button type="primary" @click="doSearchSupplier">{{ t('common.search') }}</el-button>
            </div>
            <div v-if="supplierKeyword && searchedSuppliers.length === 0 && !searchingSupplier" style="color:#909399;font-size:13px;padding:16px 0;text-align:center">{{ t('inquiry.noSupplierFound') }}</div>
            <div v-else-if="searchedSuppliers.length > 0" class="supplier-check-list">
              <el-checkbox v-for="s in searchedSuppliers" :key="s.tenantId" v-model="selectedFromManual" :label="s.tenantId" :value="s.tenantId" :disabled="isSupplierDisabled(s)" class="supplier-check-item">
                <div class="supplier-row">
                  <span class="supplier-name">{{ s.companyName }}</span>
                  <el-tag v-if="invitedSupplierIds.has(s.tenantId)" size="small" type="info">{{ t('inquiry.awardedTag') }}</el-tag>
                  <el-tag v-else-if="activeInquirySupplierIds.has(s.tenantId)" size="small" type="warning">{{ t('inquiry.inquiring') }}</el-tag>
                  <el-tag v-else-if="String(s.tenantId) === String(myTenantId)" size="small" type="danger">{{ t('inquiry.myCompany') }}</el-tag>
                </div>
              </el-checkbox>
            </div>
          </el-tab-pane>

          <!-- Tab 3: AI 推荐 -->
          <el-tab-pane name="ai">
            <template #label>
              <span style="display:inline-flex;align-items:center;gap:4px">{{ t('inquiry.aiRecommend') }}<sup class="ai-badge">NEW</sup></span>
            </template>
            <div v-loading="aiRecommendLoading" style="min-height:80px">
              <div v-if="aiRecommendedSuppliers.length === 0 && !aiRecommendLoading" style="color:#909399;font-size:13px;padding:16px 0;text-align:center">
                <p style="margin-bottom:8px">{{ t('inquiry.aiRecommendDesc') }}</p>
                <el-button type="primary" plain size="small" @click="handleAiRecommend">{{ t('inquiry.startAiRecommend') }}</el-button>
              </div>
              <div v-else class="supplier-check-list">
                <el-checkbox v-for="s in aiRecommendedSuppliers" :key="s.tenantId" v-model="selectedFromAi" :label="s.tenantId" :value="s.tenantId" :disabled="isSupplierDisabled(s)" class="supplier-check-item">
                  <div class="supplier-row">
                    <span class="supplier-name">{{ s.companyName }}</span>
                    <el-tag size="small" :type="s.matchScore >= 80 ? 'success' : s.matchScore >= 60 ? 'warning' : 'info'">{{ t('inquiry.matchScore') }} {{ s.matchScore }}</el-tag>
                    <el-tag v-if="invitedSupplierIds.has(s.tenantId)" size="small" type="info" style="margin-left:4px">{{ t('inquiry.awardedTag') }}</el-tag>
                    <el-tag v-else-if="activeInquirySupplierIds.has(s.tenantId)" size="small" type="warning" style="margin-left:4px">{{ t('inquiry.inquiring') }}</el-tag>
                    <el-tag v-else-if="String(s.tenantId) === String(myTenantId)" size="small" type="danger" style="margin-left:4px">{{ t('inquiry.myCompany') }}</el-tag>
                  </div>
                  <div v-if="s.matchReasons && s.matchReasons.length" class="ai-match-reasons">
                    <span v-for="(reason, idx) in s.matchReasons.slice(0, 2)" :key="idx" class="ai-reason-tag">{{ reason }}</span>
                  </div>
                </el-checkbox>
              </div>
              <div v-if="aiRecommendedSuppliers.length > 0" style="text-align:center;margin-top:8px">
                <el-button link type="primary" size="small" :loading="aiRecommendLoading" @click="handleAiRecommend">{{ t('inquiry.reRecommend') }}</el-button>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </el-form-item>
    </el-form>
    </div>

    <template #footer>
      <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">{{ t('common.confirm') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Search, Plus, FolderOpened, ArrowRight, ArrowDown } from '@element-plus/icons-vue'
import { createInquiry, updateInquiry, getInquiryDetail, searchSuppliers, getInvitedSuppliers, getActiveInquirySuppliers, recommendSuppliers, suggestInquiryFields } from '@/api/procurement'
import { listConnections, getRequirementDetail, listMyRequirements } from '@/api/match'
import { uploadFile } from '@/api/upload'
import { useAuthStore } from '@/stores/auth'
import { getFileName } from '@/utils/file'

const props = defineProps({
  visible: { type: Boolean, default: false },
  inquiryId: { type: Number, default: null },        // 编辑模式
  initialData: { type: Object, default: () => ({}) }  // 对接桥预填数据
})

const emit = defineEmits(['update:visible', 'submit'])

const { t, locale } = useI18n()
const labelWidth = computed(() => locale.value === 'en' ? '160px' : '110px')

const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
})

const isEdit = computed(() => props.inquiryId != null)

// 表单加载状态：数据就绪后才展示表单，避免空白闪烁
const formReady = ref(false)

// 表单
const formRef = ref(null)
const submitting = ref(false)
const form = reactive({
  requirementId: null, title: '', currency: 'CNY', category: '', quantity: '', unit: '', deliveryPlace: '',
  deliveryDate: '', quoteDeadline: '', paymentTerms: '', specText: '', supplierTenantIds: []
})
const rules = {
  requirementId: [{ required: true, message: () => t('message.selectRequirement'), trigger: 'change' }],
  title: [{ required: true, message: () => t('message.enterTitle'), trigger: 'blur' }],
  category: [{ required: true, message: () => t('message.enterCategory'), trigger: 'blur' }],
  quantity: [{ required: true, message: () => t('message.enterQuantity'), trigger: 'blur' }],
  unit: [{ required: true, message: () => t('message.enterUnit'), trigger: 'blur' }],
  supplierTenantIds: [{ required: true, message: () => t('message.selectSupplier'), trigger: 'change', type: 'array', min: 1 }],
  quoteDeadline: [{ required: true, message: () => t('message.selectDeadline'), trigger: 'change' }]
}

// 付款条件
const PAYMENT_TERMS = computed(() => [
  { value: t('paymentTerm.net30') }, { value: t('paymentTerm.net60') }, { value: t('paymentTerm.net90') },
  { value: t('paymentTerm.cod') }, { value: t('paymentTerm.advance') }, { value: t('paymentTerm.advance30') },
  ])
function filterPaymentTerms(keyword, cb) {
  if (!keyword) { cb(PAYMENT_TERMS.value); return }
  cb(PAYMENT_TERMS.value.filter(v => v.value.includes(keyword)))
}

// 附件上传
const attachmentUploading = ref(false)
function beforeAttachmentUpload(file) {
  if (file.size > 10 * 1024 * 1024) { ElMessage.error(t('message.fileTooLarge')); return false }
  return true
}
async function handleAttachmentUpload({ file }) {
  attachmentUploading.value = true
  try {
    const { data } = await uploadFile(file, 'inquiry')
    // 只存 oss_key：签名 URL 会过期，存库必然变死链；后端返回 VO 时按 key 现签
    form.attachmentUrl = data?.key || ''
  } catch { /* ignore */ }
  finally { attachmentUploading.value = false }
}

// 供应商选择
const supplierTab = ref('match')
const supplierKeyword = ref('')
const searchingSupplier = ref(false)
const searchedSuppliers = ref([])
const matchedSuppliers = ref([])
const selectedFromMatch = ref([])
const selectedFromManual = ref([])

// AI 推荐供应商
const aiRecommendedSuppliers = ref([])
const aiRecommendLoading = ref(false)
const selectedFromAi = ref([])

const authStore = useAuthStore()
/** 当前用户租户ID（本公司禁选） */
const myTenantId = computed(() => authStore.user?.tenantId)
/** 我的 open 采购需求（关联需求下拉） */
const myRequirements = ref([])
/** 对接桥带入的需求ID（锁定下拉） */
const initialRequirementId = computed(() => props.initialData?.requirementId || null)
/** 已授标过的供应商租户ID集合（授标合作过即禁选） */
const invitedSupplierIds = ref(new Set())
/** 该需求下进行中询盘已邀请的供应商租户ID集合（询价中即禁选） */
const activeInquirySupplierIds = ref(new Set())

// 合并三个 Tab 的选中项，去重
const allSelectedTenantIds = computed(() => {
  const set = new Set([...selectedFromMatch.value, ...selectedFromManual.value, ...selectedFromAi.value])
  return [...set]
})

// 同步合并结果到 form.supplierTenantIds
watch(allSelectedTenantIds, (ids) => {
  form.supplierTenantIds = ids
}, { deep: true })

// 加载我的 open 采购需求（关联需求下拉）
async function loadMyRequirements() {
  try {
    const res = await listMyRequirements({ status: 'open', pageSize: 100 })
    myRequirements.value = res?.data?.records || res?.records || []
  } catch { myRequirements.value = [] }
}

// 加载撮合结果（已接受的对接记录）
async function loadMatchedSuppliers() {
  try {
    const res = await listConnections({ status: 'accepted', role: 'buyer', pageSize: 200 })
    const records = res?.data?.records || res?.records || []
    // 提取供应商信息：每个 accepted connection 对应一个供应商
    const supplierMap = new Map()
    for (const conn of records) {
      if (!supplierMap.has(conn.supplierTenantId)) {
        supplierMap.set(conn.supplierTenantId, {
          tenantId: conn.supplierTenantId,
          companyName: conn.supplierCompanyName || `${t('inquiry.supplierFallback')}#${conn.supplierTenantId}`,
          connectionId: conn.id,
          requirementId: conn.requirementId
        })
      }
    }
    matchedSuppliers.value = [...supplierMap.values()]
  } catch { /* ignore */ }
}

// 该需求下已授标过的供应商集合（授标合作过即禁选；无需求关联则不启用）
async function loadInvitedSuppliers() {
  try {
    const reqId = props.initialData?.requirementId
    if (!reqId) { invitedSupplierIds.value = new Set(); return }
    const res = await getInvitedSuppliers(reqId)
    invitedSupplierIds.value = new Set(res?.data || res || [])
  } catch { invitedSupplierIds.value = new Set() }
}

// 该需求下进行中询盘已邀请的供应商集合（询价中即禁选；无需求关联则不启用）
async function loadActiveInquirySuppliers() {
  try {
    const reqId = props.initialData?.requirementId
    if (!reqId) { activeInquirySupplierIds.value = new Set(); return }
    const res = await getActiveInquirySuppliers(reqId)
    activeInquirySupplierIds.value = new Set(res?.data || res || [])
  } catch { activeInquirySupplierIds.value = new Set() }
}

// 供应商是否禁选：已授标 / 进行中询盘已邀请 / 本公司（String() 兼容后端 Long→String 序列化）
function isSupplierDisabled(s) {
  return invitedSupplierIds.value.has(s.tenantId)
    || activeInquirySupplierIds.value.has(s.tenantId)
    || String(s.tenantId) === String(myTenantId.value)
}

// 从对接记录跳转时，自动勾选该需求下所有已对接且可邀请的供应商（一次询盘带全部，避免逐家创建多条）
function autoSelectSuppliers() {
  const data = props.initialData
  if (!data || Object.keys(data).length === 0) return
  const reqId = data.requirementId
  const ids = matchedSuppliers.value
    .filter(s => {
      if (isSupplierDisabled(s)) return false
      // 有需求维度则只勾选该需求的供应商；无则勾选全部可邀请的（String() 兼容 Long→String）
      if (reqId) return String(s.requirementId) === String(reqId)
      return true
    })
    .map(s => s.tenantId)
  if (ids.length) {
    selectedFromMatch.value = ids
    form.supplierTenantIds = ids
  }
}

// 搜索供应商（手动添加 Tab）
async function doSearchSupplier() {
  if (!supplierKeyword.value.trim()) return
  searchingSupplier.value = true
  try {
    const res = await searchSuppliers({ keyword: supplierKeyword.value.trim(), pageSize: 50 })
    const records = res?.data?.records || res?.records || []
    searchedSuppliers.value = records.map(s => ({
      tenantId: s.tenantId,
      companyName: s.companyName
    }))
  } catch { /* ignore */ }
  finally { searchingSupplier.value = false }
}

// AI 推荐供应商
async function handleAiRecommend() {
  if (!form.title && !form.category) {
    ElMessage.warning(t('message.enterTitleAndCategory'))
    return
  }
  aiRecommendLoading.value = true
  try {
    const res = await recommendSuppliers({
      title: form.title,
      category: form.category,
      quantity: form.quantity,
      unit: form.unit,
      specText: form.specText
    })
    const list = res?.data || res || []
    aiRecommendedSuppliers.value = list.map(s => ({
      tenantId: s.tenantId,
      companyName: s.companyName,
      matchScore: s.matchScore || 0,
      matchReasons: s.matchReasons || []
    }))
    if (aiRecommendedSuppliers.value.length === 0) {
      ElMessage.info(t('message.aiNoMatch'))
    }
  } catch {
    ElMessage.warning(t('message.aiUnavailable'))
  } finally {
    aiRecommendLoading.value = false
  }
}

// AI 询盘内容辅助
const aiSuggestLoading = ref(false)
const aiSuggestResult = ref(null)
const aiSuggestExpanded = ref(true)

async function handleAiSuggest() {
  if (!form.category) {
    ElMessage.warning(t('message.enterCategoryFirst'))
    return
  }
  aiSuggestLoading.value = true
  try {
    const res = await suggestInquiryFields(form.title, form.category, form.quantity, form.unit)
    aiSuggestResult.value = res?.data || res
  } catch {
    ElMessage.warning(t('message.aiAssistUnavailable'))
  } finally {
    aiSuggestLoading.value = false
  }
}

function applySuggestion(type) {
  if (!aiSuggestResult.value) return
  switch (type) {
    case 'spec':
      form.specText = aiSuggestResult.value.specTemplate || form.specText
      break
    case 'payment':
      if (aiSuggestResult.value.paymentSuggestions?.length) {
        form.paymentTerms = aiSuggestResult.value.paymentSuggestions[0]
      }
      break
    case 'delivery':
      // 仅做文本提示，交付日期需人工确认
      break
  }
}

// 加载编辑数据
async function loadEditData() {
  if (!props.inquiryId) return
  try {
    const res = await getInquiryDetail(props.inquiryId)
    const detail = res?.data || res
    Object.assign(form, {
      requirementId: detail.requirementId || null,
      title: detail.title || '',
      currency: detail.currency || 'CNY',
      category: detail.category || '',
      quantity: detail.quantity || '',
      unit: detail.unit || '',
      deliveryPlace: detail.deliveryPlace || '',
      deliveryDate: detail.deliveryDate || '',
      quoteDeadline: detail.quoteDeadline || '',
      paymentTerms: detail.paymentTerms || '',
      specText: detail.specText || '',
      // 附件回填：用 key（VO 同时返回 attachmentKey），URL 会过期不能回传
      attachmentUrl: detail.attachmentKey || '',
      supplierTenantIds: detail.supplierTenantIds || []
    })
    // 编辑模式下预选已邀请的供应商
    if (detail.invites) {
      selectedFromMatch.value = detail.invites.map(inv => inv.supplierTenantId)
    }
  } catch { /* ignore */ }
}

// 预填对接桥数据 + 采购需求数据
async function applyInitialData() {
  const data = props.initialData
  if (!data || Object.keys(data).length === 0) return

  // 对接桥传来的 title 先填上
  if (data.title) form.title = data.title
  if (data.supplierTenantId) {
    // 桥接供应商自动勾选（已对接待邀请的供应商直接预选）
    form.supplierTenantIds = [data.supplierTenantId]
    selectedFromMatch.value = [data.supplierTenantId]
    // 确保桥接供应商在 matchedSuppliers 中，tag 能显示企业名而非 ID（String() 兼容 Long→String）
    const alreadyInList = matchedSuppliers.value.some(s => String(s.tenantId) === String(data.supplierTenantId))
    if (!alreadyInList && data.supplierName) {
      matchedSuppliers.value.push({ tenantId: data.supplierTenantId, companyName: data.supplierName })
    }
  }

  // 从采购需求自动填充品类、数量、单位、规格条款
  const reqId = data.requirementId
  if (reqId) {
    // 强关联需求：对接桥带入需求ID，锁定下拉
    form.requirementId = reqId
    try {
      const res = await getRequirementDetail(reqId)
      // API 返回 { code, data: { category, quantity, unit, description, ... } }
      const req = res?.data || res
      if (req) {
        if (req.category) form.category = req.category
        if (req.quantity) form.quantity = req.quantity
        if (req.unit) form.unit = req.unit
        if (req.description) form.specText = req.description
        if (!data.title && req.title) form.title = req.title
      }
    } catch (e) {
      // 静默失败不影响表单打开
    }
  }
}

// 重置表单
function resetForm() {
  formRef.value?.resetFields()
  Object.assign(form, {
    requirementId: null, title: '', currency: 'CNY', category: '', quantity: '', unit: '', deliveryPlace: '',
    deliveryDate: '', quoteDeadline: '', paymentTerms: '', specText: '', attachmentUrl: '', supplierTenantIds: []
  })
  selectedFromMatch.value = []
  selectedFromManual.value = []
  selectedFromAi.value = []
  aiRecommendedSuppliers.value = []
  aiSuggestResult.value = null
  aiSuggestExpanded.value = true
  supplierTab.value = 'match'
  supplierKeyword.value = ''
  searchedSuppliers.value = []
}

// 弹窗打开时加载供应商列表 + 编辑数据
watch(() => props.visible, async (val) => {
  if (val) {
    formReady.value = false
    resetForm()
    await loadMyRequirements()
    await loadMatchedSuppliers()
    await loadInvitedSuppliers()
    await loadActiveInquirySuppliers()
    if (isEdit.value) {
      await loadEditData()
    }
    // 如果 initialData 已经有值，立即填充（处理 visible 变化晚于 initialData 的情况）
    if (props.initialData && Object.keys(props.initialData).length > 0) {
      await applyInitialData()
    }
    // 方案A：从对接记录跳转时自动勾选该需求全部可邀请供应商（编辑模式回显已邀请的，不做全选）
    if (!isEdit.value) autoSelectSuppliers()
    formReady.value = true
  }
}, { immediate: true })

// 监听 initialData 变化：对接桥可能在弹窗已打开后才传入数据
watch(() => props.initialData, async (data) => {
  if (data && Object.keys(data).length > 0 && dialogVisible.value) {
    formReady.value = false
    await applyInitialData()
    formReady.value = true
  }
}, { deep: true })

function onClosed() {
  formReady.value = false
  resetForm()
}

async function handleSubmit() {
  try {
    await formRef.value.validate()
  } catch { return }
  submitting.value = true
  try {
    const payload = {
      requirementId: form.requirementId,
      title: form.title,
      currency: form.currency || 'CNY',
      category: form.category || null,
      quantity: form.quantity || null,
      unit: form.unit || null,
      deliveryPlace: form.deliveryPlace || null,
      deliveryDate: form.deliveryDate || null,
      quoteDeadline: form.quoteDeadline || null,
      paymentTerms: form.paymentTerms || null,
      specText: form.specText || null,
      // 后端 CreateInquiryDTO 字段名为 attachmentKey（存 OSS 对象路径，非签名 URL）
      attachmentKey: form.attachmentUrl || null,
      supplierTenantIds: allSelectedTenantIds.value
    }
    // 创建时传 connectionId 等附加信息（对接桥）
    if (!isEdit.value && props.initialData?.connectionId) {
      payload.connectionId = props.initialData.connectionId
    }
    if (isEdit.value) {
      await updateInquiry(props.inquiryId, payload)
      ElMessage.success(t('message.inquiryUpdated'))
    } else {
      await createInquiry(payload)
      ElMessage.success(t('message.inquiryCreated'))
    }
    dialogVisible.value = false
    emit('submit')
  } finally { submitting.value = false }
}
</script>

<style>
.inquiry-dialog {
  width: auto !important;
  max-width: 720px;
}
.inquiry-form .el-form-item.is-required .el-form-item__label::before {
  margin-right: 1px !important;
}
.inquiry-supplier-item .el-form-item__label {
  align-self: flex-start !important;
  margin-top: 6px !important;
}
.supplier-check-list {
  display: flex;
  flex-direction: column;
}
.supplier-check-item {
  padding: 8px 12px !important;
  margin: 0 !important;
  border-bottom: 1px solid #ebeef5;
  transition: background 0.2s;
}
.supplier-check-item:hover {
  background: #f5f7fa;
}
.supplier-check-item:last-child {
  border-bottom: none;
}
.supplier-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}
.supplier-name {
  font-size: 14px;
  color: #303133;
}
.tab-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: #409eff;
  color: #fff;
  font-size: 11px;
  line-height: 15px;
  vertical-align: middle;
}
.ai-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 14px;
  padding: 0 4px;
  border-radius: 4px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
  font-size: 10px;
  line-height: 13px;
  vertical-align: middle;
}
.ai-match-reasons {
  margin-top: 4px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.ai-reason-tag {
  font-size: 12px;
  color: #909399;
  background: #f5f7fa;
  padding: 1px 6px;
  border-radius: 3px;
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* AI 询盘辅助 */
.ai-suggest-panel {
  border: 1px solid #d3d9f5;
  border-radius: 6px;
  background: #f8f9ff;
  margin-bottom: 16px;
  overflow: hidden;
}
.ai-suggest-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 14px;
  background: #eef0fd;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}
.ai-suggest-body {
  padding: 10px 14px;
}
.ai-suggest-item {
  margin-bottom: 10px;
}
.ai-suggest-item:last-child {
  margin-bottom: 0;
}
.ai-suggest-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}
.ai-suggest-label {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
}
.ai-suggest-text {
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}
</style>
