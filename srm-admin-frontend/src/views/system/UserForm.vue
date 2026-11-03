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
      autocomplete="off"
    >
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
            <el-icon><Plus /></el-icon> 选择图片
          </el-button>
          <template #tip>
            <div class="el-upload__tip">
              支持 jpg/png，不超过 2MB
            </div>
          </template>
        </el-upload>
      </el-form-item>

      <el-form-item
        label="用户名"
        prop="username"
      >
        <el-input
          v-model="form.username"
          placeholder="请输入用户名"
          :maxlength="32"
          autocomplete="off"
        />
      </el-form-item>

      <el-form-item
        label="密码"
        prop="password"
      >
        <el-input
          v-model="form.password"
          type="password"
          show-password
          :placeholder="userId ? '留空则不修改密码' : '请输入密码'"
          :maxlength="64"
          autocomplete="new-password"
        />
      </el-form-item>

      <el-form-item
        label="姓名"
        prop="realName"
      >
        <el-input
          v-model="form.realName"
          placeholder="请输入真实姓名"
          :maxlength="32"
        />
      </el-form-item>

      <el-form-item
        label="邮箱"
        prop="email"
      >
        <el-input
          v-model="form.email"
          placeholder="请输入邮箱"
          :maxlength="128"
        />
      </el-form-item>

      <el-form-item
        label="手机号"
        prop="phone"
      >
        <el-input
          v-model="form.phone"
          placeholder="请输入手机号"
          :maxlength="16"
        />
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
import { ref, reactive, watch } from 'vue'
import { getUserById } from '@/api/user'
import { uploadFile } from '@/api/upload'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'

const props = defineProps({
  visible: Boolean,
  title: String,
  userId: {
    type: Number,
    default: null,
  },
})

const emit = defineEmits(['update:visible', 'submit'])

const formRef = ref(null)
const uploadRef = ref(null)
const loading = ref(false)
const submitting = ref(false)
const uploading = ref(false)
const originalData = ref(null)  // 编辑时的原始数据，用于脏检查

const form = reactive({
  username: '',
  password: '',
  realName: '',
  email: '',
  phone: '',
  avatarUrl: '',
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 32, message: '用户名长度3-32位', trigger: 'blur' },
  ],
  password: [
    {
      required: true,
      message: '请输入密码',
      trigger: 'blur',
      validator: (_rule, value, callback) => {
        // 编辑时密码非必填，留空表示不修改
        if (props.userId && !value) {
          callback()
          return
        }
        if (!props.userId && !value) {
          callback(new Error('请输入密码'))
          return
        }
        if (value && value.length < 6) {
          callback(new Error('密码至少6位'))
          return
        }
        callback()
      },
    },
  ],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
}

// 弹窗打开时重置 submitting 状态
watch(() => props.visible, (val) => {
  if (val) submitting.value = false
})

// 编辑时获取用户详情
watch(
  () => props.userId,
  async (id) => {
    if (!id) {
      originalData.value = null
      form.username = ''
      form.password = ''
      form.realName = ''
      form.email = ''
      form.phone = ''
      form.avatarUrl = ''
      return
    }

    loading.value = true
    try {
      // 调用后端获取用户详情接口，回填到表单
      const { data } = await getUserById(id)
      form.username = data.username || ''
      form.password = ''
      form.realName = data.realName || ''
      form.email = data.email || ''
      form.phone = data.phone || ''
      form.avatarUrl = data.avatarUrl || ''
      // 保存原始数据用于脏检查
      originalData.value = {
        username: form.username,
        realName: form.realName,
        email: form.email,
        phone: form.phone,
        avatarUrl: form.avatarUrl,
      }
    } catch {
      /* 拦截器已处理 */
    } finally {
      loading.value = false
    }
  },
  { immediate: true },
)

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
/** 上传头像到 OSS */
async function handleAvatarUpload({ file }) {
  try {
    // 调用后端上传接口，上传文件到阿里云 OSS，返回预签名URL
    const { data } = await uploadFile(file, 'avatar')
    form.avatarUrl = data.url  // OSS 预签名 URL，30天有效
    ElMessage.success('头像上传成功')
  } catch {
    /* 拦截器已处理 */
  } finally {
    uploading.value = false
  }
}

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  // 编辑时检查数据是否变化，未修改则直接关闭
  if (props.userId && originalData.value) {
    const unchanged =
      form.username === originalData.value.username &&
      form.realName === originalData.value.realName &&
      form.email === originalData.value.email &&
      form.phone === originalData.value.phone &&
      form.avatarUrl === originalData.value.avatarUrl &&
      !form.password  // 密码留空表示不修改
    if (unchanged) {
      emit('update:visible', false)
      return
    }
  }

  submitting.value = true
  const submitData = { ...form }
  // 编辑时密码留空则不传
  if (props.userId && !submitData.password) {
    delete submitData.password
  }
  emit('submit', submitData)
}

function handleCancel() {
  emit('update:visible', false)
}
</script>
