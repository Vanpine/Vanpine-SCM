/**
 * SRM Admin - 路由实例
 * 包含导航守卫：Token 校验 → 用户信息恢复 → 菜单加载 → 权限判断
 */
import { createRouter, createWebHistory } from 'vue-router'
import { constantRoutes, asyncRoutes, catchAllRoute } from './routes'
import { useAuthStore } from '@/stores/auth'
import { usePermissionStore } from '@/stores/permission'
import { getToken } from '@/utils/auth'
import { homePath } from '@/utils/homePath'
import { ElMessage } from 'element-plus'

const router = createRouter({
  history: createWebHistory(),
  routes: [...constantRoutes, ...asyncRoutes, catchAllRoute],
  scrollBehavior: () => ({ top: 0 }),
})

// 白名单路径（不需要登录）
const whiteList = ['/login', '/forgot-password', '/404', '/403']

/**
 * 全局前置守卫
 * 1. 无 Token → 跳转登录页
 * 2. 有 Token 但无用户信息 → 从服务端恢复
 * 3. 无菜单 → 加载菜单
 * 4. 每次路由切换都同步最新权限（权限变更实时生效，无需刷新浏览器）
 * 5. 检查页面权限
 */
router.beforeEach(async (to, _from, next) => {
  const authStore = useAuthStore()
  const permissionStore = usePermissionStore()

  // ---- 有 Token ----
  if (getToken()) {
    if (to.path === '/login') {
      next(homePath(permissionStore.roles))  // 按角色回首页（事务所/预审用户非 /dashboard）
      return
    }

    // 恢复用户信息（仅刷新页面后首次）
    if (!authStore.user) {
      try {
        const userInfo = await authStore.fetchUserInfo()
        permissionStore.setFromUserInfo(userInfo.roles, userInfo.permissions)
      } catch {
        next('/login')
        return
      }
    }

    // 确保企业画像类型已加载：登录后 SPA 内导航不会经过上面的 fetchUserInfo 分支，
    // profileType 会一直为 null，导致页面按错误身份渲染（如供应商看到采购方视角 → 列表空白）。
    if (authStore.profileType == null) {
      try {
        await authStore.loadProfileType()
      } catch {
        // 加载失败不阻塞导航，页面内部有兜底默认值
      }
    }

    // 加载菜单（仅首次）
    if (!permissionStore.isMenusLoaded) {
      try {
        await permissionStore.loadMenus()
      } catch {
        next('/login')
        return
      }
    }

    // 每次路由切换同步最新权限（带版本号，权限未变时只返回 changed=false，几乎无开销）
    // 不 await：立即放行导航，页面表格内部用自己的 v-loading 展示加载；
    // 权限/角色变更最多延迟到下一次切换菜单生效（本轮判断用上一轮已同步的 roles 兜底，登录时已初始化过）
    permissionStore.syncLatestPermissions()

    // DUE_DILIGENCE 用户只能访问尽职调查相关页面（超管不受限制；String() 兼容 Long 序列化为字符串）
    if (String(authStore.tenantId) !== '1' && permissionStore.roles.includes('DUE_DILIGENCE') && !to.path.startsWith('/due-diligence')) {
      next('/due-diligence')
      return
    }

    // FIRM 事务所用户只能访问事务所工作台（超管不受限制）
    if (String(authStore.tenantId) !== '1' && permissionStore.roles.includes('FIRM') && !to.path.startsWith('/due-diligence/firm')) {
      next('/due-diligence/firm')
      return
    }

    // 权限检查（处理 *:*:* 和 admin 通配符）
    if (to.meta?.permissions && to.meta.permissions.length > 0) {
      const userPerms = permissionStore.permissions
      // 超级管理员通配符直接放行
      if (userPerms.includes('*:*:*') || userPerms.includes('admin')) {
        next()
        return
      }
      const hasPerm = to.meta.permissions.some((requiredPerm) =>
        userPerms.some((userPerm) =>
          userPerm === requiredPerm || userPerm.startsWith(requiredPerm + ':'),
        ),
      )
      if (!hasPerm) {
        ElMessage.warning('无权限访问该页面')
        next('/403')
        return
      }
    }

    // 根据菜单 isCache 配置设置路由缓存标记
    to.meta.keepAlive = permissionStore.cachedPaths.has(to.path)

    next()
    return
  }

  // ---- 无 Token ----
  if (whiteList.includes(to.path)) {
    next()
  } else {
    next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
  }
})

export default router
