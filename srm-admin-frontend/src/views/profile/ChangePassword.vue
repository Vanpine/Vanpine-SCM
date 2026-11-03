<template>
  <div class="password-page">
    <div class="password-container">
      <el-card
        shadow="never"
        class="form-card"
      >
        <template #header>
          <span class="card-title">修改密码</span>
        </template>
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-width="110px"
          style="max-width: 420px"
          class="password-form"
        >
          <el-form-item
            label="旧密码"
            prop="oldPassword"
          >
            <el-input
              v-model="form.oldPassword"
              type="password"
              show-password
              placeholder="请输入旧密码"
            />
          </el-form-item>
          <el-form-item
            label="新密码"
            prop="newPassword"
          >
            <el-input
              v-model="form.newPassword"
              type="password"
              show-password
              placeholder="请输入新密码（至少6位）"
            />
          </el-form-item>
          <el-form-item
            label="确认密码"
            prop="confirmPassword"
          >
            <el-input
              v-model="form.confirmPassword"
              type="password"
              show-password
              placeholder="请再次输入新密码"
            />
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              :loading="loading"
              @click="handleSubmit"
            >
              确认修改
            </el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <div class="tips-card">
        <p class="tips-title">
          密码要求
        </p>
        <ul class="tips-list">
          <li>至少 6 位字符</li>
          <li>建议包含大小写字母、数字和特殊字符</li>
          <li>修改密码后需要重新登录</li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { changePassword } from '@/api/auth'
import { ElMessage } from 'element-plus'

const router = useRouter()
const authStore = useAuthStore()

const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const rules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码不能少于6位', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value && value === form.oldPassword) {
          callback(new Error('新密码不能与旧密码相同'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  loading.value = true
  try {
    // 调用后端修改密码接口（后端会校验旧密码是否正确）
    await changePassword({
      oldPassword: form.oldPassword,
      newPassword: form.newPassword,
    })
    ElMessage.success('密码修改成功，请重新登录')
    // 修改密码后需要重新登录
    authStore.logout()
    router.push('/login')
  } catch {
    // 错误已在拦截器中处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.password-page {
  width: 100%;
}

.password-container {
  max-width: 520px;
}

.password-form :deep(.el-form-item__label) {
  white-space: nowrap;
}

.password-form :deep(.el-form-item.is-required .el-form-item__label::before) {
  margin-right: 4px;
}

.form-card {
  border-radius: 12px;
  margin-bottom: 16px;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.tips-card {
  padding: 16px 20px;
  background: #f8fafc;
  border-radius: 10px;
  border: 1px solid #edf0f4;
}

.tips-title {
  font-size: 13px;
  font-weight: 600;
  color: #64748b;
  margin: 0 0 8px 0;
}

.tips-list {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  color: #94a3b8;
  line-height: 1.8;
}
</style>
