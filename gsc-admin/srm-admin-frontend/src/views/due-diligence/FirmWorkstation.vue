<template>
  <div>
    <el-card class="search-card">
      <span class="text-base font-semibold text-gray-700">事务所工作台</span>
    </el-card>

    <el-card class="content-card">
      <div v-if="!list.length && !loading" class="text-center py-20 text-gray-400">
        暂无分配的尽调任务
      </div>

      <div v-else class="firm-layout">
        <!-- 左侧：企业列表 -->
        <div class="firm-left">
          <div class="firm-left-title">尽调企业 ({{ list.length }})</div>
          <div
            v-for="item in list"
            :key="item.applicationId"
            :class="['firm-company-item', { active: String(selected?.applicationId) === String(item.applicationId) }]"
            @click="selectCompany(item)"
          >
            <div class="company-name">{{ item.companyName || '-' }}</div>
            <el-tag size="small" :type="FIRM_STATUS_MAP[item.ddFirmStatus]?.type || 'info'">
              {{ FIRM_STATUS_MAP[item.ddFirmStatus]?.label || item.ddFirmStatus || '已分配' }}
            </el-tag>
          </div>
        </div>

        <!-- 右侧：详情 -->
        <div class="firm-right" v-if="selected">
          <!-- 分配信息 -->
          <el-descriptions v-if="selected.ddFirmAssignedAt" title="尽调任务" :column="2" border size="small" class="mb-4">
            <el-descriptions-item label="分配时间">{{ formatTime(selected.ddFirmAssignedAt) }}</el-descriptions-item>
            <el-descriptions-item label="截止日期">
              <span :class="{ 'text-red-500': isOverdue }">{{ selected.ddFirmDeadline || '未设定' }}</span>
            </el-descriptions-item>
            <el-descriptions-item v-if="selected.ddFirmInstruction" label="特殊要求" :span="2">
              {{ selected.ddFirmInstruction }}
            </el-descriptions-item>
          </el-descriptions>

          <!-- 企业信息 -->
          <el-descriptions title="企业信息" :column="2" border size="small" class="mb-4">
            <el-descriptions-item label="企业名称">{{ selected.companyName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="注册号">{{ selected.regNumber || '-' }}</el-descriptions-item>
            <el-descriptions-item label="所在国家">{{ selected.country || '-' }}</el-descriptions-item>
            <el-descriptions-item label="省份 / 城市">{{ selected.province || '-' }} / {{ selected.city || '-' }}</el-descriptions-item>
            <el-descriptions-item label="成立年份">{{ selected.establishedYear || '-' }}</el-descriptions-item>
            <el-descriptions-item label="法人代表">{{ selected.legalPersonName || '-' }}</el-descriptions-item>
            <el-descriptions-item v-if="selected.website" label="官网" :span="2">
              <el-link type="primary" :href="selected.website" target="_blank">{{ selected.website }}</el-link>
            </el-descriptions-item>
            <el-descriptions-item v-if="selected.description" label="企业简介" :span="2">
              {{ selected.description }}
            </el-descriptions-item>
          </el-descriptions>

          <!-- 经营规模 -->
          <el-descriptions title="经营规模" :column="2" border size="small" class="mb-4">
            <el-descriptions-item label="员工人数">
              {{ EMPLOYEE_COUNT_OPTIONS.find(o => o.value === selected.employeeCount)?.label || selected.employeeCount || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="年营业额">
              {{ annualRevenueDisplay(selected) }}
            </el-descriptions-item>
            <el-descriptions-item label="申请身份">
              {{ selected.applicantRoleLabel || ROLE_LABEL_MAP[selected.applicantRole] || selected.applicantRole || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="法人身份证号">{{ selected.legalPersonId || '-' }}</el-descriptions-item>
          </el-descriptions>

          <!-- 联系信息 -->
          <el-descriptions title="联系信息" :column="2" border size="small" class="mb-4">
            <el-descriptions-item label="联系人">{{ selected.contactName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="职位">{{ selected.contactTitle || '-' }}</el-descriptions-item>
            <el-descriptions-item label="邮箱">{{ selected.contactEmail || '-' }}</el-descriptions-item>
            <el-descriptions-item label="电话">{{ selected.contactPhone || '-' }}</el-descriptions-item>
          </el-descriptions>

          <!-- 采购方信息 -->
          <el-descriptions v-if="isBuyer" title="采购信息" :column="2" border size="small" class="mb-4">
            <el-descriptions-item v-if="selected.buyCategory" label="采购品类">{{ selected.buyCategory }}</el-descriptions-item>
            <el-descriptions-item v-if="selected.buyVolumeAmount || selected.buyVolume" label="年采购额">
              {{ purchaseVolumeDisplay(selected) }}
            </el-descriptions-item>
            <el-descriptions-item v-if="selected.buyMarket" label="目标市场">{{ selected.buyMarket }}</el-descriptions-item>
            <el-descriptions-item v-if="selected.industry" label="所属行业">{{ selected.industry }}</el-descriptions-item>
            <el-descriptions-item v-if="selected.procurementDetail" label="采购明细" :span="2">{{ selected.procurementDetail }}</el-descriptions-item>
            <el-descriptions-item v-if="selected.supplierRequirement" label="供应商要求" :span="2">{{ selected.supplierRequirement }}</el-descriptions-item>
          </el-descriptions>

          <!-- 供应方信息 -->
          <el-descriptions v-if="isSupplier" title="供应信息" :column="2" border size="small" class="mb-4">
            <el-descriptions-item v-if="selected.supCategory" label="生产品类">{{ selected.supCategory }}</el-descriptions-item>
            <el-descriptions-item v-if="selected.supCapacity" label="年产能">{{ selected.supCapacity }}</el-descriptions-item>
            <el-descriptions-item v-if="selected.supCerts" label="资质认证">{{ selected.supCerts }}</el-descriptions-item>
            <el-descriptions-item v-if="selected.supExport" label="出口经验">{{ supExportLabel(selected.supExport) }}</el-descriptions-item>
            <el-descriptions-item v-if="selected.mainProducts" label="主要产品" :span="2">{{ selected.mainProducts }}</el-descriptions-item>
            <el-descriptions-item v-if="selected.keyClients" label="主要客户" :span="2">{{ selected.keyClients }}</el-descriptions-item>
            <el-descriptions-item v-if="selected.exportMarkets" label="出口市场">{{ selected.exportMarkets }}</el-descriptions-item>
            <el-descriptions-item v-if="selected.managementCert" label="管理认证">{{ optionLabels(MANAGEMENT_CERT_OPTIONS, selected.managementCert) }}</el-descriptions-item>
            <el-descriptions-item v-if="selected.productCert" label="产品认证">{{ optionLabels(PRODUCT_CERT_OPTIONS, selected.productCert) }}</el-descriptions-item>
          </el-descriptions>

          <!-- 附件材料 -->
          <el-descriptions v-if="hasAttachments" title="附件材料" :column="1" border size="small" class="mb-4">
            <el-descriptions-item v-if="selected.businessLicenseUrl" label="营业执照">
              <el-link type="primary" :href="getFileUrl(selected.businessLicenseUrl)" target="_blank">
                {{ getFileName(selected.businessLicenseUrl) }}
              </el-link>
            </el-descriptions-item>
            <el-descriptions-item v-if="selected.financialReportUrl" label="审计报告">
              <el-link type="primary" :href="getFileUrl(selected.financialReportUrl)" target="_blank">
                {{ getFileName(selected.financialReportUrl) }}
              </el-link>
            </el-descriptions-item>
            <el-descriptions-item v-if="selected.shareholderInfo" label="股权结构">
              <el-link type="primary" :href="getFileUrl(selected.shareholderInfo)" target="_blank">
                {{ getFileName(selected.shareholderInfo) }}
              </el-link>
            </el-descriptions-item>
            <el-descriptions-item v-if="legalFile" label="诉讼文书">
              <el-link type="primary" :href="legalFile.url" target="_blank">{{ legalFile.name }}</el-link>
            </el-descriptions-item>
            <el-descriptions-item v-if="selected.attachments?.length" label="附件材料">
              <template v-for="(att, idx) in selected.attachments" :key="idx">
                <el-link :href="att.url" target="_blank" type="primary" class="mr-4">
                  {{ att.fileName || '附件' }}
                </el-link>
              </template>
            </el-descriptions-item>
          </el-descriptions>

          <!-- 核查清单（按类别分组） -->
          <h4 class="section-title">核查清单</h4>
          <div class="checklist">
            <template v-for="group in checklistGroups" :key="group.name">
              <div class="checklist-group-title">{{ group.name }}</div>
              <div v-for="item in group.items" :key="item.itemCode" :class="['checklist-item', { checked: item.checked }]">
                <el-checkbox v-model="item.checked" :disabled="isDisabled">{{ item.itemName }}</el-checkbox>
                <template v-if="item.checked">
                  <el-input v-if="item.remark != null" v-model="item.remark" :disabled="isDisabled" placeholder="核查备注（选填）" size="small" class="checklist-remark" />
                  <el-button v-else-if="!isDisabled" link type="primary" size="small" class="checklist-add-remark" @click="item.remark = ''">+ 添加备注</el-button>
                </template>
              </div>
            </template>
            <div v-if="!isDisabled" class="checklist-save-row">
              <el-button type="primary" :loading="savingChecklist" @click="saveChecklist">保存核查清单</el-button>
            </div>
          </div>

          <!-- 提交 -->
          <h4 class="section-title">提交尽调结果</h4>
          <div class="submit-area">
            <el-input v-model="progress" type="textarea" :rows="3" :disabled="isDisabled" placeholder="尽调工作备注（必填，概述核查过程和结论）" class="mb-3" />
            <div class="submit-row">
              <!-- 可编辑状态：上传 + 提交 + 退回 -->
              <template v-if="!isDisabled">
                <el-upload ref="uploadReport" :http-request="handleUpload" :show-file-list="false" accept=".pdf" class="submit-upload">
                  <el-button type="primary" plain :icon="Upload">{{ reportUrl ? '更换尽调报告' : '上传尽调报告 PDF' }}</el-button>
                </el-upload>
                <el-button type="success" plain :loading="generating" @click="handleDownloadTemplate">下载报告模板</el-button>
                <div class="submit-actions">
                  <el-button type="danger" plain :loading="returning" @click="returnDialogVisible = true">退回补充</el-button>
                  <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="handleSubmit">提交尽调结果</el-button>
                </div>
              </template>
              <!-- 已退回 -->
              <template v-else-if="selected?.ddFirmStatus === 'returned'">
                <el-tag type="danger" size="large" style="margin-left: auto;">已退回企业补充中</el-tag>
              </template>
              <!-- 待管理员确认 -->
              <template v-else-if="selected?.ddFirmStatus === 'submitted'">
                <div class="report-info-row">
                  <span class="report-info-label">尽调报告</span>
                  <el-link type="primary" :href="reportUrl" target="_blank">{{ reportName || '查看报告' }}</el-link>
                </div>
                <div class="report-info-row" style="margin-top: 4px;">
                  <span class="report-info-label">提交时间</span>
                  <span>{{ formatTime(selected.ddReportTime) }}</span>
                </div>
                <el-tag type="info" size="large" style="margin-left: auto;">等待管理员确认</el-tag>
              </template>
              <!-- 已完成 -->
              <template v-else>
                <div class="report-info-row">
                  <span class="report-info-label">尽调报告</span>
                  <el-link type="primary" :href="reportUrl" target="_blank">{{ reportName || '查看报告' }}</el-link>
                </div>
                <div class="report-info-row" style="margin-top: 4px;">
                  <span class="report-info-label">完成时间</span>
                  <span>{{ formatTime(selected.ddReportTime) }}</span>
                </div>
                <el-tag type="success" size="large" style="margin-left: auto;">已完成提交</el-tag>
              </template>
            </div>
            <div v-if="reportUrl && !isDisabled" class="uploaded-file-name">
              <svg class="upload-check" viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="10" fill="#16a34a"/><path d="M8 12l3 3 5-5" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
              <span>{{ reportName || getFileName(reportUrl) || '已上传' }}</span>
            </div>
          </div>
        </div>

        <div v-else class="firm-right flex items-center justify-center text-gray-400 text-sm">
          请选择左侧企业开始核查
        </div>
      </div>
    </el-card>

    <!-- 退回补充弹窗 -->
    <el-dialog v-model="returnDialogVisible" title="退回补充材料" :close-on-click-modal="false" class="return-dialog">
      <el-form label-width="85px" class="return-form" label-position="right">
        <el-form-item label="企业名称">
          <span class="text-sm text-gray-700">{{ selected?.companyName || '-' }}</span>
        </el-form-item>
        <el-form-item label="退回原因" required>
          <el-input v-model="returnReason" type="textarea" :rows="4" placeholder="请说明需要企业补充材料或修正信息，企业将收到通知后可重新提交" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="returnDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="returning" :disabled="!returnReason.trim()" @click="handleReturn">确认退回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getFirmWorkbench, submitFirmResult, updateChecklistItem, returnToEnterprise, generateDdReport } from '@/api/application'
import { uploadFile } from '@/api/upload'
import { EMPLOYEE_COUNT_OPTIONS, ANNUAL_REVENUE_OPTIONS, MANAGEMENT_CERT_OPTIONS, PRODUCT_CERT_OPTIONS, buyVolumeLabel, supExportLabel, currencyAmountLabel } from '@/constants'
import { Upload } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getFileUrl, getFileName } from '@/utils/file'
import { formatDateTime as formatTime } from '@/utils/datetime'
import { escapeHtml } from '@/utils/escape'

const FIRM_STATUS_MAP = {
  assigned: { label: '已分配', type: 'primary' },
  in_progress: { label: '核查中', type: '' },
  submitted: { label: '待管理员确认', type: 'info' },
  returned: { label: '已退回', type: 'danger' },
  completed: { label: '已完成', type: 'success' },
}

const ROLE_LABEL_MAP = { buyer: '采购商', supplier: '供应商', 'buyer,supplier': '采购商+供应商' }

function annualRevenueDisplay(item) {
  return currencyAmountLabel(item?.annualRevenueAmount, item?.annualRevenueCurrency)
    || ANNUAL_REVENUE_OPTIONS.find(o => o.value === item?.annualRevenue)?.label
    || item?.annualRevenue
    || '-'
}

function purchaseVolumeDisplay(item) {
  return currencyAmountLabel(item?.buyVolumeAmount, item?.buyVolumeCurrency)
    || buyVolumeLabel(item?.buyVolume)
}

const loading = ref(false)
const list = ref([])
const selected = ref(null)
const checklist = ref([])
const progress = ref('')
const reportUrl = ref('')
const reportName = ref('')
const submitting = ref(false)
// 退回补充弹窗状态
const returnDialogVisible = ref(false)
const returnReason = ref('')
const returning = ref(false)
const generating = ref(false)

const canSubmit = computed(() => {
  const allChecked = checklist.value.length > 0 && checklist.value.every(c => c.checked)
  return progress.value.trim() && reportUrl.value && allChecked
})
const isOverdue = computed(() => {
  const deadline = selected.value?.ddFirmDeadline
  if (!deadline) return false
  // 按本地日期字符串比较，避免 new Date(iso) 按 UTC 解析导致跨时区差一天
  const now = new Date()
  const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
  return deadline < today
})

const hasAttachments = computed(() =>
  selected.value?.businessLicenseUrl || selected.value?.financialReportUrl ||
  selected.value?.shareholderInfo || selected.value?.legalRecordUrl ||
  selected.value?.attachments?.length
)

/** 法律文书附件（已拆为独立字段，后端返回现签 URL） */
const legalFile = computed(() => {
  const url = selected.value?.legalRecordUrl
  return url ? { url, name: getFileName(url) } : null
})

/** 是否采购商 */
const isBuyer = computed(() => {
  const role = selected.value?.applicantRole
  return role && (role.includes('buyer') || role === 'buyer')
})
/** 是否供应商 */
const isSupplier = computed(() => {
  const role = selected.value?.applicantRole
  return role && (role.includes('supplier') || role === 'supplier')
})


async function fetchWorkbench() {
  loading.value = true
  try {
    const { data } = await getFirmWorkbench()
    list.value = data || []
    if (list.value.length) selectCompany(list.value[0])
  } catch { list.value = [] } finally { loading.value = false }
}

function selectCompany(item) {
  selected.value = item
  progress.value = item.ddFirmProgress || ''
  reportUrl.value = item.ddReportUrl || ''
  reportName.value = item.ddReportName || ''
  // 从后端返回的 checklist 初始化（已持久化），null → false
  if (item.checklist && item.checklist.length) {
    checklist.value = item.checklist.map(c => ({ ...c, checked: c.checked || false, remark: c.remark || null }))
  } else {
    checklist.value = DEFAULT_CHECKLIST.map(c => ({ ...c, checked: false, remark: null }))
  }
}

const DEFAULT_CHECKLIST = [
  { itemCode: 'biz_info', itemName: '工商注册信息已核实', groupName: '公司基本情况' },
  { itemCode: 'legal_person', itemName: '法人代表背景无异常', groupName: '公司基本情况' },
  { itemCode: 'legal_record', itemName: '诉讼仲裁记录已核查', groupName: '公司基本情况' },
  { itemCode: 'financial', itemName: '审计报告/财务报表已审核', groupName: '财务资料' },
]

/** 码值 → 中文标签 */
function optionLabel(options, val) {
  if (!val) return '-'
  return options.find(o => o.value === val)?.label || val
}
/** 码值 → 中文标签（多选，逗号分隔） */
function optionLabels(options, val) {
  if (!val) return '-'
  return String(val).split(',').map(v => optionLabel(options, v)).join('、')
}

/** 将清单按分组整理（分组名来自后端 dd_checklist.group_name，对齐诺臣8大类） */
const checklistGroups = computed(() => {
  const map = {}
  for (const item of checklist.value) {
    const g = item.groupName || '其他核查'
    if (!map[g]) map[g] = []
    map[g].push(item)
  }
  return Object.entries(map).map(([name, items]) => ({ name, items }))
})

/** 保存核查清单（批量保存所有项） */
const savingChecklist = ref(false)

async function saveChecklist() {
  savingChecklist.value = true
  try {
    const tasks = checklist.value
      .filter(item => item.id != null)
      .map(item =>
      updateChecklistItem(item.id, { checked: item.checked, remark: item.remark || '' })
    )
    await Promise.all(tasks)
    ElMessage.success('核查清单已保存')
  } catch {
    // 拦截器已处理
  } finally {
    savingChecklist.value = false
  }
}

/** 是否不可编辑（已提交/已完成/已退回 后禁用核查操作） */
const isDisabled = computed(() => {
  const s = selected.value?.ddFirmStatus
  return s === 'submitted' || s === 'completed' || s === 'returned'
})

async function handleUpload(options) {
  try {
    const { data } = await uploadFile(options.file, 'dd')
    // 只存 oss_key：签名 URL 会过期，存库必然变死链；后端返回 VO 时按 key 现签
    reportUrl.value = composeFileValue(data.key, data.originalName)
    // 记录真实文件名，用于上传区下方展示（优先接口返回，兜底取本地文件名）
    reportName.value = data.originalName || options.file.name
    ElMessage.success('上传成功')
  } catch { /* 拦截器已处理 */ }
}

/** 提交尽调结果（先弹窗确认） */
async function handleSubmit() {
  if (!progress.value.trim()) { ElMessage.warning('请填写尽调工作备注'); return }
  if (!reportUrl.value) { ElMessage.warning('请上传尽调报告'); return }
  // 弹窗确认：防止误操作
  try {
    await ElMessageBox.confirm(
      `<div style="white-space:nowrap;margin-bottom:4px">确认提交 <span class="highlight-name">${escapeHtml(selected.value?.companyName || '')}</span> 的尽调结果？</div><div>提交后将移交管理员审核。</div>`,
      '确认提交',
      { confirmButtonText: '确认提交', cancelButtonText: '取消', type: 'warning', dangerouslyUseHTMLString: true, customClass: 'confirmDdMsgBox' }
    )
  } catch { return } // 用户取消
  submitting.value = true
  try {
    await submitFirmResult(selected.value.applicationId, { progress: progress.value, resultUrl: reportUrl.value })
    ElMessage.success('尽调结果已提交，等待管理员确认')
    selected.value = null
    await fetchWorkbench()
  } catch { /* 拦截器已处理 */ } finally { submitting.value = false }
}

/** 下载尽调报告模板 PDF（生成并打开新窗口） */
async function handleDownloadTemplate() {
  generating.value = true
  try {
    const { data } = await generateDdReport(selected.value.applicationId)
    ElMessage.success('报告模板已生成')
    window.open(data.url, '_blank')
  } catch { /* 拦截器已处理 */ } finally { generating.value = false }
}

/** 退回尽调给企业补充材料（事务提交后自动发邮件通知企业用户） */
async function handleReturn() {
  if (!returnReason.value.trim()) { ElMessage.warning('请填写退回原因'); return }
  returning.value = true
  try {
    // 调后端接口：校验归属+状态 → assigned→returned → 注册 afterCommit 发邮件
    await returnToEnterprise(selected.value.applicationId, returnReason.value.trim())
    ElMessage.success('已退回企业补充材料')
    // 重置状态：关闭弹窗、清空原因、清除选中、刷新列表
    returnDialogVisible.value = false
    returnReason.value = ''
    selected.value = null
    await fetchWorkbench()
  } catch { /* 拦截器已处理 */ } finally { returning.value = false }
}

onMounted(() => fetchWorkbench())
</script>

<style>
/* 退回弹窗自适应宽度 */
.return-dialog { width: auto !important; max-width: 520px; }
</style>

<style scoped>
/* 退回弹窗表单：星号紧跟label、label不换行、减小label与content间距 */
.return-form :deep(.el-form-item__label) { white-space: nowrap; }
.return-form :deep(.el-form-item.is-required .el-form-item__label::before) { margin-right: 2px; }
/* 附件链接长文件名自动换行 */
:deep(.el-link) {
  word-break: break-all;
}

.firm-right {
  flex: 1; overflow-y: auto; padding-right: 4px;
  font-size: 16px;
}
.firm-right :deep(.el-descriptions__label),
.firm-right :deep(.el-descriptions__content) {
  font-size: 16px !important;
  padding: 12px 16px !important;
}
.firm-right :deep(.el-descriptions__title) {
  font-size: 16px !important;
  margin-bottom: 10px !important;
}
.firm-right :deep(.el-descriptions) {
  margin-bottom: 20px !important;
}

.firm-layout { display: flex; gap: 20px; height: calc(100vh - 230px); }
.firm-left { width: 220px; flex-shrink: 0; border-right: 1px solid #e5e7eb; overflow-y: auto; padding-right: 12px; }
.firm-left-title { font-size: 13px; color: #9ca3af; margin-bottom: 8px; padding-left: 8px; }
.firm-company-item { padding: 10px 12px; border-radius: 8px; cursor: pointer; margin-bottom: 4px; display: flex; align-items: center; justify-content: space-between; transition: all .2s; }
.firm-company-item:hover { background: #f3f4f6; }
.firm-company-item.active { background: #eff6ff; border: 1px solid #93c5fd; }
.company-name { font-size: 14px; font-weight: 500; color: #1f2937; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 130px; }
.section-title { font-size: 16px; font-weight: 600; color: #374151; margin: 24px 0 12px; padding-bottom: 8px; border-bottom: 2px solid #e5e7eb; }
.checklist { background: #fafbfc; border: 1px solid #e5e7eb; border-radius: 10px; padding: 20px; font-size: 16px; }
.checklist-group-title { font-size: 14px; font-weight: 600; color: #6b7280; padding: 8px 0 4px; margin-top: 6px; }
.checklist-group-title:first-child { margin-top: 0; }
.checklist-item { padding: 10px 0; }
.checklist-item + .checklist-item { border-top: 1px solid #f3f4f6; }
.checklist-remark { margin-top: 10px; margin-left: 30px; width: 360px; }
.checklist-save-row { display: flex; justify-content: flex-end; align-items: center; margin-top: 16px; padding-top: 12px; border-top: 1px solid #e5e7eb; }
.checklist-add-remark { margin-left: 30px; font-size: 14px; color: #9ca3af; }
.submit-area { background: #fafbfc; border: 1px solid #e5e7eb; border-radius: 10px; padding: 20px; font-size: 16px; }
.submit-row { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; margin-top: 15px; justify-content: space-between; }
.submit-actions { margin-left: auto; display: flex; gap: 8px; }
.uploaded-file-name { display: flex; align-items: center; gap: 6px; font-size: 14px; margin-top: 15px !important; }
.report-info-row { display: flex; align-items: center; gap: 4px; font-size: 14px; }
.report-info-row .report-info-label { color: #6b7280; }
.report-info-row .report-info-label::after { content: '：'; }
.upload-check { width: 18px; height: 18px; flex-shrink: 0; }
</style>

<!-- 覆盖 Element Plus 描述列表样式（非 scoped，用 firm-right 限定范围） -->
<style>
.firm-right .el-descriptions__label {
  width: 120px !important;
  text-align: center !important;
  vertical-align: middle !important;
  white-space: nowrap !important;
}
</style>
