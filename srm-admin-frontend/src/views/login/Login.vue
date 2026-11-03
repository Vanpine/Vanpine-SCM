<template>
  <div
    class="login-page"
    :class="{ dark: isDark }"
  >
    <!-- ========== 左上角系统标题 ========== -->
    <div class="login-header">
      <img
        src="@/assets/icons/logo-icon.png"
        alt="logo"
        class="header-logo"
      >
      <span class="header-name">GLOBAL SCM</span>
    </div>

    <!-- ========== 右上角：主题切换 + 语言切换 ========== -->
    <div class="login-toolbar">
      <button
        class="tool-btn"
        :title="isDark ? '亮色模式' : '暗色模式'"
        @click="toggleTheme"
      >
        <!-- 太阳图标 -->
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
        <!-- 月亮图标 -->
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
              <span :style="{ fontWeight: lang === 'zh' ? 700 : 400 }">中文</span>
            </el-dropdown-item>
            <el-dropdown-item command="en">
              <span :style="{ fontWeight: lang === 'en' ? 700 : 400 }">English</span>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <!-- ========== 主体：左右分栏 ========== -->
    <div class="login-body">
      <LoginBrand
        :title="t('brandTitle')"
        :subtitle="t('brandSubtitle')"
      />

      <div class="right-panel">
        <div class="form-wrapper">
          <div class="form-head">
            <h2>{{ t('welcome') }}</h2>
            <p>{{ t('subtitle') }}</p>
          </div>

          <!-- 防止浏览器自动填充 -->
          <input
            type="text"
            name="fakeuser"
            autocomplete="off"
            style="position:fixed;top:-9999px;left:-9999px"
            tabindex="-1"
          >
          <input
            type="password"
            name="fakepass"
            autocomplete="off"
            style="position:fixed;top:-9999px;left:-9999px"
            tabindex="-1"
          >

          <el-form
            ref="loginFormRef"
            :model="loginForm"
            :rules="loginRules"
            autocomplete="off"
          >
            <el-form-item prop="tenantCode">
              <el-input
                v-model="loginForm.tenantCode"
                placeholder="租户编码"
                size="large"
                autocomplete="off"
              />
            </el-form-item>
            <el-form-item prop="username">
              <el-input
                v-model="loginForm.username"
                placeholder="用户名"
                size="large"
                autocomplete="off"
              />
            </el-form-item>
            <el-form-item prop="password">
              <el-input
                v-model="loginForm.password"
                type="password"
                show-password
                placeholder="密码"
                size="large"
                autocomplete="new-password"
                @keyup.enter="handleLogin"
              />
            </el-form-item>

            <div class="form-extra">
              <el-checkbox
                v-model="rememberMe"
                class="remember-check"
              >
                {{ t('rememberPwd') }}
              </el-checkbox>
              <span
                class="forgot-link"
                @click="goForgot"
              >{{ t('forgotPwd') }}</span>
            </div>

            <el-form-item>
              <button
                class="login-btn"
                :disabled="loading"
                @click.prevent="handleLogin"
              >
                {{ loading ? '登录中...' : '登 录' }}
              </button>
            </el-form-item>
          </el-form>
        </div>
      </div>
    </div>
  </div>
</template>
<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { usePermissionStore } from '@/stores/permission'
import { homePath } from '@/utils/homePath'
import { ElMessage } from 'element-plus'
import { useTheme } from '@/composables/useTheme'
import { useI18n } from '@/composables/useI18n'
import LoginBrand from '@/components/LoginBrand.vue'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const permissionStore = usePermissionStore()
const { isDark, toggle: toggleTheme } = useTheme()
const { t, current: lang, setLang } = useI18n()

const loginFormRef = ref(null)
const loading = ref(false)
const rememberMe = ref(false)

const saved = JSON.parse(localStorage.getItem('gsc-remembered') || '{}')
const loginForm = reactive({
  tenantCode: saved.tenantCode || '',
  username: saved.username || '',
  password: saved.password || '',
})
if (saved.username) rememberMe.value = true

function goForgot() {
  router.push('/forgot-password')
}

const loginRules = {
  tenantCode: [{ required: true, message: '请输入租户编码', trigger: 'blur' }],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码不能少于6位', trigger: 'blur' },
  ],
}

async function handleLogin() {
  if (loading.value) return  // 防重入：阻止 Enter + 点击 同时触发
  try { await loginFormRef.value?.validate() } catch { return }
  loading.value = true
  try {
    // 调用后端登录接口，获取 accessToken + refreshToken + 用户信息 + 菜单
    const res = await authStore.login({
      tenantCode: loginForm.tenantCode.trim(),
      username: loginForm.username.trim(),
      password: loginForm.password,
    })
    // 从登录响应中提取角色和权限
    if (res.userInfo) permissionStore.setFromUserInfo(res.userInfo.roles, res.userInfo.permissions)
    // 优先使用登录响应中的菜单，没有则从后端重新加载
    if (res.menus?.length > 0) permissionStore.menus = res.menus
    else await permissionStore.loadMenus()
    if (rememberMe.value) {
      localStorage.setItem('gsc-remembered', JSON.stringify({
        tenantCode: loginForm.tenantCode,
        username: loginForm.username,
        password: loginForm.password,
      }))
    } else {
      localStorage.removeItem('gsc-remembered')
    }

    ElMessage.success('登录成功')
    // 无 redirect 参数时按角色回首页（事务所/预审用户不是 /dashboard）
    await router.replace(route.query.redirect || homePath(permissionStore.roles))
  } catch {
    // 错误消息由 request.js 拦截器统一处理
  } finally {
    loading.value = false
  }
}

// 被强制踢出时显示原因（如"账号已被禁用"、"租户已停用"）
onMounted(() => {
  const reason = route.query.reason
  if (reason) {
    ElMessage.error(decodeURIComponent(reason))
    router.replace({ path: '/login', query: {} })  // 清除 URL 中的 reason 参数，防止重复提示
  }
})
</script>

<style>
/* ========================================
 * 暗色主题 CSS 变量
 * ======================================== */
.login-page {
  --lp-bg-left: linear-gradient(160deg, #0a1628 0%, #0f1f3a 30%, #132b4f 60%, #0d1f38 100%);
  --lp-bg-right: #ffffff;
  --lp-card-bg: #ffffff;
  --lp-title-color: #ffffff;
  --lp-subtitle-color: rgba(255, 255, 255, 0.7);
  --lp-text-primary: #1d1d2e;
  --lp-text-secondary: #8b8fa8;
  --lp-input-bg: #f5f6fa;
  --lp-input-border: #e4e7f0;
  --lp-input-text: #1d1d2e;
  --lp-input-placeholder: #b8bcc8;
  --lp-btn-bg: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  --lp-demo-bg: #f5f6fa;
  --lp-header-color: #ffffff;
}

.login-page.dark {
  --lp-bg-left: linear-gradient(160deg, #0f1a2e 0%, #0d1525 50%, #0a101c 100%);
  --lp-bg-right: #1a1d2e;
  --lp-card-bg: #252836;
  --lp-text-primary: #e8e9f0;
  --lp-text-secondary: #8b8fa8;
  --lp-input-bg: #2a2d3e;
  --lp-input-border: #3a3d52;
  --lp-input-text: #e8e9f0;
  --lp-input-placeholder: #5c6080;
  --lp-btn-bg: linear-gradient(135deg, #5d87ff 0%, #7c5cfc 100%);
  --lp-demo-bg: #2a2d3e;
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
}

/* ========================================
 * 左上角系统名
 * ======================================== */
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

/* ========================================
 * 右上角工具栏
 * ======================================== */
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

/* ========================================
 * 主体：左右分栏
 * ======================================== */
.login-body {
  display: flex;
  height: 100%;
}

/* ---- 左侧 ---- */
.left-panel {
  flex: 0 0 50vw;
  background: var(--lp-bg-left);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  transition: background 0.35s ease;
}

/* 右侧边缘平滑过渡 */
.left-panel::after {
  content: '';
  position: absolute;
  right: -60px;
  top: 0;
  bottom: 0;
  width: 120px;
  background: linear-gradient(to right, transparent 0%, rgba(0, 0, 0, 0.06) 50%, rgba(0, 0, 0, 0.08) 100%);
  z-index: 2;
  pointer-events: none;
}

/* 暗色模式装饰光斑 */
.left-panel::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  opacity: 0;
  transition: opacity 0.35s ease;
  background:
    radial-gradient(ellipse at 20% 80%, rgba(120, 119, 198, 0.3) 0%, transparent 50%),
    radial-gradient(ellipse at 80% 20%, rgba(120, 219, 255, 0.2) 0%, transparent 50%),
    radial-gradient(ellipse at 50% 50%, rgba(130, 100, 240, 0.15) 0%, transparent 50%);
}

.dark .left-panel::before {
  opacity: 1;
}

.left-illustration {
  position: relative;
  z-index: 1;
  margin-bottom: 20px;
}

.left-illustration svg {
  width: 560px;
  height: auto;
}

.left-text {
  position: absolute;
  bottom: 60px;
  left: 0;
  right: 0;
  z-index: 1;
  text-align: center;
  padding: 0 40px;
}

.left-text h1 {
  font-size: 32px;
  font-weight: 800;
  color: var(--lp-title-color);
  margin-bottom: 12px;
  letter-spacing: 2px;
  line-height: 1.4;
}

.left-text p {
  font-size: 16px;
  color: var(--lp-subtitle-color);
}

/* ---- 右侧 ---- */
.right-panel {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
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

/* ---- 输入框 ---- */
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

/* ---- 记住密码 + 忘记密码 ---- */
.form-extra {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  margin-bottom: 8px;
}

.remember-check {
  --el-checkbox-text-color: var(--lp-text-secondary);
  --el-checkbox-font-size: 14px;
}

.forgot-link {
  font-size: 14px;
  color: #5d87ff;
  cursor: pointer;
  text-decoration: none;
  transition: color 0.2s;
}

.forgot-link:hover {
  color: #3b6de6;
  text-decoration: underline;
}

/* ---- 按钮 ---- */
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
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.35);
  transform: translateY(-1px);
}

.login-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

/* ========================================
 * 响应式
 * ======================================== */
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
