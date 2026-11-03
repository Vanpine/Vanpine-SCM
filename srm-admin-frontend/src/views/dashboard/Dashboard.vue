<template>
  <div class="dashboard">
    <div v-if="loading" class="flex justify-center py-20">
      <div class="flex flex-col items-center gap-3">
        <div class="w-8 h-8 border-4 border-blue-200 border-t-blue-600 rounded-full animate-spin" />
        <span class="text-sm text-gray-400">加载中...</span>
      </div>
    </div>

    <template v-else>
      <!-- 页头：深色欢迎条 -->
      <el-card class="welcome-card" shadow="never">
        <div class="welcome-inner">
          <div class="welcome-text">
            <h1>欢迎回来，{{ authStore.user?.realName || authStore.username }}</h1>
            <p>
              <span>{{ authStore.tenantCode }}</span>
              <el-tag v-for="r in (authStore.user?.roleNames || [])" :key="r" size="small" :type="roleTagType(r)">
                {{ r }}
              </el-tag>
              <el-tag v-if="currentRole === 'BOTH'" size="small" type="warning">
                采购商+供应商
              </el-tag>
            </p>
          </div>
          <div class="welcome-time">
            <span class="time-label">最近登录</span>
            <span class="time-value">{{ today }}</span>
          </div>
        </div>
      </el-card>

      <!-- 核心指标：双身份按采购/供应分组，可点进对应列表 -->
      <div v-for="group in statGroups" :key="group.title || 'default'" class="stat-group">
        <div v-if="group.title" class="group-title">{{ group.title }}</div>
        <el-row :gutter="16" class="stats-row">
          <el-col v-for="(card, idx) in group.items" :key="`${card.label}-${idx}`" :xs="12" :sm="12" :md="6">
            <el-card
              shadow="never"
              class="stat-card"
              :class="{ 'is-zero': !card.value, 'is-clickable': !!statLink(card) }"
              @click="goLink(statLink(card))"
            >
              <p class="stat-label">{{ card.label }}</p>
              <p class="stat-number">
                {{ card.value }}<small v-if="card.unit">{{ card.unit }}</small>
              </p>
            </el-card>
          </el-col>
        </el-row>
      </div>

      <!-- 待办：有数的排前面，查看全部走第一条待处理 -->
      <el-card v-if="sortedTodos.length" shadow="never" class="section-card">
        <template #header>
          <div class="section-head">
            <span class="section-title">待办事项</span>
            <el-button v-if="todoHomeLink" link type="primary" @click="goLink(todoHomeLink)">查看全部</el-button>
          </div>
        </template>
        <div class="todo-grid">
          <div
            v-for="t in sortedTodos"
            :key="t.label"
            class="todo-item"
            :class="{ 'is-clickable': !!t.link, 'is-zero': Number(t.count) === 0 }"
            @click="goLink(t.link)"
          >
            <span class="todo-label">{{ t.label }}</span>
            <span class="todo-count" :class="todoCountClass(t)">{{ t.count }}</span>
          </div>
        </div>
      </el-card>

      <el-row :gutter="16" class="bottom-row">
        <el-col :xs="24" :md="8">
          <el-card shadow="never" class="section-card">
            <template #header><span class="section-title">快捷入口</span></template>
            <div class="quick-grid">
              <button
                v-for="link in quickActions"
                :key="link.path"
                type="button"
                class="quick-item"
                @click="goLink(link.path)"
              >
                <el-icon class="quick-icon"><component :is="link.icon" /></el-icon>
                <span>{{ link.label }}</span>
              </button>
            </div>
          </el-card>
        </el-col>
        <el-col :xs="24" :md="16">
          <el-card shadow="never" class="section-card">
            <template #header><span class="section-title">近期动态</span></template>
            <div v-if="data.recentActivities?.length" class="activity-list">
              <div
                v-for="(act, i) in data.recentActivities"
                :key="i"
                class="activity-item"
                :class="{ 'is-clickable': !!act.link }"
                @click="goLink(act.link)"
              >
                <span class="activity-desc">{{ act.desc }}</span>
                <span class="activity-time">{{ act.time }}</span>
              </div>
            </div>
            <div v-else class="empty-hint">
              <span>暂无动态</span>
              <el-button
                v-if="quickActions[0]"
                link
                type="primary"
                @click="goLink(quickActions[0].path)"
              >
                从{{ quickActions[0].label }}开始
              </el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { usePermissionStore } from '@/stores/permission'
import { fetchDashboardStats } from '@/api/dashboard'
import { ROLE_TAG_TYPE_MAP } from '@/constants'
import {
  Document,
  Histogram,
  List,
  Notebook,
  OfficeBuilding,
  Search,
  Setting,
  Stamp,
  User,
} from '@element-plus/icons-vue'

const router = useRouter()
const authStore = useAuthStore()
const permissionStore = usePermissionStore()

const today = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' })

const loading = ref(true)
const dashboardData = ref({ stats: [], todos: [], recentActivities: [] })

/** 后端未带 link 时按指标名兜底，保证卡片可跳转 */
const STAT_LINK_FALLBACK = {
  进行中询盘: '/procurement/inquiry',
  待比价询盘: '/procurement/compare',
  进行中合同: '/procurement/contract',
  待验收交付: '/delivery/manage/acceptance',
  待报价询盘: '/procurement/inquiry',
  已中标: '/procurement/inquiry',
  待发货: '/delivery/manage/shipment',
  租户总数: '/admin/tenant',
  今日活跃租户: '/admin/tenant',
  待审核入驻: '/admin/application',
}

/** 快捷入口：图标+文字宫格（路由属前端信息） */
const QUICK_ACTIONS = {
  SUPER_ADMIN: [
    { path: '/admin/application', label: '入驻审核', icon: Stamp },
    { path: '/admin/tenant', label: '租户管理', icon: OfficeBuilding },
    { path: '/admin/audit-log', label: '操作日志', icon: List },
  ],
  TENANT_ADMIN: [
    { path: '/admin/user', label: '用户管理', icon: User },
    { path: '/admin/role', label: '角色管理', icon: Setting },
  ],
  BUYER: [
    { path: '/procurement/inquiry', label: '创建询盘', icon: Document },
    { path: '/procurement/compare', label: '比价管理', icon: Histogram },
    { path: '/procurement/contract', label: '合同管理', icon: Notebook },
    { path: '/matching/requirement', label: '找供应商', icon: Search },
  ],
  SUPPLIER: [
    { path: '/procurement/inquiry', label: '报价管理', icon: Document },
    { path: '/procurement/contract', label: '合同管理', icon: Notebook },
    { path: '/matching/requirement', label: '找需求', icon: Search },
  ],
  BOTH: [
    { path: '/procurement/inquiry', label: '询盘管理', icon: Document },
    { path: '/procurement/compare', label: '比价管理', icon: Histogram },
    { path: '/procurement/contract', label: '合同管理', icon: Notebook },
    { path: '/matching/requirement', label: '找供应商', icon: Search },
  ],
}

function roleTagType(roleName) {
  return ROLE_TAG_TYPE_MAP[roleName] || 'info'
}

const currentRole = computed(() => {
  const roles = permissionStore.roles
  if (roles.includes('SUPER_ADMIN')) return 'SUPER_ADMIN'
  const isBuyer = roles.includes('BUYER') || roles.includes('BUYER_ADMIN')
  const isSupplier = roles.includes('SUPPLIER') || roles.includes('SUPPLIER_ADMIN')
  if (roles.includes('TENANT_ADMIN') || (isBuyer && isSupplier)) return 'BOTH'
  if (isBuyer) return 'BUYER'
  if (isSupplier) return 'SUPPLIER'
  return 'DEFAULT'
})

const data = computed(() => dashboardData.value)

const quickActions = computed(() => QUICK_ACTIONS[currentRole.value] || [])

/** 双身份拆成采购/供应两组；后端无 group 时按 8 条指标兜底切分 */
const statGroups = computed(() => {
  const stats = data.value.stats || []
  if (!stats.length) return []
  if (stats.some((s) => s.group)) {
    const order = []
    const map = new Map()
    stats.forEach((s) => {
      const key = s.group || ''
      if (!map.has(key)) {
        const g = { title: key, items: [] }
        map.set(key, g)
        order.push(g)
      }
      map.get(key).items.push(s)
    })
    if (order.length === 1) order[0].title = ''
    return order
  }
  if (currentRole.value === 'BOTH' && stats.length === 8) {
    return [
      { title: '采购', items: stats.slice(0, 4) },
      { title: '供应', items: stats.slice(4) },
    ]
  }
  return [{ title: '', items: stats }]
})

/** 有数优先、高优先级优先，0 沉底但不隐藏 */
const sortedTodos = computed(() => {
  const list = [...(data.value.todos || [])]
  return list.sort((a, b) => {
    const az = Number(a.count) === 0 ? 1 : 0
    const bz = Number(b.count) === 0 ? 1 : 0
    if (az !== bz) return az - bz
    const ap = a.priority === 'high' ? 0 : 1
    const bp = b.priority === 'high' ? 0 : 1
    return ap - bp
  })
})

const todoHomeLink = computed(() => {
  const hit = sortedTodos.value.find((t) => Number(t.count) > 0 && t.link) || sortedTodos.value.find((t) => t.link)
  return hit?.link || ''
})

function statLink(card) {
  return card?.link || STAT_LINK_FALLBACK[card?.label] || ''
}

function todoCountClass(t) {
  if (Number(t.count) === 0) return 'zero'
  return t.priority === 'high' ? 'high' : 'medium'
}

function goLink(link) {
  if (link) router.push(link)
}

onMounted(async () => {
  try {
    const res = await fetchDashboardStats(currentRole.value)
    dashboardData.value = res?.data || { stats: [], todos: [], recentActivities: [] }
  } catch {
    dashboardData.value = { stats: [], todos: [], recentActivities: [] }
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.dashboard {
  width: 100%;
}

.welcome-card {
  margin-bottom: 16px;
  background: linear-gradient(135deg, #1a2332 0%, #1e3048 100%);
  border-color: transparent !important;
}

.welcome-card :deep(.el-card__body) {
  padding: 24px 28px;
}

.welcome-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
}

.welcome-text h1 {
  color: #fff;
  font-size: 20px;
  font-weight: 700;
  margin-bottom: 12px;
}

.welcome-text p {
  color: rgba(255, 255, 255, 0.55);
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.welcome-time {
  text-align: right;
}

.time-label {
  display: block;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.35);
  margin-bottom: 4px;
}

.time-value {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.7);
}

.stat-group {
  margin-bottom: 8px;
}

.group-title {
  margin: 0 0 8px 2px;
  font-size: 14px;
  font-weight: 500;
  color: rgba(0, 0, 0, 0.65);
}

.stats-row :deep(.el-col) {
  margin-bottom: 16px;
}

.stat-card {
  height: 100%;
  background: #ffffff !important;
  --el-card-bg-color: #ffffff;
  transition: border-color 0.2s;
}

.stat-card :deep(.el-card__body) {
  padding: 16px 20px 20px;
  background: #ffffff;
}

.stat-label {
  margin: 0 0 8px;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.45);
  line-height: 22px;
}

.stat-number {
  margin: 0;
  font-size: 24px;
  font-weight: 500;
  color: rgba(0, 0, 0, 0.88);
  line-height: 32px;
  font-variant-numeric: tabular-nums;
}

.stat-card.is-zero .stat-number {
  color: rgba(0, 0, 0, 0.25);
}

.stat-number small {
  font-size: 14px;
  color: rgba(0, 0, 0, 0.45);
  font-weight: 400;
  margin-left: 4px;
}

.stat-card.is-clickable {
  cursor: pointer;
}

.stat-card.is-clickable:hover {
  border-color: #0066ff !important;
}

.section-card {
  margin-bottom: 16px;
  background: #ffffff !important;
  --el-card-bg-color: #ffffff;
}

.section-card :deep(.el-card__header) {
  padding: 16px 24px;
  border-bottom-color: #f0f0f0 !important;
}

.section-card :deep(.el-card__body) {
  padding: 8px 24px 16px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.section-title {
  font-size: 16px;
  font-weight: 500;
  color: rgba(0, 0, 0, 0.88);
}

.bottom-row :deep(.el-col) {
  margin-bottom: 16px;
}

.todo-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 32px;
}

.todo-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 0;
  border-bottom: 1px solid #f0f0f0;
}

.todo-item.is-clickable {
  cursor: pointer;
}

.todo-item.is-clickable:hover .todo-label {
  color: #0066ff;
}

.todo-item.is-zero .todo-label {
  color: rgba(0, 0, 0, 0.45);
}

.todo-label {
  min-width: 0;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.88);
  line-height: 22px;
}

.todo-count {
  flex-shrink: 0;
  font-size: 16px;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

.todo-count.high {
  color: #ff4d4f;
}

.todo-count.medium {
  color: rgba(0, 0, 0, 0.88);
}

.todo-count.zero {
  color: rgba(0, 0, 0, 0.25);
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  padding: 8px 0 8px;
}

.quick-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  margin: 0;
  padding: 16px 8px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fff;
  color: rgba(0, 0, 0, 0.88);
  font-size: 14px;
  line-height: 22px;
  font-family: inherit;
  cursor: pointer;
  appearance: none;
  transition: border-color 0.2s, color 0.2s;
}

.quick-item:hover {
  border-color: #0066ff;
  color: #0066ff;
}

.quick-icon {
  font-size: 22px;
  color: #0066ff;
}

.activity-list {
  display: flex;
  flex-direction: column;
}

.activity-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 0;
  border-bottom: 1px solid #f0f0f0;
}

.activity-item:last-child {
  border-bottom: none;
}

.activity-item.is-clickable {
  cursor: pointer;
}

.activity-item.is-clickable:hover .activity-desc {
  color: #0066ff;
}

.activity-desc {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.88);
  line-height: 22px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.activity-time {
  flex-shrink: 0;
  padding-top: 1px;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.45);
  white-space: nowrap;
}

.empty-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 24px 0 16px;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.45);
}

@media (max-width: 768px) {
  .todo-grid,
  .quick-grid {
    grid-template-columns: 1fr;
  }
}
</style>
