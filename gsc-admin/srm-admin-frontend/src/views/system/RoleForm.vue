<template>
  <el-dialog
    :model-value="visible"
    :title="title"
    width="600px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:visible', $event)"
  >
    <el-form
      ref="formRef"
      v-loading="loading"
      :model="form"
      :rules="rules"
      label-width="100px"
    >
      <el-form-item
        label="角色编码"
        prop="roleCode"
      >
        <el-input
          v-model="form.roleCode"
          placeholder="如 TENANT_ADMIN"
          :disabled="!!roleId"
          :maxlength="32"
        />
      </el-form-item>

      <el-form-item
        label="角色名称"
        prop="roleName"
      >
        <el-input
          v-model="form.roleName"
          placeholder="如 企业管理员"
          :maxlength="32"
        />
      </el-form-item>

      <el-form-item
        label="角色描述"
        prop="roleDesc"
      >
        <el-input
          v-model="form.roleDesc"
          type="textarea"
          placeholder="描述该角色的职责和权限范围"
          :rows="3"
          :maxlength="256"
          show-word-limit
        />
      </el-form-item>

      <el-form-item
        v-if="isSuperAdmin"
        label="角色身份"
        prop="roleType"
      >
        <el-select v-model="form.roleType" placeholder="选择角色适用身份" style="width:100%">
          <el-option label="自定义" value="custom" />
          <el-option label="采购方" value="buyer" />
          <el-option label="供应方" value="supplier" />
          <el-option label="通用" value="both" />
          <el-option label="平台" value="platform" />
        </el-select>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleCancel">
        取消
      </el-button>
      <el-button
        type="primary"
        :loading="submitting"
        @click="handleSubmit"
      >
        确定
      </el-button>
    </template>
  </el-dialog>
</template>
<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { getRoleById } from '@/api/role'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
// 后端 Long 序列化为 String，用 String() 兼容旧缓存 number
const isSuperAdmin = computed(() => String(authStore.user?.tenantId) === '1')

const props = defineProps({
  visible: Boolean,
  title: String,
  roleId: {
    type: Number,
    default: null,
  },
})

const emit = defineEmits(['update:visible', 'submit'])

const formRef = ref(null)
const loading = ref(false)
const submitting = ref(false)
const originalData = ref(null)  // 编辑时的原始数据，用于脏检查

const form = reactive({
  roleCode: '',
  roleName: '',
  roleDesc: '',
  roleType: 'custom',
})

const rules = {
  roleCode: [
    { required: true, message: '请输入角色编码', trigger: 'blur' },
    { min: 2, max: 32, message: '编码长度2-32位', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z][a-zA-Z0-9_:]*$/,
      message: '以字母开头，可包含字母、数字、下划线和冒号',
      trigger: 'blur',
    },
  ],
  roleName: [
    { required: true, message: '请输入角色名称', trigger: 'blur' },
    { max: 32, message: '名称不超过32个字符', trigger: 'blur' },
  ],
}

// 弹窗打开时重置 submitting 状态
watch(() => props.visible, (val) => {
  if (val) submitting.value = false
})

// 编辑时获取角色详情
watch(
  () => props.roleId,
  async (id) => {
    if (!id) {
      originalData.value = null
      form.roleCode = ''
      form.roleName = ''
      form.roleDesc = ''
      form.roleType = 'custom'
      return
    }

    loading.value = true
    try {
      // 调用后端获取角色详情接口，回填到表单
      const { data } = await getRoleById(id)
      form.roleCode = data.roleCode || ''
      form.roleName = data.roleName || ''
      form.roleDesc = data.roleDesc || ''
      form.roleType = data.roleType || 'custom'
      // 保存原始数据用于脏检查
      originalData.value = {
        roleName: form.roleName,
        roleDesc: form.roleDesc,
      }
    } catch {
      /* 拦截器已处理 */
    } finally {
      loading.value = false
    }
  },
  { immediate: true },
)

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  // 编辑时检查数据是否变化，未修改则直接关闭
  if (props.roleId && originalData.value) {
    if (form.roleName === originalData.value.roleName &&
        form.roleDesc === originalData.value.roleDesc) {
      emit('update:visible', false)
      return
    }
  }

  emit('submit', { ...form })
}

function handleCancel() {
  emit('update:visible', false)
}
</script>
