<template>
  <div class="sidebar">
    <!-- Logo -->
    <router-link
      :to="homePath(permissionStore.roles)"
      class="sidebar-logo"
    >
      <div class="logo-mark">
        <img
          src="@/assets/icons/logo-icon.png"
          alt="GSC"
          class="logo-img"
        >
      </div>
      <transition name="fade-slide">
        <span
          v-if="!isCollapse"
          class="logo-title"
        >GLOBAL SCM</span>
      </transition>
    </router-link>

    <!-- 菜单 -->
    <div class="sidebar-menu-wrap">
      <el-menu
        :default-active="activeMenu"
        :default-openeds="defaultOpenMenus"
        :collapse="isCollapse"
        :collapse-transition="false"
        background-color="transparent"
        text-color="rgba(255,255,255,0.55)"
        active-text-color="#fff"
        class="sidebar-menu"
        @select="handleMenuSelect"
      >
        <template
          v-for="menu in menuList"
          :key="menu.index"
        >
          <!-- 有子菜单 -->
          <el-sub-menu
            v-if="menu.children && menu.children.length > 0"
            :index="menu.index"
            popper-class="sidebar-popper"
          >
            <template #title>
              <el-icon :size="18">
                <component :is="resolveIcon(menu.icon) || Menu" />
              </el-icon>
              <span class="menu-title">{{ menu.title }}</span>
            </template>
            <template
              v-for="child in menu.children"
              :key="child.index"
            >
              <!-- 子节点也有子菜单 → 嵌套 el-sub-menu -->
              <el-sub-menu
                v-if="child.children && child.children.length > 0"
                :index="child.index"
                popper-class="sidebar-popper"
              >
                <template #title>
                  <span class="menu-title">{{ child.title }}</span>
                </template>
                <el-menu-item
                  v-for="grandChild in child.children"
                  :key="grandChild.index"
                  :index="grandChild.index"
                >
                  <span class="menu-title">{{ grandChild.title }}</span>
                </el-menu-item>
              </el-sub-menu>
              <!-- 叶子节点 -->
              <el-menu-item
                v-else
                :index="child.index"
              >
                <span class="menu-title">{{ child.title }}</span>
              </el-menu-item>
            </template>
          </el-sub-menu>
          <!-- 叶子节点（无子菜单） -->
          <el-menu-item
            v-else
            :index="menu.index"
          >
            <el-icon :size="18">
              <component :is="resolveIcon(menu.icon) || Menu" />
            </el-icon>
            <span class="menu-title">{{ menu.title }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </div>
  </div>
</template>
<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { usePermissionStore } from '@/stores/permission'
import { homePath } from '@/utils/homePath'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'
import {
  // 通用
  Menu, Setting, HomeFilled, List, Grid, Search, Tools, Clock, Calendar,
  // 用户 / 权限
  User, UserFilled, Key, Lock, Management,
  // 文件 / 编辑
  Document, Files, Notebook, Tickets, EditPen, Share, Postcard,
  // 财务 / 结算
  Money, CreditCard, Wallet, Coin, PriceTag, RefreshRight,
  // 可信 / 认证
  CircleCheck, CircleCheckFilled, Stamp, Medal, Trophy, Star, StarFilled, Checked, WarningFilled,
  // 商机 / 分析
  TrendCharts, DataLine, Histogram, PieChart, Rank, Opportunity, View,
  // 供应商 / 商品
  Goods, Shop, Box, Van, Collection, DocumentAdd,
  // 全球直采 / 采购
  ShoppingCart, ShoppingCartFull, Connection, Link, Aim, Compass, Guide,
  // 企业
  OfficeBuilding, Briefcase, Monitor, Platform, DataBoard,
  // 物流
  Ship,
  // 提示
  Bell, ChatDotSquare, ChatLineSquare, Warning, InfoFilled, SuccessFilled,
  // 操作
  Edit, Delete,
} from '@element-plus/icons-vue'

/** 已导入图标映射表（PascalCase → 组件），新增图标只需在 import 行加名字 */
const Icons = {
  Menu, Setting, HomeFilled, List, Grid, Search, Tools, Clock, Calendar,
  User, UserFilled, Key, Lock, Management,
  Document, Files, Notebook, Tickets, EditPen, Share, Postcard,
  Money, CreditCard, Wallet, Coin, PriceTag, RefreshRight,
  CircleCheck, CircleCheckFilled, Stamp, Medal, Trophy, Star, StarFilled, Checked, WarningFilled,
  TrendCharts, DataLine, Histogram, PieChart, Rank, Opportunity, View,
  Goods, Shop, Box, Van, Collection, DocumentAdd,
  ShoppingCart, ShoppingCartFull, Connection, Link, Aim, Compass, Guide,
  OfficeBuilding, Briefcase, Monitor, Platform, DataBoard,
  Ship,
  Bell, ChatDotSquare, ChatLineSquare, Warning, InfoFilled, SuccessFilled,
  Edit, Delete,
}

const route = useRoute()
const router = useRouter()
const permissionStore = usePermissionStore()
const appStore = useAppStore()
const authStore = useAuthStore()
const { t } = useI18n()

/** 英文模式下按菜单路径查语言包，查不到 fallback 后端中文名 */
function getMenuTitle(path, fallbackName) {
  if (appStore.lang !== 'en' || !path) return fallbackName
  const key = `menu.${path}`
  const translated = t(key)
  return translated === key ? fallbackName : translated
}

const menuList = computed(() => {
  const pt = authStore.profileType  // buyer | supplier | both | null（未加载时不过滤）

  function transform(menus, parentPath) {
    if (!menus || menus.length === 0) return []
    return menus
      .filter((m) => m.isVisible !== 0)
      .map((m) => {
        let children = m.children ? transform(m.children, m.menuPath) : []
        // 需求广场子菜单按身份过滤
        if (m.menuPath === '/matching/square' && pt) {
          children = children.filter((c) => {
            if (c.index === '/matching/square/buyer') return pt === 'buyer' || pt === 'both'
            if (c.index === '/matching/square/supplier') return pt === 'supplier' || pt === 'both'
            return true
          })
        }
        return {
          index: m.menuPath || `/${m.id}`,
          title: getMenuTitle(m.menuPath, m.menuName),
          icon: m.menuIcon,
          children,
        }
      })
  }
  return transform(permissionStore.menus, '')
})

const activeMenu = computed(() => route.meta?.activeMenu || route.path)
const isCollapse = computed(() => appStore.sidebarCollapsed)

/** 角色自定默认展开菜单 */
const defaultOpenMenus = computed(() => {
  const roles = permissionStore.roles
  if (roles.includes('DUE_DILIGENCE') || roles.includes('FIRM')) {
    return ['/due-diligence']
  }
  return []
})

/** 根据 kebab-case 图标名获取 Element Plus 图标组件
 *  如 "user-filled" → UserFilled，"home-filled" → HomeFilled
 *  @param {string} iconName - 图标名称
 *  @returns {Component|null} 找不到返回 null
 */
function resolveIcon(iconName) {
  if (!iconName) return null
  const pascal = iconName.split('-').map((s) => s.charAt(0).toUpperCase() + s.slice(1)).join('')
  return Icons[pascal] || null
}

function handleMenuSelect(index) {
  router.push(index)
}
</script>

<style scoped>
.sidebar {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #0d1b2a 0%, #0a1525 100%);
  border-right: 1px solid rgba(255, 255, 255, 0.06);
}

/* ---- Logo ---- */
.sidebar-logo {
  display: flex;
  align-items: center;
  height: 56px;
  padding: 0 18px;
  margin: 5px 0;
  gap: 10px;
  text-decoration: none;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  flex-shrink: 0;
  overflow: hidden;
  transition: padding 0.3s;
}

.logo-mark {
  width: 30px;
  height: 30px;
  flex-shrink: 0;
}

.logo-img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.sidebar-logo:hover .logo-mark {
  filter: drop-shadow(0 0 14px rgba(0, 180, 255, 0.5));
}

.logo-title {
  font-family: 'DM Sans', -apple-system, sans-serif;
  font-size: 16px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 0.5px;
  white-space: nowrap;
}

/* ---- 菜单 ---- */
.sidebar-menu-wrap {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 8px 0;
}

/* 分组间距：非首个顶级菜单顶上留白 */
:deep(.sidebar-menu > li + li) {
  margin-top: 8px;
}

.sidebar-menu {
  border-right: none !important;
}

.menu-title {
  margin-left: 8px;
}

/* el-menu-item 内 el-icon 对齐 */
:deep(.sidebar-menu .el-menu-item .el-icon),
:deep(.sidebar-menu .el-sub-menu__title .el-icon) {
  flex-shrink: 0;
}

/* 菜单项通用 */
:deep(.sidebar-menu .el-menu-item),
:deep(.sidebar-menu .el-sub-menu__title) {
  height: 44px;
  line-height: 44px;
  margin: 0 8px;
  border-radius: 10px;
  font-size: 13px;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

/* 菜单项之间间距用 ul padding，不干扰动画 */
:deep(.sidebar-menu > li) {
  margin-bottom: 8px;
}

:deep(.sidebar-menu > li:last-child) {
  margin-bottom: 0;
}

:deep(.sidebar-menu .el-menu-item:hover),
:deep(.sidebar-menu .el-sub-menu__title:hover) {
  background: rgba(255, 255, 255, 0.06) !important;
  color: rgba(255, 255, 255, 0.85) !important;
}

:deep(.sidebar-menu .el-menu-item.is-active) {
  background: linear-gradient(135deg, rgba(0, 102, 255, 0.35), rgba(0, 160, 255, 0.2)) !important;
  color: #fff !important;
  font-weight: 500;
  box-shadow: 0 2px 8px rgba(0, 100, 255, 0.15);
}

:deep(.sidebar-menu .el-sub-menu.is-active > .el-sub-menu__title) {
  color: #00b4ff !important;
}

/* 二级子菜单缩进（智能撮合 → 企业画像/需求广场 title） */
:deep(.sidebar-menu > li > .el-menu > .el-menu-item),
:deep(.sidebar-menu > li > .el-menu > .el-sub-menu > .el-sub-menu__title) {
  height: 44px;
  line-height: 44px;
  padding-left: 60px !important;
  margin: 0 8px;
}

/* 三级子菜单缩进（需求广场 → 找供应商/找需求），比二级多缩进 */
:deep(.sidebar-menu .el-menu .el-menu .el-menu-item) {
  padding-left: 80px !important;
}

/* 折叠状态 */
:deep(.sidebar-menu.el-menu--collapse) {
  width: 64px;
}

:deep(.sidebar-menu.el-menu--collapse .el-menu-item),
:deep(.sidebar-menu.el-menu--collapse .el-sub-menu__title) {
  justify-content: center;
  padding: 0 !important;
}

:deep(.sidebar-menu.el-menu--collapse .el-sub-menu__title span),
:deep(.sidebar-menu.el-menu--collapse .menu-title) {
  display: none;
}

/* ---- 底部 ---- */
.sidebar-footer {
  flex-shrink: 0;
  padding: 12px 18px;
  overflow: hidden;
}

.footer-line {
  height: 1px;
  background: rgba(255, 255, 255, 0.08);
  margin-bottom: 8px;
}

.footer-version {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.2);
  font-family: 'DM Sans', sans-serif;
}

/* ---- 过渡 ---- */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.fade-slide-enter-from,
.fade-slide-leave-to {
  opacity: 0;
  transform: translateX(-8px);
}
</style>
