<template>
  <div class="app-header">
    <!-- 左侧 -->
    <div class="header-left">
      <button class="collapse-btn" :title="isCollapse ? '展开菜单' : '折叠菜单'" @click="handleToggleSidebar">
        <el-icon :size="18">
          <Fold v-if="!isCollapse" />
          <Expand v-else />
        </el-icon>
      </button>

      <el-breadcrumb separator="/">
        <el-breadcrumb-item v-for="item in crumbs" :key="item.path" :to="item.to">
          <span :class="item.cls">{{ item.title }}</span>
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <!-- 右侧：通知 + 用户信息 + 操作 -->
    <div class="header-right">
      <!-- 语言切换 -->
      <LangSwitch />

      <!-- 站内消息铃铛 -->
      <el-popover v-model:visible="notifyVisible" placement="bottom-end" :width="380" trigger="click"
        popper-class="notify-popover">
        <template #reference>
          <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99" class="notify-badge">
            <button class="notify-btn" title="消息通知">
              <el-icon :size="18">
                <Bell />
              </el-icon>
            </button>
          </el-badge>
        </template>
        <div class="notify-panel">
          <div class="notify-panel-header">
            <span class="notify-panel-title">消息通知</span>
            <el-button v-if="unreadCount > 0" link type="primary" size="small"
              @click="handleMarkAllRead">全部已读</el-button>
          </div>
          <div class="notify-panel-body">
            <!-- 数据未就绪：骨架屏占位，避免旧数据/空状态闪现 -->
            <div v-if="notifyLoading" class="notify-loading">
              <el-skeleton :rows="4" animated />
            </div>
            <template v-else-if="notifyList.length > 0">
              <div v-for="item in notifyList" :key="item.id" class="notify-item"
                :class="{ 'is-unread': item.isRead === 0 }" @click="handleOpenNotification(item)">
                <div class="notify-item-main">
                  <span class="notify-icon"
                    :style="{ color: typeIcon(item.relatedType).color, background: typeIcon(item.relatedType).bg }">
                    <el-icon :size="16">
                      <component :is="typeIcon(item.relatedType).icon" />
                    </el-icon>
                  </span>
                  <div class="notify-item-body">
                    <div class="notify-item-title">
                      <span v-if="item.isRead === 0" class="notify-dot"></span>
                      <span class="notify-title-text">{{ item.title }}</span>
                      <span class="notify-time">{{ relativeTime(item.createTime) }}</span>
                    </div>
                    <div class="notify-item-content">{{ item.content }}</div>
                  </div>
                </div>
              </div>
            </template>
            <el-empty v-else description="暂无通知" :image-size="60" />
          </div>
        </div>
      </el-popover>

      <div class="user-info-tag">
        <span class="role-badge">{{ authStore.user?.roleNames?.[0] || '' }}</span>
        <span class="user-name">{{ authStore.user?.realName || authStore.username }}</span>
      </div>

      <el-dropdown trigger="click" @command="handleCommand">
        <div class="avatar-trigger">
          <el-avatar :size="32" :src="authStore.user?.avatarUrl" icon="UserFilled" />
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-if="!isRestrictedUser" command="profile">
              <el-icon>
                <User />
              </el-icon> 个人信息
            </el-dropdown-item>
            <el-dropdown-item v-if="!isRestrictedUser" command="changePassword">
              <el-icon>
                <Key />
              </el-icon> 修改密码
            </el-dropdown-item>
            <el-dropdown-item command="logout" divided>
              <span style="color: #f56c6c">退出登录</span>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>
<script setup>
import { computed, ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { usePermissionStore } from '@/stores/permission'
import { homePath } from '@/utils/homePath'
import { useAppStore } from '@/stores/app'
import { ElMessage } from 'element-plus'
import { Fold, Expand, Key, User } from '@element-plus/icons-vue'
import LangSwitch from '@/components/LangSwitch.vue'
import { getNotificationList, getNotificationUnreadCount, markNotificationRead, markAllNotificationsRead } from '@/api/notification'
import { formatTime } from '@/utils/format'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()
const authStore = useAuthStore()
const permissionStore = usePermissionStore()
const appStore = useAppStore()

const isCollapse = computed(() => appStore.sidebarCollapsed)

/** 面包屑数组：[{ title, path, to, cls }] */
const crumbs = computed(() => {
  const home = homePath(permissionStore.roles)
  const homeCrumb = { title: t('common.home'), path: home, to: { path: home }, cls: 'breadcrumb-home' }
  // 站在首页本身（工作台 / 事务所工作台 / 尽调页）：只显示「首页」，不再重复当前页标题
  if (route.path === home) return [homeCrumb]
  // 单一页面角色（事务所/预审用户）没有「首页」这一层，只显示当前页标题
  const items = home === '/dashboard' ? [homeCrumb] : []
  if (!route.meta?.title) return items

  // 中间层级（由路由 meta.breadcrumb 定义）
  const mids = route.meta?.breadcrumb
  if (mids && mids.length) {
    mids.forEach((m) => items.push({ title: routeTitle(m.path, null, m.title), path: m.path, to: { path: m.path }, cls: 'breadcrumb-link' }))
  }

  // 当前页（不可点击）
  items.push({ title: routeTitle(route.path, route.name, route.meta.title), path: route.path, to: undefined, cls: 'breadcrumb-current' })
  return items
})

/** 按路由路径/名称查语言包，查不到 fallback 原始标题 */
function routeTitle(path, name, fallback) {
  if (path) {
    const key = `menu.${path}`
    const translated = t(key)
    if (translated !== key) return translated
  }
  if (name) {
    const key = `menu.${name}`
    const translated = t(key)
    if (translated !== key) return translated
  }
  return fallback
}

/** 低权限用户（预审/事务所）不显示个人信息和修改密码 */
const isRestrictedUser = computed(() =>
  permissionStore.roles.includes('DUE_DILIGENCE') || permissionStore.roles.includes('FIRM'),
)

function handleToggleSidebar() {
  appStore.toggleSidebar()
}

function handleCommand(command) {
  if (command === 'profile') router.push('/profile')
  else if (command === 'changePassword') router.push('/change-password')
  else if (command === 'logout') handleLogout()
}

async function handleLogout() {
  stopPolling()
  await authStore.logout()
  permissionStore.clearPermissions()
  unreadCount.value = 0
  notifyList.value = []
  router.push('/login')
}

// ==================== 站内消息 ====================
const notifyVisible = ref(false)
const unreadCount = ref(0)
const notifyList = ref([])
const notifyLoading = ref(false)
let notifyTimer = null

/** popover 打开前先设 loading，避免空状态闪现 */
watch(notifyVisible, (val) => {
  if (val) {
    notifyLoading.value = true
    handleOpenNotifications()
  }
})

/** 拉取未读数 */
async function fetchUnreadCount() {
  try {
    // 后台轮询失败不打扰用户：发版重启、网络抖动时保留上一次的数字即可
    const res = await getNotificationUnreadCount({ silentError: true })
    unreadCount.value = res?.data || 0
  } catch { /* 静默失败，保留当前未读数 */ }
}

/** 打开通知面板：刷新未读数 + 列表（由 watch(notifyVisible) 触发） */
async function handleOpenNotifications() {
  await Promise.all([fetchUnreadCount(), fetchNotifyList()])
}

/** 拉取通知列表（未读优先，最近 20 条） */
async function fetchNotifyList() {
  notifyLoading.value = true
  try {
    const res = await getNotificationList({ pageNum: 1, pageSize: 20 })
    const records = res?.data?.records || res?.records || []
    notifyList.value = records
  } catch { /* 拦截器已处理 */ }
  finally { notifyLoading.value = false }
}

/** 点击单条：标记已读 + 跳转对应业务页 */
async function handleOpenNotification(item) {
  notifyVisible.value = false
  if (item.isRead === 0) {
    try { await markNotificationRead(item.id) } catch { /* 拦截器已处理 */ }
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  }
  jumpByType(item)
}

/** 按关联业务类型跳转 */
function jumpByType(item) {
  if (!item?.relatedType) return
  if (item.relatedType === 'connection') router.push({ name: 'Connections' })
  else if (item.relatedType === 'contract') router.push({ name: 'ContractManagement', query: { contractId: item.relatedId } })
  else if (item.relatedType === 'quote') router.push({ name: 'PriceComparison' })
  else if (item.relatedType === 'inquiry') router.push({ name: 'InquiryManagement', query: { inquiryId: item.relatedId } })
  else if (item.relatedType === 'delivery') {
    const name = authStore.profileType === 'supplier' ? 'DeliveryShipment' : 'DeliveryReceipt'
    router.push({ name, query: { deliveryId: item.relatedId } })
  }
  // 入驻/尽调通知：超管跳「入驻预审」，企业侧无对应管理页则留在原地
  else if (item.relatedType === 'application') {
    if (String(authStore.tenantId) === '1') {
      router.push({ name: 'ApplicationReview', query: { applicationId: item.relatedId } })
    }
  }
}

/** 通知类型 → 图标 + 颜色（按 relatedType 区分，图标全局注册，用字符串名） */
const NOTIFY_ICONS = {
  connection: { icon: 'Link', color: '#409eff', bg: 'rgba(64,158,255,0.12)' },
  quote: { icon: 'Document', color: '#67c23a', bg: 'rgba(103,194,58,0.12)' },
  contract: { icon: 'Files', color: '#e6a23c', bg: 'rgba(230,162,60,0.12)' },
  inquiry: { icon: 'Tickets', color: '#9254de', bg: 'rgba(146,84,222,0.12)' },
  delivery: { icon: 'Van', color: '#13c2c2', bg: 'rgba(19,194,194,0.12)' },
  application: { icon: 'Postcard', color: '#f56c6c', bg: 'rgba(245,108,108,0.12)' },
}
function typeIcon(type) {
  return NOTIFY_ICONS[type] || { icon: 'Bell', color: '#909399', bg: 'rgba(144,147,153,0.12)' }
}

/** 相对时间：刚刚/X分钟前/X小时前/昨天/X天前，超过7天回退完整日期 */
function relativeTime(time) {
  if (!time) return ''
  const t = new Date(time.replace(/-/g, '/')).getTime()
  const now = Date.now()
  const diff = now - t
  const MIN = 60 * 1000
  const HOUR = 60 * MIN
  const DAY = 24 * HOUR
  if (diff < MIN) return '刚刚'
  if (diff < HOUR) return Math.floor(diff / MIN) + '分钟前'
  const startToday = new Date(now)
  startToday.setHours(0, 0, 0, 0)
  const startOfTime = new Date(t)
  startOfTime.setHours(0, 0, 0, 0)
  const daysDiff = Math.round((startToday.getTime() - startOfTime.getTime()) / DAY)
  if (daysDiff === 0) return Math.floor(diff / HOUR) + '小时前'
  if (daysDiff === 1) return '昨天'
  if (daysDiff < 7) return daysDiff + '天前'
  return formatTime(time)
}

/** 全部标记已读 */
async function handleMarkAllRead() {
  try {
    await markAllNotificationsRead()
    notifyList.value.forEach(item => { item.isRead = 1 })
    unreadCount.value = 0
    ElMessage.success('已全部标记为已读')
  } catch { /* 拦截器已处理 */ }
}

// 轮询间隔：页面可见 60s；隐藏时完全暂停，切回页面再立即刷新
const POLL_VISIBLE = 60 * 1000

function startPolling() {
  stopPolling()
  if (document.hidden) return
  notifyTimer = setInterval(fetchUnreadCount, POLL_VISIBLE)
}

function stopPolling() {
  if (notifyTimer) { clearInterval(notifyTimer); notifyTimer = null }
}

function handleVisibilityChange() {
  if (document.hidden) {
    stopPolling()
    return
  }
  fetchUnreadCount()
  startPolling()
}

onMounted(() => {
  fetchUnreadCount()
  startPolling()
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onBeforeUnmount(() => {
  stopPolling()
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})

</script>

<style scoped>
.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 100%;
  padding: 0 20px;
  background: #ffffff;
  border-bottom: 1px solid #edf0f4;
}

/* ---- 左侧 ---- */
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.collapse-btn {
  width: 34px;
  height: 34px;
  border: none;
  background: transparent;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #64748b;
  transition: all 0.2s;
}

.collapse-btn:hover {
  background: #f1f5f9;
  color: #1a1a2e;
}

/* 面包屑 */
.breadcrumb-home {
  font-size: 13px;
  color: #94a3b8;
  transition: color 0.2s;
  cursor: pointer;
}

.breadcrumb-home:hover {
  color: #0066ff;
}

.breadcrumb-link {
  font-size: 13px;
  color: #94a3b8;
  transition: color 0.2s;
  cursor: pointer;
}

.breadcrumb-link:hover {
  color: #0066ff;
}

.breadcrumb-current {
  font-size: 13px;
  color: #0066ff;
  font-weight: 600;
}

/* ---- 右侧 ---- */
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-info-tag {
  display: flex;
  align-items: center;
  gap: 0;
  padding: 0;
}

.role-badge {
  font-size: 11px;
  font-weight: 500;
  color: #0066ff;
  background: rgba(0, 102, 255, 0.07);
  padding: 3px 10px;
  border-radius: 6px 0 0 6px;
  line-height: 1.5;
}

.user-name {
  font-size: 13px;
  color: #334155;
  font-weight: 500;
  padding: 3px 10px;
  background: #f8fafc;
  border-radius: 0 6px 6px 0;
  line-height: 1.5;
}

.avatar-trigger {
  cursor: pointer;
  border-radius: 50%;
  padding: 2px;
  transition: box-shadow 0.2s;
  flex-shrink: 0;
}

.avatar-trigger:hover {
  box-shadow: 0 0 0 3px rgba(0, 102, 255, 0.12);
}

/* ---- 下拉菜单头部 ---- */
.dropdown-header {
  padding: 10px 16px 6px;
  min-width: 140px;
}

.dropdown-name {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a2e;
  margin-bottom: 2px;
}

.dropdown-tenant {
  font-size: 12px;
  color: #94a3b8;
}
</style>

<style>
/* 站内消息铃铛 + 通知面板（el-popover 挂载在 body，需非 scoped） */
.notify-badge {
  margin-right: 2px;
}

.notify-btn {
  width: 34px;
  height: 34px;
  border: none;
  background: transparent;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #64748b;
  transition: all 0.2s;
}

.notify-btn:hover {
  background: #f1f5f9;
  color: #0066ff;
}

.notify-panel {
  padding: 0;
}

.notify-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #ebeef5;
}

.notify-panel-title {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a2e;
}

.notify-panel-body {
  max-height: 360px;
  overflow-y: auto;
  min-height: 100px;
}

.notify-loading {
  padding: 16px;
}

.notify-item {
  padding: 10px 16px;
  cursor: pointer;
  border-bottom: 1px solid #f5f7fa;
  transition: background 0.2s;
}

.notify-item:hover {
  background: #f5f9ff;
}

.notify-item.is-unread {
  background: #f0f7ff;
}

.notify-item-main {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.notify-icon {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 1px;
}

.notify-item-body {
  flex: 1;
  min-width: 0;
}

.notify-item-title {
  display: flex;
  align-items: center;
  gap: 6px;
}

.notify-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #f56c6c;
  flex-shrink: 0;
}

.notify-title-text {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notify-time {
  font-size: 12px;
  color: #a8abb2;
  flex-shrink: 0;
}

.notify-item-content {
  font-size: 13px;
  color: #606266;
  margin-top: 4px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
