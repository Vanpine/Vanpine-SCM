/**
 * SRM Admin - 权限与菜单状态管理
 * 管理用户菜单列表、角色列表、权限列表
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getUserMenus, syncPermissions } from '@/api/auth'

export const usePermissionStore = defineStore('permission', () => {
  // ---- state ----
  const menus = ref([])
  const roles = ref([])
  const permissions = ref([])
  const permissionsVersion = ref(0)
  const isSyncingPermissions = ref(false)

  // ---- getters ----
  const isMenusLoaded = computed(() => menus.value.length > 0)

  /** 递归收集标记了 isCache 的菜单路径 */
  function collectCachedPaths(tree) {
    const paths = []
    for (const node of tree) {
      if (node.menuPath && node.isCache) paths.push(node.menuPath)
      if (node.children?.length) paths.push(...collectCachedPaths(node.children))
    }
    return paths
  }

  /** 所有开启了缓存的菜单路径集合 */
  const cachedPaths = computed(() => new Set(collectCachedPaths(menus.value)))

  /**
   * 将扁平菜单列表转为树形结构（侧边栏渲染用）
   * @param {Array} flatMenus - 扁平菜单列表
   * @returns {Array} 树形菜单
   */
  function buildMenuTree(flatMenus) {
    const map = {}
    const tree = []

    flatMenus.forEach((menu) => {
      map[menu.id] = { ...menu, children: [] }
    })

    flatMenus.forEach((menu) => {
      if (menu.parentId && map[menu.parentId]) {
        map[menu.parentId].children.push(map[menu.id])
      } else {
        tree.push(map[menu.id])
      }
    })

    return tree
  }

  /**
   * 将菜单列表转为 el-tree 权限分配用的树形结构
   * 每个节点格式 { id: 'menu_xxx', label, children }
   * @param {Array} menus - 后端返回的菜单树
   * @returns {Array} el-tree 适用的树
   */
  function buildPermTree(menus) {
    function walk(node) {
      return {
        id: 'menu_' + node.id,
        label: node.menuName,
        children: (node.children || []).map(walk),
      }
    }
    return menus.map(walk)
  }

  // ---- actions ----

  /**
   * 加载用户菜单列表
   */
  /**
   * 加载用户菜单列表
   * 调用后端 /api/auth/menus，超管返回全部菜单，企业管理员返回自己有权限的
   */
  async function loadMenus() {
    try {
      // 调用后端获取当前用户可见菜单树接口
      const { data } = await getUserMenus()
      menus.value = data || []
      return menus.value
    } catch {
      menus.value = []
      throw new Error('获取菜单失败')
    }
  }

  /**
   * 从用户信息设置角色和权限
   * @param {string[]} roleList  - 角色列表
   * @param {string[]} permList  - 权限列表
   */
  function setFromUserInfo(roleList, permList) {
    roles.value = roleList || []
    permissions.value = permList || []
  }

  /**
   * 同步最新权限（带版本号，权限未变时只返回 version，几乎无开销）
   */
  /**
   * 同步最新权限（带版本号，权限未变时只返回 changed=false，几乎无开销）
   * 调用后端 /api/auth/permissions?version=N
   */
  async function syncLatestPermissions() {
    if (isSyncingPermissions.value) return
    isSyncingPermissions.value = true
    try {
      // 调用后端权限同步接口，传入客户端当前版本号
      const { data } = await syncPermissions(permissionsVersion.value)
      if (data.permissions) {
        // 权限有变更时才更新
        permissions.value = data.permissions
      }
      if (data.roles) {
        // 角色有变更时才更新（路由守卫依赖此值做 DUE_DILIGENCE/FIRM 强制跳转判断）
        roles.value = data.roles
      }
      // 更新本地版本号
      permissionsVersion.value = data.version || 0
    } catch {
      // 静默失败，使用现有权限和角色
    } finally {
      isSyncingPermissions.value = false
    }
  }

  /**
   * 清除所有权限数据（登出时调用）
   */
  function clearPermissions() {
    menus.value = []
    roles.value = []
    permissions.value = []
    permissionsVersion.value = 0
  }

  return {
    // state
    menus,
    roles,
    permissions,
    permissionsVersion,
    isSyncingPermissions,
    // getters
    isMenusLoaded,
    cachedPaths,
    // actions
    buildMenuTree,
    buildPermTree,
    loadMenus,
    setFromUserInfo,
    syncLatestPermissions,
    clearPermissions,
  }
})
