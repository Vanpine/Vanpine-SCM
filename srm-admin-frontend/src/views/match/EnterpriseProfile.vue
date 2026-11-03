<template>
  <div class="enterprise-profile">
    <el-card class="search-card">
      <div class="profile-header">
        <div class="profile-header-left">
          <el-icon :size="22" color="#409eff"><OfficeBuilding /></el-icon>
          <span style="font-size:18px;font-weight:600;color:#303133;letter-spacing:2px">企业画像</span>
          <el-tag v-if="profile" type="primary" effect="dark">{{ profileTypeLabel }}</el-tag>
        </div>
        <div v-if="profile" class="profile-completeness">
          <span>画像完整度</span>
          <el-progress :percentage="profile.completenessScore || 0" :stroke-width="8" style="width:200px" />
        </div>
      </div>
    </el-card>

    <el-card v-loading="loading" class="content-card">
      <el-empty v-if="!profile && !loading" description="企业画像尚未初始化，请等待尽调确认通过后自动生成" />

      <template v-if="profile">
        <el-descriptions title="企业信息" :column="2" label-width="110px" border class="detail-descriptions mb-4">
          <el-descriptions-item label="公司名称"><EmptyText :value="profile.companyName" /></el-descriptions-item>
          <el-descriptions-item label="信用代码"><EmptyText :value="profile.regNumber" /></el-descriptions-item>
          <el-descriptions-item label="所在国家"><EmptyText :value="profile.country" /></el-descriptions-item>
          <el-descriptions-item label="省份 / 城市"><EmptyText :value="profile.province" /> / <EmptyText :value="profile.city" /></el-descriptions-item>
          <el-descriptions-item label="法定代表人"><EmptyText :value="profile.legalRep" /></el-descriptions-item>
          <el-descriptions-item label="注册地址"><EmptyText :value="profile.registeredAddress" /></el-descriptions-item>
          <el-descriptions-item label="联系人"><EmptyText :value="profile.contactName" /></el-descriptions-item>
          <el-descriptions-item label="联系电话"><EmptyText :value="profile.contactPhone" /></el-descriptions-item>
          <el-descriptions-item label="联系邮箱"><EmptyText :value="profile.contactEmail" /></el-descriptions-item>
        </el-descriptions>

        <el-descriptions title="企业详情" :column="2" label-width="110px" border class="detail-descriptions enterprise-detail mb-4">
          <el-descriptions-item label="所属行业"><EmptyText :value="profile.industry" /></el-descriptions-item>
          <el-descriptions-item label="成立年份"><EmptyText :value="profile.establishedYear" /></el-descriptions-item>
          <el-descriptions-item label="员工人数">{{ employeeCountLabel(profile.employeeCount) }}</el-descriptions-item>
          <el-descriptions-item label="年营业额">{{ annualRevenueDisplay }}</el-descriptions-item>
          <el-descriptions-item v-if="isSupplier" label="交货周期">{{ leadTimeLabel(profile.leadTime) }}</el-descriptions-item>
          <el-descriptions-item v-if="isSupplier" label="主要客户" :span="2"><EmptyText :value="profile.keyClients" /></el-descriptions-item>
          <el-descriptions-item v-if="isSupplier" label="主要产品/服务" :span="2"><EmptyText :value="profile.mainProducts" /></el-descriptions-item>
          <el-descriptions-item label="企业简介" :span="2"><EmptyText :value="profile.description" /></el-descriptions-item>
        </el-descriptions>

        <el-descriptions v-if="isBuyer" title="采购方画像" :column="2" label-width="110px" border class="detail-descriptions mb-4">
          <el-descriptions-item label="采购品类" :span="2"><EmptyText :value="profile.buyCategories" /></el-descriptions-item>
          <el-descriptions-item label="采购产品明细" :span="2"><EmptyText :value="profile.procurementDetail" /></el-descriptions-item>
          <el-descriptions-item label="年采购额"><EmptyText :value="buyVolumeDisplay" /></el-descriptions-item>
          <el-descriptions-item label="目标市场"><EmptyText :value="profile.buyMarket" /></el-descriptions-item>
        </el-descriptions>

        <el-descriptions v-if="isSupplier" title="供应方画像" :column="2" label-width="110px" border class="detail-descriptions mb-4">
          <el-descriptions-item label="生产品类" :span="2"><EmptyText :value="profile.supCategories" /></el-descriptions-item>
          <el-descriptions-item label="年产能"><EmptyText :value="profile.supCapacity" /></el-descriptions-item>
          <el-descriptions-item label="出口经验">{{ supExportLabel(profile.supExport) }}</el-descriptions-item>
          <el-descriptions-item label="资质认证" :span="2"><EmptyText :value="profile.supCerts" /></el-descriptions-item>
        </el-descriptions>

        <el-descriptions v-if="isSupplier" title="履约表现" :column="2" label-width="110px" border class="detail-descriptions mb-4">
          <el-descriptions-item label="综合履约分">
            <template v-if="hasPerfData">
              <span class="perf-score" :class="perfScoreClass">{{ profile.perfScore }}</span>
              <span class="text-xs text-gray-400"> / 100</span>
            </template>
            <EmptyText v-else :value="null" />
          </el-descriptions-item>
          <el-descriptions-item label="履约样本">
            <span v-if="hasPerfData">{{ profile.perfSampleSize }} 批</span>
            <EmptyText v-else :value="null" />
          </el-descriptions-item>
          <el-descriptions-item label="准时率">
            <span v-if="hasPerfData">{{ profile.onTimeRate }}%</span>
            <EmptyText v-else :value="null" />
          </el-descriptions-item>
          <el-descriptions-item label="验收合格率">
            <span v-if="hasPerfData">{{ profile.acceptPassRate }}%</span>
            <EmptyText v-else :value="null" />
          </el-descriptions-item>
          <el-descriptions-item label="退回次数">
            <span v-if="hasPerfData">{{ profile.returnCount }} 次</span>
            <EmptyText v-else :value="null" />
          </el-descriptions-item>
        </el-descriptions>
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { OfficeBuilding } from '@element-plus/icons-vue'
import { getProfile } from '@/api/match'
import { supExportLabel, employeeCountLabel, annualRevenueLabel, leadTimeLabel, currencyAmountLabel } from '@/constants'

const BUY_VOLUMES = [
  { value: '1', label: '100万美元以下' },
  { value: '2', label: '100万-500万美元' },
  { value: '3', label: '500万-2000万美元' },
  { value: '4', label: '2000万-1亿美元' },
  { value: '5', label: '1亿美元以上' },
]

const loading = ref(true)
const profile = ref(null)

const isBuyer = computed(() => profile.value?.profileType === 'buyer' || profile.value?.profileType === 'both')
const isSupplier = computed(() => profile.value?.profileType === 'supplier' || profile.value?.profileType === 'both')
const profileTypeLabel = computed(() => {
  const map = { buyer: '采购商', supplier: '供应商', both: '采购商 + 供应商' }
  return map[profile.value?.profileType] || profile.value?.profileType
})
const buyVolumeLabel = computed(() => {
  return BUY_VOLUMES.find(o => o.value === profile.value?.buyVolume)?.label || profile.value?.buyVolume || null
})
const annualRevenueDisplay = computed(() => (
  currencyAmountLabel(profile.value?.annualRevenueAmount, profile.value?.annualRevenueCurrency)
  || annualRevenueLabel(profile.value?.annualRevenue)
))
const buyVolumeDisplay = computed(() => (
  currencyAmountLabel(profile.value?.buyVolumeAmount, profile.value?.buyVolumeCurrency)
  || buyVolumeLabel.value
))
const hasPerfData = computed(() => (profile.value?.perfSampleSize ?? 0) > 0)
const perfScoreClass = computed(() => {
  const s = profile.value?.perfScore
  if (s == null) return ''
  if (s >= 85) return 'perf-good'
  if (s >= 70) return 'perf-mid'
  return 'perf-bad'
})

async function fetchProfile() {
  loading.value = true
  try {
    const { data } = await getProfile()
    profile.value = data
  } catch { profile.value = null } finally { loading.value = false }
}

onMounted(() => { fetchProfile() })
</script>

<style>
.enterprise-profile .profile-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.enterprise-profile .profile-header-left {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
}

.enterprise-profile .profile-completeness {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  font-size: 13px;
  color: #909399;
}

.enterprise-profile .detail-descriptions .el-descriptions__cell {
  vertical-align: top;
}

.enterprise-profile .detail-descriptions .el-descriptions__content {
  padding: 8px 18px;
  line-height: 1.7;
  white-space: normal;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.enterprise-profile .perf-score { font-size: 20px; font-weight: 700; }
.enterprise-profile .perf-good { color: #67c23a; }
.enterprise-profile .perf-mid { color: #e6a23c; }
.enterprise-profile .perf-bad { color: #f56c6c; }
</style>
