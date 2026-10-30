<template>
  <div>
    <!-- 需求信息卡片 -->
    <el-card class="search-card">
      <div style="display:flex;align-items:center;gap:16px">
        <el-icon :size="20" color="#409eff"><Search /></el-icon>
        <span style="font-size:20px;font-weight:600;color:#303133">智能匹配 · {{ requirement?.title || '加载中...' }}</span>
        <el-tag v-if="requirement" type="primary" effect="dark">{{ requirement.category }}</el-tag>
      </div>
      <div v-if="requirement" style="margin-top:12px;margin-left:36px;display:flex;gap:28px;color:#606266;font-size:13px">
        <span>数量：{{ requirement.quantity }} {{ requirement.unit }}</span>
        <span>供应商地域：{{ requirement.region || '不限' }}</span>
        <span>资质要求：{{ requirement.certRequire || '不限' }}</span>
        <span>截止日期：<EmptyText :value="requirement.deadline" /></span>
      </div>
    </el-card>

    <!-- 匹配结果卡片 -->
    <el-card class="content-card">
      <div class="table-toolbar">
        <span class="text-base font-semibold text-gray-700">候选供应商（{{ list.length }} 家）</span>
        <el-tag v-if="reasonGenerating" type="warning" effect="plain" size="small">AI 理由生成中</el-tag>
      </div>

      <el-empty v-if="!loading && list.length === 0" description="暂无匹配的供应商，建议放宽需求条件后重试" />

      <!-- AI 匹配加载中 -->
      <div v-if="loading" style="text-align:center;padding:60px 0">
        <el-icon :size="40" style="animation:rotate 1.5s linear infinite;color:#409eff"><Loading /></el-icon>
        <p style="margin-top:16px;font-size:16px;color:#409eff;font-weight:500">正在匹配供应商，请稍候...</p>
        <p style="margin-top:8px;font-size:13px;color:#909399">先出名单和分数，匹配理由后台生成</p>
      </div>

      <div v-else v-loading="loading">
        <div v-for="(item, idx) in list" :key="item.tenantId"
             style="border:1px solid #e4e7ed;border-radius:8px;padding:20px;margin-bottom:16px;transition:box-shadow .3s"
             class="match-card">
          <!-- 排名 + 企业名 + 分数 -->
          <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:14px">
            <div style="display:flex;align-items:center;gap:12px">
              <span style="display:inline-flex;align-items:center;justify-content:center;width:28px;height:28px;border-radius:50%;font-size:13px;font-weight:700;color:#fff"
                    :style="{ background: idx === 0 ? '#f56c6c' : idx === 1 ? '#e6a23c' : idx === 2 ? '#409eff' : '#909399' }">
                {{ idx + 1 }}
              </span>
              <span style="font-size:18px;font-weight:600;color:#303133">{{ item.companyName || '未知企业' }}</span>
              <el-tag size="small" effect="plain"><EmptyText :value="item.province" />{{ item.city ? ' · ' + item.city : '' }}</el-tag>
            </div>
            <div style="text-align:right">
              <span style="font-size:22px;font-weight:700" :style="{ color: idx === 0 ? '#f56c6c' : '#409eff' }">
                {{ item.matchScore }}
              </span>
              <span style="font-size:12px;color:#909399"> 分</span>
            </div>
          </div>

          <!-- 供应商关键信息 -->
          <el-descriptions :column="3" border class="detail-descriptions" style="margin-bottom:14px">
            <el-descriptions-item label="生产品类"><EmptyText :value="item.supCategories" /></el-descriptions-item>
            <el-descriptions-item label="年产能"><EmptyText :value="item.supCapacity" /></el-descriptions-item>
            <el-descriptions-item label="出口经验">{{ supExportLabel(item.supExport) }}</el-descriptions-item>
            <el-descriptions-item label="资质认证"><EmptyText :value="item.supCerts" /></el-descriptions-item>
            <el-descriptions-item label="所属行业"><EmptyText :value="item.industry" /></el-descriptions-item>
            <el-descriptions-item label="成立年份"><EmptyText :value="item.establishedYear" /></el-descriptions-item>
            <el-descriptions-item label="信用代码"><EmptyText :value="item.regNumber" /></el-descriptions-item>
            <el-descriptions-item label="画像完整度">
              <el-progress :percentage="item.completenessScore || 0" :stroke-width="6" style="width:120px" />
            </el-descriptions-item>
            <el-descriptions-item label="综合履约分">
              <template v-if="item.perfSampleSize">
                <span style="font-weight:700" :style="{ color: perfColor(item.perfScore) }">{{ item.perfScore }}</span>
                <span style="color:#909399;font-size:12px"> / 100</span>
              </template>
              <EmptyText v-else :value="null" />
            </el-descriptions-item>
            <el-descriptions-item label="主要客户" :span="3"><EmptyText :value="item.keyClients" /></el-descriptions-item>
            <el-descriptions-item label="主要产品" :span="3"><EmptyText :value="item.mainProducts" /></el-descriptions-item>
          </el-descriptions>

          <!-- 尽调信息 -->
          <div v-if="item.ddFirmName || item.ddFirmRemark || item.ddReportUrl" style="margin-bottom:14px;padding:12px;background:#f5f7fa;border-radius:6px;font-size:13px;color:#606266;display:flex;align-items:center;gap:24px;flex-wrap:wrap">
            <span v-if="item.ddFirmName">核查事务所：{{ item.ddFirmName }}</span>
            <span v-if="item.ddFirmRemark">核查结论：{{ item.ddFirmRemark }}</span>
            <span v-if="item.ddReportUrl" style="color:#409eff;cursor:pointer" @click="openPreview(item.ddReportUrl)">查看尽调报告 →</span>
          </div>

          <!-- 匹配详情：规则摘要 / AI 解读分开标 -->
          <el-collapse :model-value="['detail']">
            <el-collapse-item name="detail">
              <template #title>
                <div class="match-detail-title">
                  <span>匹配详情</span>
                  <el-tag v-if="item.reasonStatus === 'ready'" size="small" type="success" effect="plain">AI 已生成</el-tag>
                  <el-tag v-else-if="item.reasonStatus === 'generating'" size="small" type="warning" effect="plain">AI 生成中</el-tag>
                  <el-tag v-else-if="item.reasonStatus === 'fallback'" size="small" type="info" effect="plain">AI 未生成</el-tag>
                </div>
              </template>

              <section class="reason-block reason-block--rule">
                <header class="reason-head">
                  <span class="reason-kicker">规则摘要</span>
                  <span class="reason-hint">系统按品类、资质、地域等维度自动打分</span>
                </header>
                <ul v-if="splitReasons(item).ruleReasons.length" class="reason-list">
                  <li v-for="(reason, ri) in splitReasons(item).ruleReasons" :key="'r-' + ri">{{ reason }}</li>
                </ul>
                <p v-else class="reason-empty">暂无规则摘要</p>
              </section>

              <section class="reason-block reason-block--ai">
                <header class="reason-head">
                  <span class="ai-mark">AI</span>
                  <span class="reason-kicker">AI 匹配解读</span>
                  <span class="reason-hint">DeepSeek 根据需求与画像生成，仅供参考</span>
                </header>
                <p v-if="item.reasonStatus === 'generating'" class="reason-pending">
                  正在生成解读，可先看上方规则摘要
                </p>
                <p v-else-if="item.reasonStatus === 'fallback'" class="reason-pending">
                  本次未生成 AI 解读，请以规则摘要为准
                </p>
                <ul v-else-if="splitReasons(item).aiReasons.length" class="reason-list reason-list--ai">
                  <li v-for="(reason, ri) in splitReasons(item).aiReasons" :key="'a-' + ri">{{ reason }}</li>
                </ul>
                <p v-else class="reason-empty">暂无 AI 解读</p>
              </section>
            </el-collapse-item>
          </el-collapse>

          <!-- 发起对接 / 对接状态 -->
          <div style="margin-top:14px;text-align:right">
            <template v-if="declinedSupplierIds.has(item.tenantId)">
              <el-tag type="danger">已拒绝该询盘</el-tag>
            </template>
            <template v-else-if="connectionStatusMap.has(item.tenantId)">
              <el-tag v-if="connectionStatusMap.get(item.tenantId) === 'accepted'" type="success">已接受</el-tag>
              <el-tag v-else-if="connectionStatusMap.get(item.tenantId) === 'rejected'" type="danger">已拒绝</el-tag>
              <el-tag v-else type="warning">已发起对接</el-tag>
            </template>
            <el-button v-else type="primary" @click="openConnectDialog(item)">发起对接</el-button>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 发起对接弹窗 -->
    <el-dialog v-model="connectVisible" title="发起对接意向" :close-on-click-modal="false" class="connect-dialog">
      <template v-if="connectTarget">
        <div style="margin-bottom:12px;color:#606266;font-size:16px;line-height:2;text-align:center">
          向 <span class="highlight-name">{{ connectTarget.companyName }}</span> 发起对接意向
          <br />
          <span style="color:#303133;font-weight:600">{{ requirement?.title }}</span>
        </div>
        <el-input v-model="connectMessage" type="textarea" :rows="3" placeholder="留言（可选），如：贵司产品与我方需求高度匹配，期待进一步沟通" maxlength="500" show-word-limit />
      </template>
      <template #footer>
        <el-button @click="connectVisible = false">取消</el-button>
        <el-button type="primary" :loading="connecting" @click="doConnect">确认发起</el-button>
      </template>
    </el-dialog>

    <!-- 尽调报告预览弹窗 -->
    <el-dialog v-model="previewVisible" title="尽调报告预览" :close-on-click-modal="false" class="preview-dialog" top="5vh">
      <iframe v-if="previewUrl" :src="previewUrl" style="width:100%;height:80vh;border:none" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, Loading } from '@element-plus/icons-vue'
import { getRequirementDetail, matchSuppliers, createConnection, listConnections, getDeclinedSuppliers } from '@/api/match'
import { getFileUrl } from '@/utils/file'
import { supExportLabel } from '@/constants'

const route = useRoute()
const requirementId = route.params.id

const REASON_POLL_MS = 2000
const REASON_POLL_MAX = 15

const loading = ref(true)
const requirement = ref(null)
const list = ref([])
/** 名单已出、AI 理由还在写 */
const reasonGenerating = ref(false)
/** 供应商 tenantId → 对接状态（pending/accepted/rejected） */
const connectionStatusMap = ref(new Map())
/** 拒绝过该需求询盘的供应商 tenantId 集合（标注"已拒绝该询盘"） */
const declinedSupplierIds = ref(new Set())

let reasonTimer = null
let reasonPolls = 0

function anyGenerating(rows) {
  return (rows || []).some((row) => row.reasonStatus === 'generating')
}

/** 规则引擎短句，和 DeepSeek 长解读拆开标 */
const RULE_REASON_RE = /^(品类高度匹配|品类部分匹配|资质匹配|产品高度相关|地域匹配|行业匹配|画像完整|年产能|出口经验丰富|位于|主营|已具备|企业位于|主要产品覆盖|所属行业为|综合匹配度)/

function isRuleReason(text) {
  return RULE_REASON_RE.test(String(text || '').trim())
}

function splitReasons(item) {
  const all = item?.matchReasons || []
  if (item?.reasonStatus !== 'ready') {
    return { ruleReasons: all, aiReasons: [] }
  }
  const ruleReasons = []
  const aiReasons = []
  all.forEach((reason) => {
    if (isRuleReason(reason)) ruleReasons.push(reason)
    else aiReasons.push(reason)
  })
  return { ruleReasons, aiReasons }
}

function stopReasonPoll() {
  if (reasonTimer) {
    clearInterval(reasonTimer)
    reasonTimer = null
  }
}

function startReasonPoll() {
  stopReasonPoll()
  reasonPolls = 0
  reasonTimer = setInterval(async () => {
    reasonPolls += 1
    if (reasonPolls > REASON_POLL_MAX) {
      stopReasonPoll()
      reasonGenerating.value = false
      return
    }
    try {
      const matchRes = await matchSuppliers(requirementId)
      const rows = matchRes.data || []
      list.value = rows
      if (!anyGenerating(rows)) {
        stopReasonPoll()
        reasonGenerating.value = false
      }
    } catch {
      // 轮询失败不打断已展示的名单
    }
  }, REASON_POLL_MS)
}

async function fetchData() {
  loading.value = true
  try {
    const [reqRes, matchRes, connRes, declinedRes] = await Promise.all([
      getRequirementDetail(requirementId),
      matchSuppliers(requirementId),
      listConnections({ requirementId: Number(requirementId), pageSize: 999 }),
      getDeclinedSuppliers(Number(requirementId)),
    ])
    requirement.value = reqRes.data
    list.value = matchRes.data || []
    // 回填已对接的供应商及其状态
    const records = connRes?.data?.records || []
    records.forEach((c) => connectionStatusMap.value.set(c.supplierTenantId, c.status))
    // 回填拒绝过该需求询盘的供应商（标注用）
    declinedSupplierIds.value = new Set(declinedRes?.data || declinedRes || [])
    reasonGenerating.value = anyGenerating(list.value)
    if (reasonGenerating.value) {
      startReasonPoll()
    }
  } catch {
    list.value = []
  } finally { loading.value = false }
}

// ---- 发起对接 ----
const connectVisible = ref(false)
const connectTarget = ref(null)
const connectMessage = ref('')
const connecting = ref(false)

function openConnectDialog(item) {
  connectTarget.value = item
  connectMessage.value = ''
  connectVisible.value = true
}

// ---- 尽调报告预览 ----
const previewVisible = ref(false)
const previewUrl = ref('')

function openPreview(rawUrl) {
  previewUrl.value = getFileUrl(rawUrl)
  previewVisible.value = true
}

function perfColor(score) {
  if (score == null) return '#909399'
  if (score >= 85) return '#67c23a'
  if (score >= 70) return '#e6a23c'
  return '#f56c6c'
}

async function doConnect() {
  connecting.value = true
  try {
    await createConnection({
      requirementId: Number(requirementId),
      supplierTenantId: connectTarget.value.tenantId,
      message: connectMessage.value,
    })
    ElMessage.success('对接意向已发起')
    connectionStatusMap.value.set(connectTarget.value.tenantId, 'pending')
    connectVisible.value = false
  } catch { } finally { connecting.value = false }
}

onMounted(() => fetchData())
onUnmounted(() => stopReasonPoll())
</script>

<style>
.match-card:hover { box-shadow: 0 2px 12px rgba(0,0,0,.08); }
.connect-dialog { width: auto !important; max-width: 520px; }
.preview-dialog { width: 90% !important; max-width: 1100px; }
@keyframes rotate { from { transform: rotate(0deg) } to { transform: rotate(360deg) } }

.match-detail-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}
.reason-block {
  padding: 10px 12px 12px;
  border-radius: 6px;
}
.reason-block + .reason-block {
  margin-top: 10px;
}
.reason-block--rule {
  background: #f7f8fa;
  border: 1px solid #ebeef5;
}
.reason-block--ai {
  background: #f3f8ff;
  border: 1px solid #d6e6fb;
}
.reason-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}
.reason-kicker {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}
.reason-hint {
  font-size: 12px;
  color: #909399;
}
.ai-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 18px;
  padding: 0 5px;
  border-radius: 3px;
  background: #1d6feb;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.04em;
}
.reason-list {
  margin: 0;
  padding-left: 18px;
  line-height: 1.8;
  color: #606266;
  font-size: 13px;
}
.reason-list--ai li {
  color: #303133;
}
.reason-empty,
.reason-pending {
  margin: 0;
  font-size: 13px;
  color: #909399;
}
.reason-pending {
  color: #e6a23c;
}
</style>
