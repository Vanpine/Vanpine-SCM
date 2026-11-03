<template>
  <el-dialog v-model="visible" title="分配事务所" top="8vh" :close-on-click-modal="false" class="assign-firm-dialog"
    @close="handleCancel">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" label-position="right"
      class="assign-firm-form">
      <!-- 选择事务所（远程搜索） -->
      <el-form-item label="选择事务所" prop="firmId">
        <el-select v-model="form.firmId" filterable remote reserve-keyword :remote-method="handleFirmSearch"
          :loading="firmLoading" placeholder="输入事务所名称搜索" style="width: 100%" @change="handleFirmSelect">
          <el-option v-for="f in firmOptions" :key="f.firmId" :label="f.firmName" :value="f.firmId">
            <div class="firm-option">
              <span class="font-medium">{{ f.firmName }}</span>
              <span class="text-xs text-gray-400 ml-2">{{ f.province }}{{ f.city }} · {{ f.serviceSector || '-'
              }}</span>
            </div>
          </el-option>
        </el-select>
      </el-form-item>

      <!-- 联系人信息（选中事务所后自动回填） -->
      <el-form-item label="联系人" prop="firmContactName">
        <el-input v-model="form.firmContactName" placeholder="自动填入，可手动修改" maxlength="50" />
      </el-form-item>
      <el-form-item label="联系邮箱" prop="firmContactEmail">
        <el-input v-model="form.firmContactEmail" placeholder="自动填入，可手动修改" maxlength="100" />
      </el-form-item>
      <el-form-item label="联系电话">
        <el-input v-model="form.firmContactPhone" placeholder="自动填入，可手动修改" maxlength="20" />
      </el-form-item>

      <!-- 截止日期 + 特殊要求 -->
      <el-form-item label="截止日期">
        <el-date-picker v-model="form.deadline" type="date" placeholder="选择截止日期（可选）" value-format="YYYY-MM-DD"
          style="width: 100%" />
      </el-form-item>
      <el-form-item label="特殊要求">
        <el-input v-model="form.instruction" type="textarea" :rows="3" placeholder="特殊核查要求或说明（可选）" maxlength="500"
          show-word-limit />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleCancel">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleConfirm">
        确认分配
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { assignFirm, searchFirms } from '@/api/application'

const props = defineProps({
  visible: { type: Boolean, default: false },
  applicationId: { type: Number, required: true },
  companyName: { type: String, default: '' },
})

const emit = defineEmits(['update:visible', 'success'])

const visible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val),
})

const formRef = ref(null)
const submitting = ref(false)
const firmLoading = ref(false)
const firmOptions = ref([])

const form = reactive({
  firmId: null,
  firmName: '',
  firmContactName: '',
  firmContactEmail: '',
  firmContactPhone: '',
  deadline: '',
  instruction: '',
})

const rules = {
  firmId: [{ required: true, message: '请选择事务所', trigger: 'change' }],
  firmContactName: [{ required: true, message: '请输入联系人', trigger: 'blur' }],
  firmContactEmail: [
    { required: true, message: '请输入联系邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
}

/** 弹窗打开时重置 */
// immediate：父组件 v-if 卸载重挂载时首次 visible 即为 true，也需重置
watch(() => props.visible, (val) => {
  if (val) {
    form.firmId = null
    form.firmName = ''
    form.firmContactName = ''
    form.firmContactEmail = ''
    form.firmContactPhone = ''
    form.deadline = ''
    form.instruction = ''
    firmOptions.value = []
    formRef.value?.clearValidate()
    // 打开时默认加载全部事务所
    handleFirmSearch('')
  }
}, { immediate: true })

/** 远程搜索事务所 */
async function handleFirmSearch(keyword) {
  firmLoading.value = true
  try {
    const { data } = await searchFirms(keyword || '')
    firmOptions.value = data || []
  } catch {
    firmOptions.value = []
  } finally {
    firmLoading.value = false
  }
}

/** 选中事务所后自动回填信息（String() 兼容后端 Long→String） */
function handleFirmSelect(firmId) {
  const firm = firmOptions.value.find(f => String(f.firmId) === String(firmId))
  if (firm) {
    form.firmName = firm.firmName || ''
    form.firmContactName = firm.contactName || ''
    form.firmContactEmail = firm.contactEmail || ''
    form.firmContactPhone = firm.contactPhone || ''
  }
}

function handleCancel() {
  emit('update:visible', false)
}

async function handleConfirm() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await assignFirm(props.applicationId, {
      firmId: form.firmId,
      firmName: form.firmName,
      firmContactName: form.firmContactName,
      firmContactEmail: form.firmContactEmail,
      firmContactPhone: form.firmContactPhone || undefined,
      deadline: form.deadline || undefined,
      instruction: form.instruction || undefined,
    })
    ElMessage.success('事务所分配成功')
    emit('update:visible', false)
    emit('success')
  } catch {
    // 拦截器已处理
  } finally {
    submitting.value = false
  }
}
</script>

<style>
/* 弹窗自适应宽度 */
.assign-firm-dialog {
  width: auto !important;
  max-width: 560px;
}
</style>

<style scoped>
.firm-option {
  display: flex;
  align-items: center;
}

/* 表单：星号紧跟label、label不换行 */
.assign-firm-form :deep(.el-form-item__label) {
  white-space: nowrap;
}

.assign-firm-form :deep(.el-form-item.is-required .el-form-item__label::before) {
  margin-right: 2px;
}
</style>
