/**
 * SRM Admin - 路由表
 * 路由 path 必须与后端 menu.menu_path 严格一致
 * 业务页统一挂在同一个 AdminLayout 下，避免切菜单时整页重挂导致组件叠显
 */
import AdminLayout from '@/layout/AdminLayout.vue'
import { usePermissionStore } from '@/stores/permission'
import { homePath } from '@/utils/homePath'

export const constantRoutes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/Login.vue'),
    meta: { title: '登录', hidden: true },
  },
  {
    path: '/forgot-password',
    name: 'ForgotPassword',
    component: () => import('@/views/login/ForgotPassword.vue'),
    meta: { title: '忘记密码', hidden: true },
  },
  {
    path: '/403',
    name: '403',
    component: () => import('@/components/error/403.vue'),
    meta: { title: '无权限', hidden: true },
  },
  {
    path: '/404',
    name: '404',
    component: () => import('@/components/error/404.vue'),
    meta: { title: '页面不存在', hidden: true },
  },
]

export const asyncRoutes = [
  {
    path: '/',
    component: AdminLayout,
    // 按角色决定首页（事务所只有事务所工作台，预审用户只有尽调页，不能统一跳 /dashboard）
    redirect: () => homePath(usePermissionStore().roles),
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/Dashboard.vue'),
        meta: { title: '工作台', hidden: true },
      },

      // ---- 个人中心 ----
      {
        path: 'profile',
        name: 'PersonalProfile',
        component: () => import('@/views/profile/PersonalProfile.vue'),
        meta: { title: '个人信息', hidden: true },
      },
      {
        path: 'change-password',
        name: 'ChangePassword',
        component: () => import('@/views/profile/ChangePassword.vue'),
        meta: { title: '修改密码', hidden: true },
      },

      // ---- 系统管理 ----
      {
        path: 'admin/tenant',
        name: 'TenantManagement',
        component: () => import('@/views/system/TenantManagement.vue'),
        meta: { title: '租户管理', permissions: ['tenant'] },
      },
      {
        path: 'admin/user',
        name: 'UserManagement',
        component: () => import('@/views/system/UserManagement.vue'),
        meta: { title: '用户管理', permissions: ['user'] },
      },
      {
        path: 'admin/role',
        name: 'RoleManagement',
        component: () => import('@/views/system/RoleManagement.vue'),
        meta: { title: '角色管理', permissions: ['role'] },
      },
      {
        path: 'admin/application',
        name: 'ApplicationReview',
        component: () => import('@/views/system/ApplicationReview.vue'),
        meta: { title: '入驻预审', permissions: ['application'] },
      },
      {
        path: 'admin/menu',
        name: 'MenuManagement',
        component: () => import('@/views/system/MenuManagement.vue'),
        meta: { title: '菜单管理', permissions: ['menu'] },
      },
      {
        path: 'admin/audit-log',
        name: 'AuditLog',
        component: () => import('@/views/system/AuditLog.vue'),
        meta: { title: '操作日志', permissions: ['audit-log'] },
      },
      {
        path: 'admin/sanction',
        name: 'SanctionList',
        component: () => import('@/views/system/SanctionList.vue'),
        meta: { title: '制裁名单库', permissions: ['sanction'] },
      },

      // ---- AI 知识库 ----
      {
        path: 'ai/knowledge',
        name: 'AiKnowledgeBase',
        component: () => import('@/views/ai/AiKnowledgeBase.vue'),
        // permCode ai:document 需与后端 permission 表一致，否则路由守卫跳 403
        meta: { title: 'AI知识库', permissions: ['ai:document'] },
      },

      // ---- 尽职调查 ----
      {
        path: 'due-diligence',
        name: 'DueDiligence',
        component: () => import('@/views/due-diligence/DueDiligence.vue'),
        meta: { title: '尽职调查', permissions: ['due-diligence'] },
      },
      {
        path: 'due-diligence/cases',
        name: 'DueDiligenceCases',
        component: () => import('@/views/due-diligence/DueDiligenceCases.vue'),
        meta: { title: '尽调管理', permissions: ['due-diligence:manage'] },
      },
      {
        path: 'due-diligence/firm',
        name: 'FirmWorkstation',
        component: () => import('@/views/due-diligence/FirmWorkstation.vue'),
        meta: { title: '事务所工作台', permissions: ['due-diligence:firm'] },
      },

      // ---- 智能撮合 ----
      {
        path: 'matching/profile',
        name: 'EnterpriseProfile',
        component: () => import('@/views/match/EnterpriseProfile.vue'),
        meta: { title: '企业画像', permissions: ['match:profile'] },
      },
      {
        path: 'matching/requirement',
        name: 'RequirementPublish',
        component: () => import('@/views/match/RequirementPublish.vue'),
        meta: { title: '采购需求', permissions: ['match:publish'] },
      },
      {
        path: 'matching/requirement/:id/match',
        name: 'MatchResult',
        component: () => import('@/views/match/MatchResult.vue'),
        meta: {
          title: '智能匹配',
          permissions: ['match:publish'],
          hidden: true,
          activeMenu: '/matching/requirement',
          breadcrumb: [{ title: '采购需求', path: '/matching/requirement' }],
        },
      },
      {
        path: 'matching/connections',
        name: 'Connections',
        component: () => import('@/views/match/Connections.vue'),
        meta: { title: '对接记录', permissions: ['match:connect'] },
      },
      {
        path: 'matching/square/buyer',
        name: 'FindSuppliers',
        component: () => import('@/views/match/FindSuppliers.vue'),
        meta: { title: '找供应商', permissions: ['match:square'] },
      },
      {
        path: 'matching/square/supplier',
        name: 'FindRequirements',
        component: () => import('@/views/match/FindRequirements.vue'),
        meta: { title: '找需求', permissions: ['match:square'] },
      },

      // ---- 采购管理 ----
      {
        path: 'procurement/inquiry',
        name: 'InquiryManagement',
        component: () => import('@/views/procurement/InquiryManagement.vue'),
        meta: { title: '询盘管理', permissions: ['procurement:inquiry'] },
      },
      {
        path: 'procurement/compare',
        name: 'PriceComparison',
        component: () => import('@/views/procurement/PriceComparison.vue'),
        meta: { title: '比价管理', permissions: ['procurement:compare'] },
      },
      {
        path: 'procurement/contract',
        name: 'ContractManagement',
        component: () => import('@/views/procurement/ContractManagement.vue'),
        meta: { title: '合同管理', permissions: ['procurement:contract'] },
      },

      // ---- 可信交付 ----
      {
        path: 'delivery/manage/shipment',
        name: 'DeliveryShipment',
        component: () => import('@/views/delivery/ShipmentManagement.vue'),
        meta: { title: '发货管理', permissions: ['delivery:shipment'] },
      },
      {
        path: 'delivery/manage/receipt',
        name: 'DeliveryReceipt',
        component: () => import('@/views/delivery/ReceiptManagement.vue'),
        meta: { title: '收货确认', permissions: ['delivery:receipt'] },
      },
      {
        path: 'delivery/manage/acceptance',
        name: 'DeliveryAcceptance',
        component: () => import('@/views/delivery/AcceptanceManagement.vue'),
        meta: { title: '验收管理', permissions: ['delivery:acceptance'] },
      },
      {
        path: 'delivery/tracking',
        name: 'LogisticsTracking',
        component: () => import('@/views/delivery/LogisticsTracking.vue'),
        meta: { title: '物流追踪', permissions: ['delivery:tracking'] },
      },
      {
        path: 'delivery/tracking/:id',
        name: 'LogisticsTrackingDetail',
        component: () => import('@/views/delivery/LogisticsTrackingDetail.vue'),
        meta: {
          title: '物流追踪详情',
          permissions: ['delivery:tracking'],
          hidden: true,
          activeMenu: '/delivery/tracking',
          breadcrumb: [{ title: '物流追踪', path: '/delivery/tracking' }],
        },
      },
      {
        path: 'delivery/risk/dashboard',
        name: 'DeliveryRiskDashboard',
        component: () => import('@/views/delivery/RiskDashboard.vue'),
        meta: { title: '供应保障驾驶舱', permissions: ['delivery:risk'] },
      },
      {
        path: 'delivery/risk/:id',
        name: 'DeliveryRiskDetail',
        component: () => import('@/views/delivery/RiskDetail.vue'),
        meta: {
          title: '风险详情',
          permissions: ['delivery:risk'],
          hidden: true,
          activeMenu: '/delivery/risk/dashboard',
          breadcrumb: [{ title: '供应保障驾驶舱', path: '/delivery/risk/dashboard' }],
        },
      },
      {
        path: 'delivery/risk/workbench',
        name: 'DeliveryRecoveryWorkbench',
        component: () => import('@/views/delivery/RecoveryWorkbench.vue'),
        meta: { title: '补救工作台', permissions: ['delivery:risk'] },
      },
    ],
  },
]

export const catchAllRoute = {
  path: '/:pathMatch(.*)*',
  redirect: '/404',
  meta: { hidden: true },
}
