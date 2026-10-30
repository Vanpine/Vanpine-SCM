<template>
  <div>
    <!-- 搜索 -->
    <el-card class="search-card">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="公司名称">
          <el-input v-model="searchForm.companyName" placeholder="企业全称" clearable style="width:220px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="申请身份">
          <el-select v-model="searchForm.applicantRole" placeholder="全部" clearable style="width:140px" @change="handleSearch">
            <el-option label="采购商" value="buyer" />
            <el-option label="供应商" value="supplier" />
            <el-option label="采购商+供应商" value="buyer,supplier" />
          </el-select>
        </el-form-item>
        <el-form-item label="套餐类型">
          <el-select v-model="searchForm.packageType" placeholder="全部" clearable style="width:130px" @change="handleSearch">
            <el-option label="基础版" value="basic" />
            <el-option label="标准版" value="standard" />
            <el-option label="高级版" value="premium" />
          </el-select>
        </el-form-item>
        <el-form-item label="支付状态">
          <el-select v-model="searchForm.paymentStatus" placeholder="全部" clearable style="width:120px" @change="handleSearch">
            <el-option label="未支付" value="unpaid" />
            <el-option label="已支付" value="paid" />
          </el-select>
        </el-form-item>
        <el-form-item label="事务所状态">
          <el-select v-model="searchForm.ddFirmStatus" placeholder="全部" clearable style="width:120px" @change="handleSearch">
            <el-option label="未分配" value="unassigned" />
            <el-option label="已分配" value="assigned" />
            <el-option label="核查中" value="in_progress" />
            <el-option label="待确认" value="submitted" />
            <el-option label="已退回" value="returned" />
            <el-option label="已完成" value="completed" />
          </el-select>
        </el-form-item>
        <el-form-item label="报告状态">
          <el-select v-model="searchForm.ddReportStatus" placeholder="全部" clearable style="width:120px" @change="handleSearch">
            <el-option label="待上传" value="pending" />
            <el-option label="待审核" value="submitted" />
            <el-option label="已完成" value="completed" />
          </el-select>
        </el-form-item>
      </el-form>
      <div style="text-align:right;margin:12px 0 8px">
        <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card v-loading="loading" class="content-card">
      <div class="table-toolbar">
        <span class="text-base font-semibold text-gray-700">尽调管理</span>
      </div>

      <el-table :data="tableData" stripe border style="width: 100%">
        <el-table-column type="index" :index="(idx) => total - (pageNum - 1) * pageSize - idx" label="ID" width="55" align="center" />
        <el-table-column prop="companyName" label="企业名称" min-width="220" show-overflow-tooltip />
        <el-table-column label="申请身份" width="130" align="center">
          <template #default="{ row }">
            {{ row.applicantRoleLabel || ROLE_LABEL_MAP[row.applicantRole] || row.applicantRole || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="套餐" width="80" align="center">
          <template #default="{ row }">
            {{ PACKAGE_TYPE_MAP[row.packageType]?.label || row.packageType || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="支付状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.paymentStatus === 'paid' ? 'success' : 'primary'">
              {{ row.paymentStatus === 'paid' ? '已支付' : '未支付' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="contactName" label="联系人" min-width="120" />
        <el-table-column prop="contactEmail" label="联系邮箱" min-width="220" show-overflow-tooltip />
        <el-table-column label="事务所" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.ddFirmStatus === 'assigned'" size="small" type="warning">已分配</el-tag>
            <el-tag v-else-if="row.ddFirmStatus === 'in_progress'" size="small">核查中</el-tag>
            <el-tag v-else-if="row.ddFirmStatus === 'submitted'" size="small" type="primary">待确认</el-tag>
            <el-tag v-else-if="row.ddFirmStatus === 'returned'" size="small" type="danger">已退回</el-tag>
            <el-tag v-else-if="row.ddFirmStatus === 'completed'" size="small" type="success">已完成</el-tag>
            <span v-else class="text-gray-400 text-sm">未分配</span>
          </template>
        </el-table-column>
        <el-table-column label="报告状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="REPORT_STATUS_MAP[row.ddReportStatus]?.type || 'primary'">
              {{ REPORT_STATUS_MAP[row.ddReportStatus]?.label || '待上传' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" min-width="160">
          <template #default="{ row }">
            {{ formatTime(row.ddSubmittedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="110" fixed="right" align="center" class-name="ops-col">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleViewDetail(row)">查看资料</el-button>
            <!-- 按钮少：≤2 平铺 -->
            <template v-if="!hasExtraActions(row)">
              <el-button v-if="row.ddReportUrl" type="success" link @click="handleViewReport(row)" style="margin-left:4px">尽调报告</el-button>
            </template>
            <!-- 按钮多：≥3 收进⋮ -->
            <el-dropdown v-else trigger="click" @command="(cmd) => handleMoreCommand(cmd, row)">
              <el-icon :size="16" style="cursor:pointer;color:#409eff;margin-left:4px;margin-top:5px;display:inline-block;transform:rotate(90deg)"><MoreFilled /></el-icon>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="row.ddReportUrl" command="viewReport">尽调报告</el-dropdown-item>
                  <el-dropdown-item v-if="!row.ddFirmId" command="assignFirm" :icon="Plus">分配事务所</el-dropdown-item>
                  <el-dropdown-item v-else-if="row.ddFirmStatus !== 'completed' && row.ddFirmStatus !== 'submitted'"
                    command="reassignFirm" :icon="Plus">重新分配</el-dropdown-item>
                  <el-dropdown-item v-if="row.ddFirmStatus === 'submitted'"
                    command="confirmPass">确认通过</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
              <template #empty>
          <el-empty description="暂无数据" />
        </template>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize" :total="total"
          :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper" @size-change="handleSearch"
          @current-change="handleSearch" />
      </div>
    </el-card>

    <!-- 企业资料弹窗 -->
    <el-dialog v-model="detailVisible" title="企业尽调资料" width="800px" top="5vh">
      <template v-if="detailData">
        <el-descriptions title="企业信息" :column="2" border class="detail-descriptions">
          <el-descriptions-item label="企业名称">{{ detailData.companyName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="注册号">{{ detailData.regNumber || '-' }}</el-descriptions-item>
          <el-descriptions-item label="所在国家">{{ detailData.country || '-' }}</el-descriptions-item>
          <el-descriptions-item label="省份 / 城市">{{ detailData.province || '-' }} / {{ detailData.city || '-'
            }}</el-descriptions-item>
          <el-descriptions-item label="成立年份">{{ detailData.establishedYear || '-' }}</el-descriptions-item>
          <el-descriptions-item label="企业官网">{{ detailData.website || '-' }}</el-descriptions-item>
          <el-descriptions-item label="公司简介" :span="2">{{ detailData.description || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-descriptions title="联系信息" :column="2" border class="detail-descriptions">
          <el-descriptions-item label="联系人">{{ detailData.contactName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="职位">{{ detailData.contactTitle || '-' }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ detailData.contactEmail || '-' }}</el-descriptions-item>
          <el-descriptions-item label="电话">{{ detailData.contactPhone || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-descriptions title="尽调核心材料" :column="2" border class="detail-descriptions">
          <el-descriptions-item label="法人代表">{{ detailData.legalPersonName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="法人身份证号">{{ maskIdCard(detailData.legalPersonId) }}</el-descriptions-item>
          <el-descriptions-item label="员工人数">
            {{EMPLOYEE_COUNT_OPTIONS.find(o => o.value === detailData.employeeCount)?.label || detailData.employeeCount
              || '-'}}
          </el-descriptions-item>
          <el-descriptions-item label="年营业额">
            {{ annualRevenueDisplay(detailData) }}
          </el-descriptions-item>
          <el-descriptions-item label="诉讼记录" :span="2">
            <template v-if="!detailData.legalRecordText && !detailData.legalRecordUrl">无</template>
            <template v-else>
              <div v-if="detailData.legalRecordText">{{ detailData.legalRecordText }}</div>
              <el-link v-if="detailData.legalRecordUrl" type="primary"
                :href="detailData.legalRecordUrl" target="_blank" class="mt-1">
                {{ getFileName(detailData.legalRecordUrl) }}
              </el-link>
            </template>
          </el-descriptions-item>
        </el-descriptions>

        <el-descriptions title="申请身份" :column="2" border class="detail-descriptions">
          <el-descriptions-item label="身份" :span="2">{{ detailData.applicantRoleLabel ||
            ROLE_LABEL_MAP[detailData.applicantRole]
            ||
            detailData.applicantRole || '-' }}</el-descriptions-item>
        </el-descriptions>

        <!-- 采购方信息（申请身份含 buyer 时展示） -->
        <el-descriptions v-if="detailData.applicantRole?.includes('buyer')" title="采购方信息" :column="2" border
          class="detail-descriptions">
          <el-descriptions-item label="采购品类">{{ detailData.buyCategory || '-' }}</el-descriptions-item>
          <el-descriptions-item label="年采购额">{{ purchaseVolumeDisplay(detailData) }}</el-descriptions-item>
          <el-descriptions-item label="所属行业">{{ detailData.industry || '-' }}</el-descriptions-item>
          <el-descriptions-item label="目标市场">{{ detailData.buyMarket || '-' }}</el-descriptions-item>
          <el-descriptions-item label="目标供应地区">{{ detailData.targetRegion || '-' }}</el-descriptions-item>
          <el-descriptions-item label="付款方式偏好">{{ optionLabels(PAYMENT_TERMS_OPTIONS, detailData.paymentTerms)
          }}</el-descriptions-item>
          <el-descriptions-item label="采购产品明细" :span="2">{{ detailData.procurementDetail || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="供应商资质要求" :span="2">{{ detailData.supplierRequirement || '-'
          }}</el-descriptions-item>
        </el-descriptions>

        <!-- 供应方信息（申请身份含 supplier 时展示） -->
        <el-descriptions v-if="detailData.applicantRole?.includes('supplier')" title="供应方信息" :column="2" border
          class="detail-descriptions">
          <el-descriptions-item label="生产品类">{{ detailData.supCategory || '-' }}</el-descriptions-item>
          <el-descriptions-item label="年产能">{{ detailData.supCapacity || '-' }}</el-descriptions-item>
          <el-descriptions-item label="出口经验">{{ supExportLabel(detailData.supExport) }}</el-descriptions-item>
          <el-descriptions-item label="交货周期">{{ optionLabel(LEAD_TIME_OPTIONS, detailData.leadTime)
          }}</el-descriptions-item>
          <el-descriptions-item label="主要客户/行业">{{ detailData.keyClients || '-' }}</el-descriptions-item>
          <el-descriptions-item label="主要出口市场">{{ detailData.exportMarkets || '-' }}</el-descriptions-item>
          <el-descriptions-item label="资质认证" :span="2">{{ detailData.supCerts || '-' }}</el-descriptions-item>
          <el-descriptions-item label="管理体系认证" :span="2">{{ optionLabels(MANAGEMENT_CERT_OPTIONS,
            detailData.managementCert) }}</el-descriptions-item>
          <el-descriptions-item label="产品认证" :span="2">{{ optionLabels(PRODUCT_CERT_OPTIONS, detailData.productCert)
          }}</el-descriptions-item>
          <el-descriptions-item label="交货方式" :span="2">{{ optionLabels(DELIVERY_TERMS_OPTIONS, detailData.deliveryTerms)
          }}</el-descriptions-item>
          <el-descriptions-item label="主要产品/服务" :span="2">{{ detailData.mainProducts || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-descriptions title="套餐与支付" :column="2" border class="detail-descriptions">
          <el-descriptions-item label="套餐类型">
            {{ PACKAGE_TYPE_MAP[detailData.packageType]?.label || detailData.packageType || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="支付金额">{{ minorAmountLabel(detailData.paymentAmount, detailData.paymentCurrency) || '-' }}</el-descriptions-item>
          <el-descriptions-item label="支付状态">
            <el-tag size="small" :type="detailData.paymentStatus === 'paid' ? 'success' : 'warning'">
              {{ detailData.paymentStatus === 'paid' ? '已支付' : '未支付' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item v-if="detailData.ddSubmittedAt" label="资料提交时间">{{
            formatTime(detailData.ddSubmittedAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="detailData.ddResubmittedAt" label="资料重新提交时间">{{
            formatTime(detailData.ddResubmittedAt) }}</el-descriptions-item>
          <el-descriptions-item label="事务所状态">
            <template v-if="detailData.ddFirmStatus === 'assigned'">
              <el-tag size="small" type="warning">已分配</el-tag>
            </template>
            <template v-else-if="detailData.ddFirmStatus === 'in_progress'">
              <el-tag size="small">核查中</el-tag>
            </template>
            <template v-else-if="detailData.ddFirmStatus === 'submitted'">
              <el-tag size="small" type="primary">待管理员确认</el-tag>
            </template>
            <template v-else-if="detailData.ddFirmStatus === 'returned'">
              <el-tag size="small" type="danger">已退回</el-tag>
            </template>
            <template v-else-if="detailData.ddFirmStatus === 'completed'">
              <el-tag size="small" type="success">已完成</el-tag>
            </template>
            <span v-else class="text-gray-400">未分配</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="detailData.ddFirmName" label="事务所">{{ detailData.ddFirmName
          }}</el-descriptions-item>
          <el-descriptions-item v-if="detailData.ddFirmAssignedAt" label="分配时间">{{
            formatTime(detailData.ddFirmAssignedAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="detailData.ddFirmDeadline" label="截止日期">{{ detailData.ddFirmDeadline
            }}</el-descriptions-item>
          <el-descriptions-item v-if="detailData.ddFirmInstruction" label="核查指令" :span="2">{{
            detailData.ddFirmInstruction }}</el-descriptions-item>
          <el-descriptions-item v-if="detailData.ddFirmStatus === 'returned' && detailData.ddFirmReturnReason"
            label="退回原因" :span="2">{{ detailData.ddFirmReturnReason }}</el-descriptions-item>
          <el-descriptions-item label="报告状态">
            <el-tag size="small" :type="REPORT_STATUS_MAP[detailData.ddReportStatus]?.type || 'primary'">
              {{ REPORT_STATUS_MAP[detailData.ddReportStatus]?.label || '待上传' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="报告上传时间">{{ detailData.ddReportTime ? formatTime(detailData.ddReportTime) : '-'
            }}</el-descriptions-item>
        </el-descriptions>

        <!-- 事务所详情（已分配时展示，复用 searchFirms 按 ddFirmId 精确匹配） -->
        <div v-if="detailData.ddFirmId" class="firm-info-wrap" v-loading="firmInfoLoading">
          <template v-if="firmInfo">
            <el-descriptions title="事务所详情" :column="2" border class="detail-descriptions firm-info-desc">
              <el-descriptions-item label="事务所名称">{{ firmInfo.firmName || detailData.ddFirmName || '-'
              }}</el-descriptions-item>
              <el-descriptions-item label="联系人">{{ firmInfo.contactName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="联系电话">{{ firmInfo.contactPhone || '-' }}</el-descriptions-item>
              <el-descriptions-item label="联系邮箱">{{ firmInfo.contactEmail || '-' }}</el-descriptions-item>
              <el-descriptions-item label="地址" :span="2">{{ firmAddress || '-' }}</el-descriptions-item>
              <el-descriptions-item label="服务范围" :span="2">{{ firmInfo.serviceScope || '-' }}</el-descriptions-item>
              <el-descriptions-item label="服务领域" :span="2">{{ firmInfo.serviceSector || '-' }}</el-descriptions-item>
            </el-descriptions>
          </template>
          <!-- 外部 API 不可用时降级：只显示事务所名称 -->
          <el-descriptions v-else title="事务所详情" :column="1" border class="detail-descriptions">
            <el-descriptions-item label="事务所名称">{{ detailData.ddFirmName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="提示">事务所详细信息暂不可获取</el-descriptions-item>
          </el-descriptions>
        </div>

        <el-descriptions
          v-if="detailData.shareholderInfo || detailData.financialReportUrl || detailData.businessLicenseUrl || detailData.attachments?.length"
          title="附件材料" :column="1" border class="detail-descriptions">
          <el-descriptions-item v-if="detailData.businessLicenseUrl" label="营业执照">
            <el-link type="primary" :href="getFileUrl(detailData.businessLicenseUrl)" target="_blank">
              {{ getFileName(detailData.businessLicenseUrl) }}
            </el-link>
          </el-descriptions-item>
          <el-descriptions-item v-if="detailData.financialReportUrl" label="审计报告">
            <el-link type="primary" :href="getFileUrl(detailData.financialReportUrl)" target="_blank">
              {{ getFileName(detailData.financialReportUrl) }}
            </el-link>
          </el-descriptions-item>
          <el-descriptions-item v-if="detailData.shareholderInfo" label="股权结构">
            <el-link type="primary" :href="getFileUrl(detailData.shareholderInfo)" target="_blank">
              {{ getFileName(detailData.shareholderInfo) }}
            </el-link>
          </el-descriptions-item>
          <el-descriptions-item v-if="detailData.attachments?.length" label="附件材料">
            <template v-for="(att, idx) in detailData.attachments" :key="idx">
              <el-link :href="att.url" target="_blank" type="primary" class="mr-4">
                {{ att.fileName || '附件' }}
              </el-link>
            </template>
          </el-descriptions-item>
          <el-descriptions-item v-if="detailData.ddReportUrl" label="尽调报告">
            <el-link type="primary" :href="getFileUrl(detailData.ddReportUrl)" target="_blank">
              {{ detailData.ddReportName || getFileName(detailData.ddReportUrl) }}
            </el-link>
          </el-descriptions-item>
        </el-descriptions>
      </template>
    </el-dialog>

    <!-- 分配事务所弹窗 -->
    <AssignFirmDialog v-if="assignVisible" v-model:visible="assignVisible" :application-id="assignAppId" :company-name="assignCompanyName"
      @success="handleAssignSuccess" />

    <!-- 尽调报告预览弹窗：iframe 内联预览 PDF，浏览器原生渲染不触发下载 -->
    <el-dialog v-model="reportPreviewVisible" title="尽调报告预览" fullscreen :close-on-click-modal="false"
      class="report-preview-dialog">
      <iframe v-if="reportPreviewUrl" :src="reportPreviewUrl" class="report-preview-iframe"></iframe>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { Search, Refresh, MoreFilled, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getApplicationList, getDdDetail, confirmDdComplete, searchFirms } from '@/api/application'
import AssignFirmDialog from './AssignFirmDialog.vue'
import { EMPLOYEE_COUNT_OPTIONS, ANNUAL_REVENUE_OPTIONS, MANAGEMENT_CERT_OPTIONS, PRODUCT_CERT_OPTIONS, DELIVERY_TERMS_OPTIONS, LEAD_TIME_OPTIONS, PAYMENT_TERMS_OPTIONS, buyVolumeLabel, supExportLabel, currencyAmountLabel, minorAmountLabel } from '@/constants'
import { getFileUrl, getFileName } from '@/utils/file'
import { formatDateTime as formatTime } from '@/utils/datetime'
import { escapeHtml } from '@/utils/escape'

/** 套餐标签映射 */
const PACKAGE_TYPE_MAP = {
  basic: { label: '基础版' },
  standard: { label: '标准版' },
  premium: { label: '高级版' },
}

/** 报告状态中文映射（pending/null 兜底"待上传"） */
const REPORT_STATUS_MAP = {
  submitted: { label: '待审核', type: 'warning' },
  completed: { label: '已完成', type: 'success' },
}

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

/** 申请身份中文映射（前端 fallback） */
const ROLE_LABEL_MAP = {
  buyer: '采购商',
  supplier: '供应商',
  'buyer,supplier': '采购商+供应商',
}

/** 码值 → 中文标签（单选） */
function optionLabel(options, val) {
  if (!val) return '-'
  return options.find(o => o.value === val)?.label || val
}

/** 码值 → 中文标签（多选，逗号分隔） */
function optionLabels(options, val) {
  if (!val) return '-'
  return String(val).split(',').map(v => optionLabel(options, v)).join('、')
}


/** 法人身份证号脱敏（保留前6位+后4位，中间用 * 替换） */
function maskIdCard(val) {
  if (!val) return '-'
  const s = String(val).trim()
  return s.length < 10 ? s : s.substring(0, 6) + '********' + s.substring(s.length - 4)
}

// ---- 搜索 ----
const searchForm = reactive({
  companyName: '',
  applicantRole: '',
  packageType: '',
  paymentStatus: '',
  ddReportStatus: '',
  ddFirmStatus: '',
})

// ---- 列表 ----
const loading = ref(true)
const tableData = ref([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

async function fetchList() {
  loading.value = true
  try {
    const params = {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      status: 'approved',
      companyName: searchForm.companyName || undefined,
      applicantRole: searchForm.applicantRole || undefined,
      packageType: searchForm.packageType || undefined,
      paymentStatus: searchForm.paymentStatus || undefined,
      ddReportStatus: searchForm.ddReportStatus || undefined,
      ddFirmStatus: searchForm.ddFirmStatus || undefined,
    }
    const { data } = await getApplicationList(params)
    const rows = (data.records || [])
    // 合并尽调字段到顶层（向后兼容拆分后的表结构）
    rows.forEach(row => { if (row.dueDiligence) { const { id, applicationId, ...dd } = row.dueDiligence; Object.assign(row, dd) } })
    tableData.value = rows  // 服务端已分页排序，不做客户端重排
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pageNum.value = 1
  fetchList()
}

function handleReset() {
  searchForm.companyName = ''
  searchForm.applicantRole = ''
  searchForm.packageType = ''
  searchForm.paymentStatus = ''
  searchForm.ddReportStatus = ''
  searchForm.ddFirmStatus = ''
  handleSearch()
}

// ---- 详情弹窗 ----
const detailVisible = ref(false)
const detailData = ref(null)
/** 已分配事务所的详细信息（searchFirms 按 ddFirmName 查询后用 ddFirmId 精确匹配） */
const firmInfo = ref(null)
const firmInfoLoading = ref(false)

/** 事务所地址：省/市/区 + 详细地址拼接，空值跳过 */
const firmAddress = computed(() => {
  const f = firmInfo.value
  if (!f) return ''
  return [f.province, f.city, f.district, f.address].filter(Boolean).join(' ')
})

/**
 * 加载已分配事务所的详细信息
 * 复用 searchFirms 列表接口：用事务所名称当 keyword 查询，再用 ddFirmId 精确匹配到那一条
 * 外部 API 不可用时降级为 null，详情弹窗只显示 ddFirmName，不阻塞查看
 * @param {number} firmId 事务所 ID（detailData.ddFirmId）
 * @param {string} firmName 事务所名称（detailData.ddFirmName，用作搜索关键字）
 */
async function loadFirmInfo(firmId, firmName) {
  firmInfoLoading.value = true
  try {
    const { data } = await searchFirms(firmName)
    const list = Array.isArray(data) ? data : []
    firmInfo.value = list.find(f => String(f.firmId) === String(firmId)) || null
  } catch {
    // 拦截器已弹错，这里只做状态恢复
    firmInfo.value = null
  } finally {
    firmInfoLoading.value = false
  }
}

async function handleViewDetail(row) {
  try {
    const { data } = await getDdDetail(row.id)
    detailData.value = data
    // 重置上一次的事务所详情，已分配则加载本次的
    firmInfo.value = null
    if (data?.ddFirmId) loadFirmInfo(data.ddFirmId, data.ddFirmName)
    detailVisible.value = true
  } catch {
    // 错误由拦截器统一处理
  }
}

// ---- 分配事务所弹窗 ----
const assignVisible = ref(false)
const assignAppId = ref(null)
const assignCompanyName = ref('')

function handleAssignFirm(row) {
  assignAppId.value = row.id
  assignCompanyName.value = row.companyName || ''
  assignVisible.value = true
}

function handleAssignSuccess() {
  assignVisible.value = false
  fetchList()
}

/** 查看事务所上传的尽调报告（iframe 内联预览，避免触发下载） */
const reportPreviewVisible = ref(false)
const reportPreviewUrl = ref('')
function handleViewReport(row) {
  const url = getFileUrl(row.ddReportUrl)
  if (!url) {
    ElMessage.warning('报告地址不存在')
    return
  }
  reportPreviewUrl.value = url
  reportPreviewVisible.value = true
}

/** 该行是否有超出 2 个按钮的额外操作 */
function hasExtraActions(row) {
  let count = 1 // 企业资料始终有
  if (row.ddReportUrl) count++
  if (!row.ddFirmId) count++
  else if (row.ddFirmStatus !== 'completed' && row.ddFirmStatus !== 'submitted') count++
  if (row.ddFirmStatus === 'submitted') count++
  return count > 1
}

/** 更多操作下拉分发 */
function handleMoreCommand(cmd, row) {
  switch (cmd) {
    case 'viewReport': handleViewReport(row); break
    case 'assignFirm': case 'reassignFirm': handleAssignFirm(row); break
    case 'confirmPass': handleConfirmPass(row); break
  }
}

/** 管理员确认尽调通过（submitted → completed，升级角色+发邮件） */
const confirming = ref(false)
async function handleConfirmPass(row) {
  try {
    await ElMessageBox.confirm(
      `<div style="white-space:nowrap;margin-bottom:4px">确认通过 <span class="highlight-name">${escapeHtml(row.companyName || '')}</span> 的尽调结果？</div><div>确认后企业用户将获得企业管理员权限。</div>`,
      '确认尽调通过',
      { confirmButtonText: '确认通过', cancelButtonText: '取消', type: 'success', dangerouslyUseHTMLString: true, customClass: 'confirmDdMsgBox' }
    )
  } catch { return } // 用户取消
  confirming.value = true
  try {
    await confirmDdComplete(row.id)
    ElMessage.success('尽调已确认通过，企业用户已获得企业管理员权限')
    fetchList()
  } catch { /* 拦截器已处理 */ } finally { confirming.value = false }
}

/** 解析诉讼记录中的纯文本部分（去掉 ||文书:url|原始名） */
onMounted(() => {
  fetchList()
})
</script>

<!-- 报告预览弹窗：iframe 撑满全屏 dialog body -->
<style>
.report-preview-dialog .el-dialog__body {
  padding: 0;
}

.report-preview-iframe {
  width: 100%;
  height: calc(100vh - 110px);
  border: none;
  display: block;
}

/* 事务所详情：label 宽度统一 */
.firm-info-desc .el-descriptions__label {
  width: 110px;
  font-weight: 600;
}

.firm-info-wrap {
  margin-top: 4px;
}
</style>
