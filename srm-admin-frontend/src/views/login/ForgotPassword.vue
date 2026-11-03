<template>
  <div
    class="login-page"
    :class="{ dark: isDark }"
  >
    <!-- 左上角 -->
    <div class="login-header">
      <img
        src="@/assets/icons/logo-icon.png"
        alt="logo"
        class="header-logo"
      >
      <span class="header-name">GLOBAL SCM</span>
    </div>

    <!-- 右上角工具栏 -->
    <div class="login-toolbar">
      <button
        class="tool-btn"
        :title="isDark ? 'Light' : 'Dark'"
        @click="toggleTheme"
      >
        <svg
          v-if="isDark"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.8"
        >
          <circle
            cx="12"
            cy="12"
            r="5"
          />
          <line
            x1="12"
            y1="1"
            x2="12"
            y2="3"
          />
          <line
            x1="12"
            y1="21"
            x2="12"
            y2="23"
          />
          <line
            x1="4.22"
            y1="4.22"
            x2="5.64"
            y2="5.64"
          />
          <line
            x1="18.36"
            y1="18.36"
            x2="19.78"
            y2="19.78"
          />
          <line
            x1="1"
            y1="12"
            x2="3"
            y2="12"
          />
          <line
            x1="21"
            y1="12"
            x2="23"
            y2="12"
          />
          <line
            x1="4.22"
            y1="19.78"
            x2="5.64"
            y2="18.36"
          />
          <line
            x1="18.36"
            y1="5.64"
            x2="19.78"
            y2="4.22"
          />
        </svg>
        <svg
          v-else
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.8"
        >
          <path d="M21 12.79A9 9 0 1111.21 3 7 7 0 0021 12.79z" />
        </svg>
      </button>
      <el-dropdown
        trigger="click"
        @command="setLang"
      >
        <button class="tool-btn lang-btn">
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            class="lang-icon"
          >
            <circle
              cx="12"
              cy="12"
              r="10"
            />
            <line
              x1="2"
              y1="12"
              x2="22"
              y2="12"
            />
            <path d="M12 2a15.3 15.3 0 014 10 15.3 15.3 0 01-4 10 15.3 15.3 0 01-4-10 15.3 15.3 0 014-10z" />
          </svg>
          <span>{{ lang === 'zh' ? '中' : 'EN' }}</span>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="zh">
              <span
                :style="{ fontWeight: lang === 'zh' ? 700 : 400 }"
              >中文</span>
            </el-dropdown-item>
            <el-dropdown-item command="en">
              <span
                :style="{ fontWeight: lang === 'en' ? 700 : 400 }"
              >English</span>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <!-- ========== 主体 ========== -->
    <div class="login-body">
      <LoginBrand
        :title="t('brandTitle')"
        :subtitle="t('brandSubtitle')"
      />

      <div class="right-panel">
        <!-- ========== 步骤①：身份验证 ========== -->
        <div
          v-if="step === 1"
          class="form-wrapper"
        >
          <div class="form-head">
            <h2>{{ t('forgotTitle') }}</h2>
            <p>{{ t('forgotSubtitle') }}</p>
          </div>

          <input
            type="text"
            name="fakeforgot"
            autocomplete="off"
            style="position:fixed;top:-9999px;left:-9999px"
            tabindex="-1"
          >
          <input
            type="email"
            name="fakeforgotemail"
            autocomplete="off"
            style="position:fixed;top:-9999px;left:-9999px"
            tabindex="-1"
          >

          <el-form
            ref="formRef1"
            :model="forgotForm"
            :rules="rules1"
            autocomplete="off"
          >
            <el-form-item prop="tenantCode">
              <el-input
                v-model="forgotForm.tenantCode"
                placeholder="租户编码"
                size="large"
                autocomplete="off"
              />
            </el-form-item>
            <el-form-item prop="email">
              <el-input
                v-model="forgotForm.email"
                placeholder="邮箱"
                size="large"
                autocomplete="off"
              />
            </el-form-item>

            <el-form-item>
              <DragVerify
                v-model="dragPassed"
                :text="t('slideVerify')"
              />
            </el-form-item>

            <el-form-item>
              <button
                class="login-btn"
                :disabled="sendingCode"
                @click.prevent="handleSendCode"
              >
                {{ sendingCode ? '发送中...' : t('sendCode') }}
              </button>
            </el-form-item>
          </el-form>

          <div class="back-login">
            <a
              class="back-link"
              @click="router.push('/login')"
            >← 返回登录</a>
          </div>
        </div>

        <!-- ========== 步骤②：重置密码 ========== -->
        <div
          v-else
          class="form-wrapper"
        >
          <div class="form-head">
            <h2>{{ t('resetTitle') }}</h2>
            <p>{{ t('resetSubtitle') }} <strong>{{ emailDisplay }}</strong></p>
          </div>

          <input
            type="password"
            name="fakereset"
            autocomplete="off"
            style="position:fixed;top:-9999px;left:-9999px"
            tabindex="-1"
          >

          <el-form
            ref="formRef2"
            :model="resetForm"
            :rules="rules2"
            autocomplete="off"
          >
            <el-form-item prop="code">
              <el-input
                v-model="resetForm.code"
                placeholder="6位验证码"
                size="large"
                autocomplete="off"
              >
                <template #suffix>
                  <span
                    v-if="resendCountdown > 0"
                    class="code-countdown"
                  >{{ resendCountdown }}s</span>
                  <span
                    v-else
                    class="code-resend"
                    @click="handleResend"
                  >{{ t('resend') }}</span>
                </template>
              </el-input>
            </el-form-item>
            <el-form-item prop="newPassword">
              <el-input
                v-model="resetForm.newPassword"
                type="password"
                show-password
                placeholder="新密码（至少6位）"
                size="large"
                autocomplete="new-password"
              />
            </el-form-item>
            <el-form-item prop="confirmPassword">
              <el-input
                v-model="resetForm.confirmPassword"
                type="password"
                show-password
                placeholder="确认新密码"
                size="large"
                autocomplete="new-password"
                @keyup.enter="handleReset"
              />
            </el-form-item>

            <el-form-item>
              <button
                class="login-btn"
                :disabled="resetting"
                @click.prevent="handleReset"
              >
                {{ resetting ? '重置中...' : t('resetPasswordBtn') }}
              </button>
            </el-form-item>
          </el-form>

          <div class="back-login">
            <a
              class="back-link"
              @click="router.push('/login')"
            >← 返回登录</a>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useTheme } from '@/composables/useTheme'
import { useI18n } from '@/composables/useI18n'
import { forgotPassword, resetPassword } from '@/api/auth'
import { ElMessage } from 'element-plus'
import DragVerify from '@/components/DragVerify/index.vue'
import LoginBrand from '@/components/LoginBrand.vue'

const router = useRouter()
const route = useRoute()
const { isDark, toggle: toggleTheme } = useTheme()
const { t, current: lang, setLang } = useI18n()

// ---- sessionStorage 读写 ----
const STORAGE_KEY = 'gsc-forgot-pwd'

function saveState(obj) {
  const prev = JSON.parse(sessionStorage.getItem(STORAGE_KEY) || '{}')
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ ...prev, ...obj }))
}

function loadState() {
  return JSON.parse(sessionStorage.getItem(STORAGE_KEY) || '{}')
}

function clearState() {
  sessionStorage.removeItem(STORAGE_KEY)
  sessionStorage.removeItem('gsc-rc-end')
}

// ---- 步骤 ----
const step = ref(1)
const forgotForm = reactive({ tenantCode: '', email: '' })
const resendCountdown = ref(0)
let countdownTimer = null

// 从 sessionStorage 恢复步骤2的状态
function restoreStep2() {
  const state = loadState()
  forgotForm.tenantCode = state.tenantCode || ''
  forgotForm.email = state.email || ''
  const remaining = restoreCountdown()
  if (remaining > 0) {
    startCountdown(remaining)
  }
}

watch(() => route.query.step, (s) => {
  step.value = parseInt(s) === 2 ? 2 : 1
  if (step.value === 2) {
    restoreStep2()
  }
}, { immediate: true })

// ---- 步骤① ----
const formRef1 = ref(null)
const dragPassed = ref(false)
const sendingCode = ref(false)
const rules1 = computed(() => ({
  tenantCode: [{ required: true, message: lang === 'zh' ? '请输入租户编码' : 'Enter tenant code', trigger: 'blur' }],
  email: [
    { required: true, message: lang === 'zh' ? '请输入邮箱' : 'Enter email', trigger: 'blur' },
    { type: 'email', message: lang === 'zh' ? '邮箱格式不正确' : 'Invalid email format', trigger: 'blur' },
  ],
}))

async function handleSendCode() {
  try { await formRef1.value?.validate() } catch { return }
  if (!dragPassed.value) { ElMessage.warning(t('slideVerify')); return }
  sendingCode.value = true
  try {
    await forgotPassword({ tenantCode: forgotForm.tenantCode.trim(), email: forgotForm.email.trim() })
    ElMessage.success(t('codeSent'))
    saveState({ tenantCode: forgotForm.tenantCode.trim(), email: forgotForm.email.trim() })
    startCountdown()
    router.replace({ path: '/forgot-password', query: { step: 2 } })
  } catch { }
  finally { sendingCode.value = false }
}

// ---- 步骤② ----
const formRef2 = ref(null)
const resetting = ref(false)
const resetForm = reactive({ code: '', newPassword: '', confirmPassword: '' })
const emailDisplay = computed(() => forgotForm.email)
const rules2 = computed(() => ({
  code: [{ required: true, message: lang === 'zh' ? '请输入验证码' : 'Enter verification code', trigger: 'blur' }],
  newPassword: [
    { required: true, message: lang === 'zh' ? '请输入新密码' : 'Enter new password', trigger: 'blur' },
    { min: 6, message: lang === 'zh' ? '密码不能少于6位' : 'Min. 6 characters', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: lang === 'zh' ? '请确认新密码' : 'Confirm new password', trigger: 'blur' },
    { validator: (_r, v, cb) => v !== resetForm.newPassword ? cb(new Error(t('passwordMismatch'))) : cb(), trigger: 'blur' },
  ],
}))

function startCountdown(fromRemaining = 0) {
  // 清除旧定时器
  if (countdownTimer) clearInterval(countdownTimer)
  const remaining = fromRemaining > 0 ? fromRemaining : 60
  const endTime = Date.now() + remaining * 1000
  sessionStorage.setItem('gsc-rc-end', endTime.toString())
  resendCountdown.value = remaining
  countdownTimer = setInterval(() => {
    const left = Math.round((endTime - Date.now()) / 1000)
    if (left <= 0) {
      resendCountdown.value = 0
      clearInterval(countdownTimer)
      countdownTimer = null
      sessionStorage.removeItem('gsc-rc-end')
    } else {
      resendCountdown.value = left
    }
  }, 200)
}

// 从 sessionStorage 恢复剩余秒数
function restoreCountdown() {
  const endStr = sessionStorage.getItem('gsc-rc-end')
  if (!endStr) return 0
  const left = Math.round((parseInt(endStr) - Date.now()) / 1000)
  return left > 0 ? left : 0
}

async function handleResend() {
  if (resendCountdown.value > 0) return
  sendingCode.value = true
  try {
    await forgotPassword({ tenantCode: forgotForm.tenantCode.trim(), email: emailDisplay.value })
    ElMessage.success(t('codeResent'))
    startCountdown()
  } catch { }
  finally { sendingCode.value = false }
}

async function handleReset() {
  try { await formRef2.value?.validate() } catch { return }
  resetting.value = true
  try {
    await resetPassword({
      tenantCode: forgotForm.tenantCode.trim(),
      email: forgotForm.email,
      code: resetForm.code.trim(),
      newPassword: resetForm.newPassword,
    })
    ElMessage.success(t('resetSuccess'))
    clearState()
    router.push('/login')
  } catch { }
  finally { resetting.value = false }
}
</script>

<style>
.login-page {
  --lp-bg-left: linear-gradient(160deg, #0a1628 0%, #0f1f3a 30%, #132b4f 60%, #0d1f38 100%);
  --lp-bg-right: #ffffff;
  --lp-title-color: #ffffff;
  --lp-subtitle-color: rgba(255, 255, 255, 0.7);
  --lp-text-primary: #1d1d2e;
  --lp-text-secondary: #8b8fa8;
  --lp-input-bg: #f5f6fa;
  --lp-input-border: #e4e7f0;
  --lp-input-text: #1d1d2e;
  --lp-input-placeholder: #b8bcc8;
  --lp-btn-bg: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  --lp-header-color: #ffffff;
}

.login-page.dark {
  --lp-bg-left: linear-gradient(160deg, #0f1a2e 0%, #0d1525 50%, #0a101c 100%);
  --lp-bg-right: #1a1d2e;
  --lp-text-primary: #e8e9f0;
  --lp-text-secondary: #8b8fa8;
  --lp-input-bg: #2a2d3e;
  --lp-input-border: #3a3d52;
  --lp-input-text: #e8e9f0;
  --lp-input-placeholder: #5c6080;
  --lp-btn-bg: linear-gradient(135deg, #5d87ff 0%, #7c5cfc 100%);
  --lp-header-color: #e8e9f0;
}
</style>
<style scoped>
.login-page {
  position: relative;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Display', 'Helvetica Neue', 'PingFang SC', sans-serif;
  background: var(--lp-bg-right);
  transition: background 0.35s ease;
  display: flex;
  flex-direction: column;
}

.login-header {
  position: fixed;
  top: 28px;
  left: 36px;
  z-index: 100;
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-logo {
  width: 32px;
  height: 32px;
  object-fit: contain;
}

.header-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--lp-header-color);
  letter-spacing: 1px;
  transition: color 0.35s ease;
}

.login-toolbar {
  position: fixed;
  top: 24px;
  right: 32px;
  z-index: 100;
  display: flex;
  align-items: center;
  gap: 10px;
}

.tool-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--lp-text-secondary);
  transition: all 0.2s;
}

.tool-btn:hover {
  background: var(--lp-input-bg);
  color: var(--lp-text-primary);
}

.tool-btn svg {
  width: 18px;
  height: 18px;
}

.lang-btn {
  width: auto;
  padding: 0 10px;
  gap: 5px;
  font-size: 13px;
  font-weight: 600;
  font-family: inherit;
}

.lang-icon {
  width: 16px;
  height: 16px;
}

.login-body {
  display: flex;
  height: 100%;
}

.right-panel {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.form-wrapper {
  width: 460px;
  padding: 40px;
}

.form-head {
  margin-bottom: 40px;
}

.form-head h2 {
  font-size: 42px;
  font-weight: 700;
  color: var(--lp-text-primary);
  margin-bottom: 8px;
  transition: color 0.35s ease;
}

.form-head p {
  font-size: 18px;
  color: var(--lp-text-secondary);
}

.form-head strong {
  color: #5d87ff;
  font-weight: 600;
}

:deep(.el-form-item) {
  margin-bottom: 24px;
}

:deep(.el-input__wrapper) {
  background: var(--lp-input-bg) !important;
  border: 1.5px solid var(--lp-input-border) !important;
  border-radius: 10px !important;
  box-shadow: none !important;
  padding: 6px 16px !important;
  transition: all 0.25s !important;
}

:deep(.el-input__wrapper:hover) {
  border-color: var(--lp-input-placeholder) !important;
}

:deep(.el-input__wrapper.is-focus) {
  border-color: #5d87ff !important;
  box-shadow: 0 0 0 3px rgba(93, 135, 255, 0.1) !important;
}

:deep(.el-input__inner) {
  font-size: 16px !important;
  color: var(--lp-input-text) !important;
  font-family: inherit !important;
}

:deep(.el-input__inner::placeholder) {
  color: var(--lp-input-placeholder) !important;
  font-size: 16px !important;
}

.login-btn {
  width: 100%;
  height: 50px;
  border: none;
  border-radius: 10px;
  margin-top: 15px;
  background: var(--lp-btn-bg);
  color: #fff;
  font-size: 18px;
  font-weight: 600;
  font-family: inherit;
  letter-spacing: 2px;
  cursor: pointer;
  transition: all 0.25s;
}

.login-btn:hover {
  opacity: 0.9;
  box-shadow: 0 6px 20px rgba(59, 130, 246, 0.35);
  transform: translateY(-1px);
}

.login-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.code-countdown {
  font-size: 13px;
  color: var(--lp-text-secondary);
  white-space: nowrap;
  padding-right: 4px;
}

.code-resend {
  font-size: 13px;
  color: #5d87ff;
  cursor: pointer;
  white-space: nowrap;
  padding-right: 4px;
  user-select: none;
}

.code-resend:hover {
  color: #3b6de6;
}

.back-login {
  margin-top: 20px;
  text-align: center;
}

.back-login a,
.back-link {
  font-size: 14px;
  color: var(--lp-text-secondary);
  cursor: pointer;
  text-decoration: none;
  transition: color 0.2s;
}

.back-login a:hover,
.back-link:hover {
  color: #5d87ff;
}

@media (max-width: 1180px) {
  .left-panel {
    display: none;
  }

  .right-panel {
    flex: 1;
  }
}

@media (max-width: 500px) {
  .form-wrapper {
    width: 100%;
    padding: 30px;
  }

  .login-header {
    left: 20px;
  }

  .login-toolbar {
    right: 16px;
  }
}
</style>
