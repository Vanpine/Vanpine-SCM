<template>
  <!-- 共享物流事件录入弹窗：RiskDetail / ShipmentManagement / LogisticsTrackingDetail 三处复用 -->
  <el-dialog v-model="visible" title="录入物流节点" :close-on-click-modal="false" class="logistics-event-dialog">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="85px" label-position="right">
      <el-form-item label="节点类型" prop="eventType">
        <el-select v-model="form.eventType" style="width:100%">
          <el-option v-for="opt in LOGISTICS_EVENT_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="发生时间" prop="eventTime">
        <el-date-picker v-model="form.eventTime" type="datetime" style="width:100%" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="请选择发生时间" />
      </el-form-item>
      <el-form-item label="预计到货">
        <el-date-picker v-model="form.estimatedArrivalTime" type="datetime" style="width:100%" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="选填，默认沿用当前预计到货" />
      </el-form-item>
      <el-form-item label="地点/节点">
        <el-input v-model="form.location" placeholder="如：上海港" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="异常/延误原因（选填）" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">确认</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { recordLogisticsEvent } from '@/api/risk'
import { LOGISTICS_EVENT_OPTIONS } from '@/constants/risk'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  deliveryId: { type: [Number, String], default: null },
  // 预计到货默认值：父组件传"最近一次 ETA"，无历史时传合同交期（可传纯日期 YYYY-MM-DD）
  defaultEta: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue', 'success'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const submitting = ref(false)
const formRef = ref(null)
const form = reactive({ eventType: '', eventTime: '', estimatedArrivalTime: '', location: '', remark: '' })
const rules = {
  eventType: [{ required: true, message: '请选择节点类型', trigger: 'change' }],
  eventTime: [{ required: true, message: '请选择发生时间', trigger: 'change' }]
}

/** 兼容纯日期默认值（YYYY-MM-DD），补全为 picker 的 datetime 格式 */
function toPickerValue(v) {
  if (!v) return ''
  return String(v).length === 10 ? `${v}T00:00:00` : v
}

// 打开时重置表单（父组件通过 v-model 控制显隐）；ETA 预填默认值，用户可改可清
function openDialog() {
  const now = new Date()
  const p = (n) => String(n).padStart(2, '0')
  const nowStr = `${now.getFullYear()}-${p(now.getMonth() + 1)}-${p(now.getDate())}T${p(now.getHours())}:${p(now.getMinutes())}:${p(now.getSeconds())}`
  Object.assign(form, { eventType: '', eventTime: nowStr, estimatedArrivalTime: toPickerValue(props.defaultEta), location: '', remark: '' })
  nextTick(() => formRef.value?.clearValidate())
}

async function handleSubmit() {
  try { await formRef.value.validate() } catch { return }
  if (props.deliveryId == null) return
  submitting.value = true
  try {
    await recordLogisticsEvent({
      deliveryId: props.deliveryId,
      eventType: form.eventType,
      eventTime: form.eventTime,
      estimatedArrivalTime: form.estimatedArrivalTime || null,
      location: form.location || null,
      remark: form.remark || null
    })
    ElMessage.success('物流节点已录入')
    visible.value = false
    emit('success')
  } catch { /* ignore */ }
  finally { submitting.value = false }
}

// 弹窗打开时重置表单
watch(visible, (v) => { if (v) openDialog() })
</script>

<style>
.logistics-event-dialog { width: auto !important; max-width: 520px; }
.logistics-event-dialog .el-form-item__label { white-space: nowrap; }
.logistics-event-dialog .el-form-item.is-required .el-form-item__label::before { margin-right: 2px; }
</style>
