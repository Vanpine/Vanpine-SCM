<template>
  <el-dialog :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)"
    title="企业画像" :close-on-click-modal="false" class="profile-dialog" top="3vh">
    <template v-if="profileData">
      <el-descriptions :column="1" border class="detail-descriptions">

        <!-- ======== 公共信息（双方都关心） ======== -->
        <el-descriptions-item label="企业名称">{{ profileData.companyName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="信用代码">{{ profileData.regNumber || '-' }}</el-descriptions-item>
        <el-descriptions-item label="所属行业">{{ profileData.industry || '-' }}</el-descriptions-item>
        <el-descriptions-item label="成立年份">{{ profileData.establishedYear || '-' }}</el-descriptions-item>
        <el-descriptions-item label="所在地区">
          {{ profileData.province || '' }}{{ profileData.city ? ' · ' + profileData.city : '' }}
        </el-descriptions-item>
        <el-descriptions-item label="法定代表人">{{ profileData.legalRep || '-' }}</el-descriptions-item>
        <el-descriptions-item label="注册地址">{{ profileData.registeredAddress || '-' }}</el-descriptions-item>

        <!-- ======== 采购商侧（供应商查看采购商时显示） ======== -->
        <template v-if="viewMode === 'buyer'">
          <el-descriptions-item v-if="profileData.buyCategories" label="采购品类">
            {{ profileData.buyCategories }}
          </el-descriptions-item>
          <el-descriptions-item v-if="profileData.procurementDetail" label="采购产品明细">
            {{ profileData.procurementDetail || '-' }}
          </el-descriptions-item>
          <el-descriptions-item v-if="profileData.buyVolumeAmount || profileData.buyVolume" label="年采购额">
            {{ currencyAmountLabel(profileData.buyVolumeAmount, profileData.buyVolumeCurrency) || buyVolumeLabel(profileData.buyVolume) }}
          </el-descriptions-item>
          <el-descriptions-item v-if="profileData.buyMarket" label="目标市场">
            {{ profileData.buyMarket || '-' }}
          </el-descriptions-item>
        </template>

        <!-- ======== 供应商侧（采购商查看供应商时显示） ======== -->
        <template v-if="viewMode === 'supplier'">
          <el-descriptions-item v-if="profileData.supCategories" label="生产品类">
            {{ profileData.supCategories }}
          </el-descriptions-item>
          <el-descriptions-item v-if="profileData.mainProducts" label="主要产品">
            {{ profileData.mainProducts || '-' }}
          </el-descriptions-item>
          <el-descriptions-item v-if="profileData.supCapacity" label="年产能">
            {{ profileData.supCapacity }}
          </el-descriptions-item>
          <el-descriptions-item v-if="profileData.supCerts" label="资质认证">
            {{ profileData.supCerts }}
          </el-descriptions-item>
          <el-descriptions-item v-if="profileData.supExport" label="出口经验">
            {{ supExportLabel(profileData.supExport) }}
          </el-descriptions-item>
          <el-descriptions-item v-if="profileData.keyClients" label="主要客户">
            {{ profileData.keyClients || '-' }}
          </el-descriptions-item>
          <el-descriptions-item v-if="profileData.leadTime" label="交货周期">
            {{ leadTimeLabel(profileData.leadTime) }}
          </el-descriptions-item>
          <el-descriptions-item v-if="profileData.perfSampleSize" label="履约表现">
            <span style="font-weight:700" :style="{ color: perfColor(profileData.perfScore) }">{{ profileData.perfScore }}</span>
            <span style="color:#909399"> / 100 · 准时率 {{ profileData.onTimeRate }}% · 合格率 {{ profileData.acceptPassRate }}% · 退回 {{ profileData.returnCount }} 次 · 样本 {{ profileData.perfSampleSize }} 批</span>
          </el-descriptions-item>
        </template>

        <!-- ======== 企业简介（末尾，有则显示） ======== -->
        <el-descriptions-item v-if="profileData.description" label="企业简介">
          {{ profileData.description }}
        </el-descriptions-item>
      </el-descriptions>

      <!-- 尽调信息 -->
      <div v-if="profileData.ddFirmName || profileData.ddReportUrl"
        style="margin-top:16px;padding:12px;background:#f5f7fa;border-radius:6px;font-size:13px;color:#606266;display:flex;align-items:center;gap:24px;flex-wrap:wrap">
        <span v-if="profileData.ddFirmName">核查事务所：{{ profileData.ddFirmName }}</span>
        <span v-if="profileData.ddFirmRemark">核查结论：{{ profileData.ddFirmRemark }}</span>
        <span v-if="profileData.ddReportUrl" style="color:#409eff;cursor:pointer" @click="openReport(profileData.ddReportUrl)">查看尽调报告 →</span>
      </div>
    </template>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>

  <!-- 尽调报告预览 -->
  <el-dialog v-model="previewVisible" title="尽调报告" :close-on-click-modal="false" class="preview-dialog" top="3vh">
    <iframe v-if="previewUrl" :src="previewUrl" style="width:100%;height:70vh;border:none;border-radius:4px" />
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { getFileUrl } from '@/utils/file'
import { supExportLabel, buyVolumeLabel, leadTimeLabel, currencyAmountLabel } from '@/constants'

// 尽调报告预览
const previewVisible = ref(false)
const previewUrl = ref('')
function openReport(url) {
  previewUrl.value = getFileUrl(url)
  previewVisible.value = true
}

function perfColor(score) {
  if (score == null) return '#909399'
  if (score >= 85) return '#67c23a'
  if (score >= 70) return '#e6a23c'
  return '#f56c6c'
}

defineProps({
  modelValue: { type: Boolean, default: false },
  profileData: { type: Object, default: null },
  /** 'supplier' = 采购商视角看供应商 | 'buyer' = 供应商视角看采购商 */
  viewMode: { type: String, default: 'supplier' },
})
defineEmits(['update:modelValue'])
</script>

<style>
.profile-dialog { width: 65% !important; max-width: 1100px; min-width: 760px; }
.preview-dialog { width: 90% !important; max-width: 1100px; }
</style>
