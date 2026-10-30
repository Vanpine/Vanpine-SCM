<template>
  <div class="profile-page">
    <div class="profile-container">
      <!-- 用户概要卡片 -->
      <div class="profile-header-card">
        <div class="avatar-section">
          <img
            v-if="authStore.user?.avatarUrl"
            :src="authStore.user.avatarUrl"
            class="profile-avatar-img"
          >
          <el-avatar
            v-else
            :size="80"
            icon="UserFilled"
            class="profile-avatar-placeholder"
          />
        </div>
        <div class="user-summary">
          <h2 class="user-display-name">
            {{ authStore.user?.realName || authStore.user?.username || '未设置' }}
          </h2>
          <div class="user-tags">
            <el-tag
              type="primary"
              size="small"
            >
              {{ authStore.tenantCode }}
            </el-tag>
            <el-tag
              v-for="r in (authStore.user?.roleNames || [])"
              :key="r"
              size="small"
              type="info"
            >
              {{ r }}
            </el-tag>
          </div>
        </div>
      </div>

      <!-- 编辑表单 -->
      <el-card
        shadow="never"
        class="form-card"
      >
        <template #header>
          <span class="card-title">个人资料</span>
        </template>
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-width="90px"
          style="max-width: 500px"
        >
          <el-form-item
            label="用户名"
            prop="username"
          >
            <el-input
              v-model="form.username"
              placeholder="请输入用户名"
            />
          </el-form-item>
          <el-form-item
            label="真实姓名"
            prop="realName"
          >
            <el-input
              v-model="form.realName"
              placeholder="请输入真实姓名"
            />
          </el-form-item>
          <el-form-item
            label="邮箱"
            prop="email"
          >
            <el-input
              v-model="form.email"
              placeholder="请输入邮箱"
            />
          </el-form-item>
          <el-form-item
            label="手机号"
            prop="phone"
          >
            <el-input
              v-model="form.phone"
              placeholder="请输入手机号"
            />
          </el-form-item>

          <!-- 头像上传 -->
          <el-form-item label="头像">
            <el-upload
              ref="uploadRef"
              class="avatar-uploader"
              :show-file-list="true"
              :before-upload="beforeAvatarUpload"
              :http-request="handleAvatarUpload"
              accept="image/*"
              list-type="picture"
            >
              <el-button
                type="primary"
                :loading="uploading"
                plain
              >
                <el-icon>
                  <Plus />
                </el-icon> 选择图片
              </el-button>
              <template #tip>
                <div class="el-upload__tip">
                  支持 jpg/png，不超过 2MB
                </div>
              </template>
            </el-upload>
          </el-form-item>

          <el-form-item>
            <el-button
              type="primary"
              :loading="loading"
              @click="handleSubmit"
            >
              保存修改
            </el-button>
            <el-button @click="handleReset">
              重置
            </el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { uploadFile } from '@/api/upload'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'

const authStore = useAuthStore()

const formRef = ref(null)
const loading = ref(false)
const uploading = ref(false)

const form = reactive({
  username: '',
  realName: '',
  email: '',
  phone: '',
  /** 已上传头像的 OSS key（提交用；后端只回传现签 URL，不回传 key） */
  avatarKey: '',
  /** 头像访问 URL（仅用于本地即时预览） */
  avatarUrl: '',
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 64, message: '用户名长度2-64位', trigger: 'blur' },
  ],
  email: [
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' },
  ],
  phone: [
    {
      validator: (_rule, value, callback) => {
        if (value && !/^1[3-9]\d{9}$/.test(value)) {
          callback(new Error('请输入正确的手机号'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

/** 上传前校验 */
function beforeAvatarUpload(file) {
  const isImage = file.type.startsWith('image/')
  const isLt2M = file.size / 1024 / 1024 < 2
  if (!isImage) { ElMessage.error('只能上传图片文件'); return false }
  if (!isLt2M) { ElMessage.error('图片大小不能超过 2MB'); return false }
  uploading.value = true
  return true
}


/** 上传头像到 OSS */
/** 上传头像到 OSS，完成后刷新用户信息以更新头部头像 */
async function handleAvatarUpload({ file }) {
  try {
    // 调用后端上传接口，上传文件到阿里云 OSS
    const { data } = await uploadFile(file, 'avatar')
    // key 用于提交落库（存 key 不存 URL——签名会过期变裂图）；url 仅用于本地即时预览
    form.avatarKey = data.key
    form.avatarUrl = data.url
    await authStore.updateProfile({ avatarKey: data.key })
    // 刷新用户信息以同步头部头像显示
    await authStore.fetchUserInfo()
    ElMessage.success('头像上传成功')
  } catch {
    /* 拦截器已处理 */
  } finally {
    uploading.value = false
  }
}

/** 从 store 初始化表单 */
/**
 * 从 store 初始化表单。
 * <p>avatarKey 不在此处重置——后端只回传现签 URL 不回传 key，
 * 清空会导致「重置后保存」把已上传的头像 key 弄丢。
 */
function initForm() {
  const user = authStore.user
  if (user) {
    form.username = user.username || ''
    form.realName = user.realName || ''
    form.email = user.email || ''
    form.phone = user.phone || ''
    form.avatarUrl = user.avatarUrl || ''
  }
}

function handleReset() {
  initForm()
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
    // 调用后端更新个人资料接口，store 内会同步更新 localStorage
    await authStore.updateProfile({
      username: form.username,
      realName: form.realName,
      email: form.email,
      phone: form.phone,
      avatarKey: form.avatarKey,
    })
    ElMessage.success('个人信息更新成功')
  } catch {
    // 错误已在拦截器中处理
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  initForm()
})
</script>

<style scoped>
.profile-page {
  width: 100%;
}

.profile-container {
  max-width: 700px;
}

.profile-header-card {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 32px;
  margin-bottom: 16px;
  background: linear-gradient(135deg, #1a2332 0%, #1e3048 100%);
  border-radius: 12px;
}

.avatar-section {
  flex-shrink: 0;
}

.user-summary {
  color: #fff;
}

.user-display-name {
  margin: 0 0 10px 0;
  font-size: 22px;
  font-weight: 700;
}

.user-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.form-card {
  border-radius: 12px;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.profile-avatar-img {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  object-fit: cover;
  border: 3px solid rgba(255, 255, 255, 0.3);
}
</style>
