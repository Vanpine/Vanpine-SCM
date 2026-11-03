# 编码规范详解

> `CLAUDE.md` 的补充 — 前端/后端/全栈/联调/防御性编程的完整详细规范。按需查阅。

## 目录

| Part | 内容 | 关键章节 |
|------|------|------|
| [A](#part-a-前端-srm-admin-frontend) | 前端 | 目录结构、组件规范、列表页模板、API 层、Store、路由、样式、代码质量 |
| [B](#part-b-后端-srm-admin-backend) | 后端 | 模块结构、数据库、Mapper、Controller/Service、多租户、异常、安全 |
| [C](#part-c-全栈通用规范) | 全栈通用 | 配置管理、Stream/函数式、Builder 模式、多表兜底 |
| [D](#part-d-前后端联调项目代码位置与地址) | 联调 | 项目位置、服务地址、接口前缀、环境配置 |
| [E](#part-e-防御性编程与全局异常规范) | 防御性编程 | 异常铁律、前后端错误处理、接口检查清单 |

---

# Part A: 前端 (srm-admin-frontend)

## A.一、项目概述

Vue 3 + Element Plus + Pinia + Vue Router 4 + Vite + Tailwind CSS 4。

**语法：** JavaScript（不用 TypeScript）| **包管理：** npm | **端口：** 5173

---

## A.二、目录结构规范

```
src/
├── api/              # 接口层：按模块拆分，一个模块一个文件
├── assets/           # 静态资源：图标、图片
│   └── icons/        # 图标文件（logo、favicon 等）
├── components/       # 公共组件：单文件直接放根目录，有附属文件再建文件夹
│   ├── AppHeader.vue  # 顶栏
│   ├── Sidebar.vue    # 侧边栏
│   ├── LoginBrand.vue # 登录页品牌面板
│   ├── DragVerify/    # 滑动验证码（含 CSS，保留文件夹）
│   └── error/         # 403/404（多文件，保留文件夹）
├── composables/      # 组合式函数：useTheme、useI18n
├── constants/        # 常量：TOKEN_KEY、USER_STATUS_MAP 等
├── directives/       # 自定义指令：v-permission
├── layout/           # 布局组件
│   └── AdminLayout.vue  # 后台管理布局（侧边栏+顶栏+内容区）
├── router/           # 路由配置
│   ├── index.js      # 路由实例 + 导航守卫
│   └── routes.js     # 路由表（constantRoutes + asyncRoutes）
├── stores/           # Pinia 状态管理
│   ├── auth.js       # 认证状态（token、user、login/logout）
│   ├── permission.js # 权限状态（menus、roles、permissions）
│   └── app.js        # 应用状态（sidebarCollapsed）
├── styles/           # 全局样式
│   ├── theme.css     # CSS 变量（亮色/暗色）
│   ├── index.scss    # 样式入口（Tailwind + 全局重置 + 工具类）
│   └── transition.scss  # 页面过渡动画
├── utils/            # 工具函数
│   ├── auth.js       # Token 读写
│   └── permission.js # 权限判断（hasPermission 等）
├── views/            # 业务页面：按业务模块分文件夹
│   ├── login/        # 登录相关
│   │   ├── Login.vue
│   │   └── ForgotPassword.vue
│   ├── dashboard/    # 工作台
│   │   └── Dashboard.vue
│   ├── profile/      # 个人中心
│   │   ├── PersonalProfile.vue
│   │   └── ChangePassword.vue
│   └── system/       # 系统管理
│       ├── TenantManagement.vue
│       ├── UserManagement.vue
│       ├── UserForm.vue
│       ├── RoleManagement.vue
│       ├── RoleForm.vue
│       ├── MenuManagement.vue
│       ├── ApplicationReview.vue
│       └── AuditLog.vue
├── App.vue           # 根组件
└── main.js           # 入口文件
```

### A.2.1 文件命名

| 类型 | 规则 | 示例 |
|------|------|------|
| 业务页面 | 大驼峰，`业务名.vue` | `UserManagement.vue` |
| 弹窗/表单 | 大驼峰，`业务名Form.vue` | `UserForm.vue` |
| 公共组件（单文件） | 大驼峰 `.vue`，不放文件夹 | `components/Sidebar.vue` |
| 公共组件（多文件） | 文件夹 + `index.vue` | `components/DragVerify/index.vue` |
| API 文件 | 小写，`模块名.js` | `api/user.js` |
| Store 文件 | 小写，`模块名.js` | `stores/auth.js` |
| 工具/常量 | 小写，描述性 | `constants/index.js`、`utils/permission.js` |

### A.2.2 禁止的行为

- ❌ 不要在 `views/` 下直接放大量平铺的 `.vue` 文件，应按业务模块建文件夹（如 `views/system/UserManagement.vue`）
- ❌ 不要在一个 `.vue` 文件里写多个组件
- ❌ 不要把业务逻辑写在 `App.vue` 或 `main.js` 里
- ❌ 不要留空目录和空文件

---

## A.三、Vue 组件规范

### A.3.1 文件结构：template 在上，script 在下

```vue
<template>
  <div class="page-container">
    <!-- HTML 内容 -->
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'

// 1. props / emits
// 2. composables
// 3. 响应式数据
// 4. computed
// 5. 方法
// 6. 生命周期
</script>

<style scoped>
/* 组件私有样式 */
</style>
```

### A.3.2 组件设计原则

1. **单一职责**：一个组件只做一件事
2. **Props 向下，Events 向上**：数据通过 props 传入，变更通过 emit 传出
3. **可复用优先**：如果两处用到相同 UI，抽取为公共组件放到 `components/`
4. **弹窗类组件**用 `v-model:visible` + `emit('submit', formData)` 模式：

```vue
<!-- 父组件 -->
<UserForm v-model:visible="dialogVisible" :user-id="editingId" @submit="handleFormSubmit" />

<!-- 子组件 UserForm.vue -->
<script setup>
const props = defineProps({
  visible: Boolean,
  userId: { type: Number, default: null },
})
const emit = defineEmits(['update:visible', 'submit'])
function handleCancel() { emit('update:visible', false) }
function handleSubmit() { emit('submit', { ...form }) }
</script>
```

### A.3.3 响应式数据声明顺序

```js
// 1. props / emits
const props = defineProps({ ... })
const emit = defineEmits([...])

// 2. composables
const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

// 3. ref / reactive（声明顺序 = 模板出现顺序，避免 TDZ）
const formRef = ref(null)
const loading = ref(false)
const form = reactive({ name: '', email: '' })

// 4. computed
const isValid = computed(() => ...)

// 5. watch（放在它所依赖的数据声明之后，避免 TDZ 报错）
watch(() => route.query.step, callback, { immediate: true })

// 6. 方法
async function handleSubmit() { ... }

// 7. 生命周期
onMounted(() => { ... })
```

> ⚠️ **TDZ 陷阱**：`watch` 带 `{ immediate: true }` 会在 setup 阶段立即执行，此时只能访问前面已声明的变量。所有 `watch` 依赖的 `ref`/`reactive`/`let` 必须放在 `watch` **前面**声明。

### A.3.4 列表页布局规范（必须遵守）

**所有搜索+表格+分页的列表页统一使用双卡片布局**，参考 `views/system/TenantManagement.vue`：

```vue
<template>
  <div>
    <!-- 1. 搜索卡片 -->
    <el-card class="search-card">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="关键字">
          <el-input v-model="searchForm.keyword" placeholder="..." clearable style="width: 220px" @keyup.enter="handleSearch" />
        </el-form-item>
        <!-- 其他筛选项 ... -->
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 2. 内容卡片 -->
    <el-card class="content-card">
      <!-- 2a. 工具栏：标题 + 操作按钮 -->
      <div class="table-toolbar">
        <span class="text-base font-semibold text-gray-700">XX列表</span>
        <el-button type="primary" :icon="Plus" @click="handleCreate">新增</el-button>
      </div>

      <!-- 2b. 数据表格 -->
      <el-table :data="list" v-loading="loading" stripe border>
        <!-- 列定义 ... -->
      </el-table>

      <!-- 2c. 分页 -->
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="PAGE_SIZE_OPTIONS"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>
  </div>
</template>
```

| 规则 | 说明 |
|------|------|
| 双卡片结构 | `search-card` + `content-card`，搜索和表格**不能**挤在同一张卡片里 |
| 全局样式类 | `search-card`、`content-card`、`table-toolbar`、`pagination-wrap` 已在 `styles/index.scss` 定义，**不要在组件 scoped 里重复写** |
| 搜索卡片 | 纯搜索表单，`el-form` 带 `:inline="true"` |
| 内容卡片 | `table-toolbar`（标题 + 按钮）+ `el-table` + `pagination-wrap` |
| 表格标题 | 用 `<span class="text-base font-semibold text-gray-700">` 不加自定义样式 |
| 分页 | 统用 `PAGE_SIZE_OPTIONS` 常量，分页条居中（全局 `.pagination-wrap` 已设 `justify-content: center; margin-top: 30px`） |
| 页面最外层 | 用 `<div>` 不带额外 class，不要用 `page-container` |

**表格列宽规则**（必须遵守）：

| 类型 | 属性 | 说明 | 示例 |
|------|------|------|------|
| 文本内容列 | `min-width="N"` | 内容长度不可预测（企业名、标题、品类等），保证最小宽度不挤压内容 | `<el-table-column prop="title" label="需求标题" min-width="300" show-overflow-tooltip />` |
| 固定内容列 | `width="N"` | 仅用于内容长度确定的列（ID、数字、状态标签） | `<el-table-column type="index" width="55" />` |
| 时间列 | `min-width="120"` | `YYYY-MM-DD HH:mm` 格式固定用 120 | `<el-table-column label="发起时间" min-width="120" />` |
| 时间列（带秒） | `min-width="160"` | `YYYY-MM-DD HH:mm:ss` 格式用 160 | `<el-table-column label="提交时间" min-width="160" />` |

**原则**：
- 所有文本列用 `min-width`，总和超过容器宽度时自动出现横向滚动条，用户滑动查看
- `width` 只给 ID、数量、状态标签等短内容列
- 搭配 `show-overflow-tooltip` 确保超长文本有省略号 + hover 提示

**状态标签颜色规则**（必须遵守）：

| 状态类型 | `el-tag` 的 `type` | 示例 |
|---------|-------------------|------|
| 待处理 / 进行中 | `primary` | 待发货、待收货、待验收、待审核 |
| 已完成 / 成功 | `success` | 已验收、已完成、已通过 |
| 异常 / 退回 / 拒绝 | `danger` | 已退回、已驳回、已拒绝 |

原则：
- 所有「待 XXX」的进行中状态统一用 `primary`（蓝）
- 完成态用 `success`（绿），异常/退回态用 `danger`（红）
- **不要用 `warning` 表示「待处理」**——`warning` 是警示语义，进行中状态用 `primary`

**搜索框规则**（必须遵守）：

| 规则 | 说明 | 示例 |
|------|------|------|
| label 准确 | 搜单个字段用字段名，搜多字段覆盖用 `关键词` | `label="关键词"` 而不是 `label="品类"` |
| placeholder 提示范围 | 列出可搜索的字段，用 `/` 分隔 | `placeholder="需求标题/品类/地域/资质"` |
| 输入框宽度 | 能容纳 placeholder 文字，220-300px | `style="width:280px"` |
| 回车触发 | 必须绑 `@keyup.enter="handleSearch"` | |

**操作列规则**（必须遵守）：
- 操作 ≤ 2 个：直接平铺按钮，间隔 8-10px
- 操作 ≥ 3 个：保留最重要的 1 个外露，其余收进 `⋮`（MoreFilled 图标）下拉菜单，关闭/删除等危险操作用 `divided` 分隔线隔开并标红
- 示例：`open` 状态 4 个操作 → "智能匹配"外露 + `⋮` 收起（详情/编辑/关闭）

**⋮ 下拉菜单标准写法**（必须遵守）：

```vue
<!-- 三点按钮：MoreFilled 原生横排，rotate(90deg) 转竖排，margin-top 微调对齐 -->
<el-dropdown trigger="click" @command="(cmd) => handleCmd(cmd, row)">
  <el-icon :size="16" style="cursor:pointer;color:#409eff;margin-left:4px;margin-top:5px;transform:rotate(90deg)"><MoreFilled /></el-icon>
  <template #dropdown>
    <el-dropdown-menu>
      <el-dropdown-item command="action1">操作一</el-dropdown-item>
      <el-dropdown-item command="delete" divided style="color:#f56c6c">删除</el-dropdown-item>
    </el-dropdown-menu>
  </template>
</el-dropdown>
```

| 规则 | 说明 |
|------|------|
| 图标 | 统一用 `MoreFilled`，`size="16"`，旋转 90° 转竖排 + `margin-top:5px` 对齐 |
| 触发 | `trigger="click"`，不设 `hide-on-click`（默认点击选项后自动关闭） |
| 危险操作 | 放在最后，加 `divided` 分隔线 + `style="color:#f56c6c"` 标红 |
| 命令分发 | 用 `@command="(cmd) => handleCmd(cmd, row)"` 统一路由到 switch 方法 |

**禁止行为**：
- ❌ 不要自创 `search-form`、`page-container`、`flex justify-end mt-4` 等临时 class
- ❌ 不要在 `scoped` 里覆写 `search-card`、`content-card`、`pagination-wrap` 的全局样式
- ❌ 不要把搜索和表格塞同一个 `el-card`

**空状态占位规则**（必须遵守）：

表格无数据时必须用 `<el-empty>` 占位，并区分"无数据"和"搜不到"两种场景：

```vue
<el-empty v-if="!loading && list.length === 0" :description="searchForm.keyword ? '未找到匹配的XXX' : '暂无XXX'" />
```

| 规则 | 说明 |
|------|------|
| 位置 | `</el-table>` 之后，`pagination-wrap` 之前 |
| 条件 | `v-if="!loading && list.length === 0"` |
| 区分搜索 | 用三元判断 `searchForm.keyword`：有关键词 → "未找到匹配的XXX"，无关键词 → "暂无XXX" |

**留言列规则**（必须遵守）：

对接记录等含留言列的页面，留言展示遵循以下规则：

| 规则 | 说明 |
|------|------|
| 显示行数 | 1 行，超出省略号 `text-overflow: ellipsis` |
| 截断检测 | `:ref` 回调中比较 `scrollWidth > clientWidth`，截断的加 `msg-truncated` class |
| 点击弹窗 | 仅截断文字可点击弹窗查看全文，未截断的文字点击无反应 |
| 手型/下划线 | 仅 `.msg-truncated` 显示 `cursor: pointer` + hover 变色下划线，普通文字无交互样式 |
| 弹窗内容 | `white-space: pre-wrap; word-break: break-word` 保留换行，最大高度 60vh 滚动 |
| 展示内容 | 始终展示对方写的内容：对方发起的→原始留言，我发起被拒→对方拒绝理由，我发起未拒→我写的留言 |

```vue
<!-- 模板 -->
<span :class="['msg-cell', { 'msg-rejected': isRejected }]"
      :ref="(el) => checkTruncate(el)" @click="openMessage($event, row)">
  {{ messageText }}
</span>

<!-- 检测截断 -->
function checkTruncate(el) {
  if (!el) return
  el.classList.toggle('msg-truncated', el.scrollWidth > el.clientWidth)
}
function openMessage(e, row) {
  if (e.target.scrollWidth <= e.target.clientWidth) return  // 未截断不弹
  messageContent.value = row.message
  messageVisible.value = true
}
```

```css
.msg-cell { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #606266; }
.msg-rejected { color: #f56c6c; }
.msg-truncated { cursor: pointer; }
.msg-truncated:hover { color: #409eff; text-decoration: underline; }
.msg-truncated.msg-rejected:hover { color: #f56c6c; text-decoration: underline; }
```

### A.3.5 嵌套子菜单规范（二级以上菜单必须遵守）

**背景**：Element Plus `el-menu` 支持嵌套 `el-sub-menu`，但 Sidebar 模板和路由必须配合才能正常工作。不遵守以下规则会导致 404 或菜单不显示。

#### 1. 路由注册

**多级菜单路径必须作为父路由的 children 注册，不能定义为独立的顶层路由。**

```js
// ✅ 正确：嵌套在父路由 children 下
{
  path: '/matching',
  component: AdminLayout,
  children: [
    { path: 'profile', name: 'EnterpriseProfile', component: () => import('@/views/match/EnterpriseProfile.vue'), meta: { title: '企业画像' } },
    // 二级子菜单（需求广场 → 找供应商）
    { path: 'square/buyer', name: 'FindSuppliers', component: () => import('@/views/match/FindSuppliers.vue'), meta: { title: '找供应商' } },
  ],
}

// ❌ 错误：与父路由同级 → Vue Router 4 前缀归并导致匹配失败 → 404
{
  path: '/matching/square/buyer',  // 和 /matching 共享前缀，冲突！
  component: AdminLayout,
  children: [{ path: '', name: 'FindSuppliers', ... }],
}
```

| 规则 | 说明 |
|------|------|
| 嵌套路径 | 子菜单路径是父路由 path 的延伸，必须在父路由 `children` 里用相对路径 |
| 不要顶层注册 | 不要复制 `/profile` 模式（`/profile` 没有同前缀的父路由所以能工作，多级路径不行） |
| 菜单 path | 后端 `menu.menu_path` 的值需要与前端的路由 path 完全一致 |

#### 2. Sidebar 模板：children 渲染必须递归

**Sidebar 的 `<el-sub-menu>` 内部不能把所有 children 都当叶子节点渲染，必须区分"有子节点"和"无子节点"。**

```vue
<!-- ✅ 正确：children 里分两种情况 -->
<el-sub-menu v-if="menu.children && menu.children.length > 0" :index="menu.index">
  <template #title>...</template>
  <template v-for="child in menu.children" :key="child.index">
    <!-- 子节点也有孩子 → 继续用 el-sub-menu -->
    <el-sub-menu v-if="child.children && child.children.length > 0" :index="child.index">
      <template #title>{{ child.title }}</template>
      <el-menu-item v-for="grandChild in child.children" :key="grandChild.index" :index="grandChild.index">
        {{ grandChild.title }}
      </el-menu-item>
    </el-sub-menu>
    <!-- 真正的叶子节点 → el-menu-item -->
    <el-menu-item v-else :index="child.index">{{ child.title }}</el-menu-item>
  </template>
</el-sub-menu>

<!-- ❌ 错误：children 全用 el-menu-item → 有孙子节点的菜单被压平成叶子 -->
<el-sub-menu ...>
  <el-menu-item v-for="child in menu.children" :index="child.index" />
</el-sub-menu>
```

#### 3. CSS：用 `>` 直接子选择器区分层级

**多级嵌套时，不同层级需要不同的 `padding-left` 来体现视觉层次。必须用 `>` 直接子选择器，不能用后代选择器（会误伤更深层级）。**

```css
/* 二级菜单项（父菜单 > 直接子项） */
:deep(.sidebar-menu > li > .el-menu > .el-menu-item),
:deep(.sidebar-menu > li > .el-menu > .el-sub-menu > .el-sub-menu__title) {
  padding-left: 60px !important;
}

/* 三级菜单项（嵌套子菜单内的项，比二级多缩进 20px） */
:deep(.sidebar-menu .el-menu .el-menu .el-menu-item) {
  padding-left: 80px !important;
}
```

| 规则 | 说明 |
|------|------|
| `>` 限定层级 | 二级用 `> li > .el-menu >` 只选直接子元素，不穿透到三级 |
| 后代选择器 | 三级用 `.el-menu .el-menu .el-menu-item`（两个 `.el-menu` 表示嵌套两层子菜单） |
| 折叠兼容 | 折叠状态 `.el-menu--collapse` 已有全局覆盖（`padding: 0 !important`），不用额外处理 |
| 支持更多层级 | N 级菜单同理：路由→children 嵌套，模板→递归判断，CSS→按 `.el-menu` 层数递增 `padding-left` |

---

## A.四、API 层规范

### A.4.1 文件组织

每个业务模块一个 API 文件，放在 `src/api/` 下：

```
src/api/
├── request.js       # axios 实例 + 拦截器
├── auth.js          # 认证相关（login、logout、refresh、me、menus）
├── user.js          # 用户管理
├── role.js          # 角色管理
├── tenant.js        # 租户管理
├── menu.js          # 菜单权限
├── application.js   # 入驻申请审核
├── audit-log.js     # 操作日志
└── upload.js        # 文件上传
```

### A.4.2 API 函数写法

```js
/**
 * 获取用户分页列表
 * @param {Object} params - { pageNum, pageSize, username, realName, status }
 * @returns {Promise}
 */
export function getUserList(params) {
  return request.get('/users', { params })
}

/**
 * 新增用户
 * @param {Object} data - { username, password, realName, email, phone }
 * @returns {Promise}
 */
export function createUser(data) {
  return request.post('/users', data)
}
```

### A.4.3 request.js 规则

**拦截器是唯一的错误弹窗来源**：
- 请求拦截器：自动注入 `Authorization: Bearer xxx`
- 响应拦截器 `code === 200`：正常返回，不弹窗
- 响应拦截器 `code !== 200`：**弹 `ElMessage.error`**（唯一错误弹窗源）
- HTTP 400/403/500：**弹 `ElMessage.error`**（后端 BizException 返回 400）
- HTTP 401：自动刷新 Token，刷新失败 → 清空缓存 → 跳转 `/login`
- 白名单路径不拦截 401：`/auth/login`、`/auth/refresh-token`、`/auth/forgot-password`、`/auth/reset-password`

**组件规则**：
- 成功 → 组件弹 `ElMessage.success()`
- 失败 → **组件不弹错误**，拦截器已处理；catch 块只做状态恢复（`loading = false` 等）

---

## A.五、Store 规范（Pinia）

### A.5.1 文件结构

```js
/**
 * SRM Admin - 模块名
 * 一句话描述
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useXxxStore = defineStore('xxx', () => {
  // ---- state ----
  const data = ref(null)

  // ---- getters ----
  const isEmpty = computed(() => !data.value)

  // ---- actions ----
  async function fetchData() { ... }
  function clearData() { ... }

  return { data, isEmpty, fetchData, clearData }
})
```

### A.5.2 存放规则

- 需要跨组件共享的状态 → 放 store
- 只在单个组件内使用的状态 → 放组件内的 `ref`/`reactive`
- 不要把所有数据都塞 store，按需放入

---

## A.六、路由规范

### A.6.1 路由分类

| 类型 | 变量 | 说明 |
|------|------|------|
| 常量路由 | `constantRoutes` | 不需要登录：Login、ForgotPassword、403、404 |
| 异步路由 | `asyncRoutes` | 需要登录 + 权限校验：Dashboard、UserManagement 等 |
| 兜底路由 | `catchAllRoute` | 未匹配路径重定向到 404 |

### A.6.2 导航守卫逻辑

```
有 Token？
  ├─ 是 → 已登录访问 /login → 重定向 /dashboard
  │      ├─ 无用户信息 → fetchUserInfo（恢复状态）
  │      ├─ 无菜单 → loadMenus
  │      └─ 有权限要求 → 检查 permissions → 通过/403
  └─ 否 → 在白名单？
           ├─ 是 → 放行
           └─ 否 → 重定向 /login?redirect=原路径
```

### A.6.3 路由 meta 字段

```js
{
  path: '/admin/user',
  name: 'UserManagement',
  component: () => import('@/views/system/UserManagement.vue'),
  meta: {
    title: '用户管理',           // 面包屑 + 页签标题
    permissions: ['user:read'],   // 所需权限
    hidden: true,                 // 不在菜单中显示（如 Dashboard）
  },
}
```

---

## A.七、样式规范

### A.7.1 优先级

1. **Tailwind 原子类** → 简单间距、flex 布局、文字大小
2. **Element Plus 组件** → 用自带 props 控制，必要时 `:deep()` 覆盖
3. **scoped CSS** → 组件独有的复杂样式

### A.7.2 CSS 变量

全局变量定义在 `styles/theme.css`，通过 `html.dark` 切换亮暗色：

```css
:root {
  --color-bg: #ffffff;
  --color-text: #1d1d2e;
}
html.dark {
  --color-bg: #1a1d2e;
  --color-text: #e8e9f0;
}
```

组件内使用 `var(--xxx)` 引用，不硬编码颜色值。

### A.7.3 不要在 scoped CSS 里写 `@keyframes`

Tailwind CSS 4 解析器不兼容。动画相关 CSS 单独放 `.css` 文件。

### A.7.4 覆盖 Element Plus 默认样式（必读！）

**问题**：`scoped` 样式只能影响当前组件的 DOM 元素，Element Plus 组件内部渲染的 DOM（如 `.el-descriptions__label`）不带 `data-v-xxxxx` 属性，`:deep()` 优先级不够时穿透失败。

**解决方案**：在组件 `.vue` 文件底部加一个**不带 `scoped`** 的 `<style>` 块，用组件特有的 class 做命名空间限制范围：

```vue
<!-- ✅ 正确：非 scoped 全局块，用 .firm-right 限定范围 -->
<style>
.firm-right .el-descriptions__label {
  width: 120px !important;
  text-align: center !important;
}
.firm-right .el-descriptions__content {
  font-size: 16px !important;
}
</style>
```

```vue
<!-- ❌ 错误：scoped 穿透经常失败 -->
<style scoped>
:deep(.el-descriptions__label) {
  width: 120px;      /* 可能被 Element Plus 默认样式覆盖 */
}
</style>
```

**规则**：

| 情况 | 方案 |
|------|------|
| 修改自己组件的 DOM | `<style scoped>` ✅ |
| 修改 Element Plus 组件内部样式 | `<style>` 非 scoped + 命名空间 class + `!important` |
| 全局统一的 Element Plus 覆盖 | 放 `styles/index.scss` |

---

## A.八、代码质量规则

### A.8.1 必须遵守

| 规则 | 说明 |
|------|------|
| 每个函数写 JSDoc 注释 | `/** 一句话描述 */` |
| 不用的 import 删除 | 保持文件干净 |
| 空目录删除 | 不保留预留给未来的空壳 |
| API 错误不重复弹窗 | `request.js` 拦截器已统一处理，组件里 catch 只做状态恢复 |
| 弹窗中目标名称高亮 | 用全局类 `<span class="highlight-name">名称</span>`，不要用 `「」` `【】` 等括号包裹 |
| 复用优先 | 遇到相同场景必须调用已有公共函数（`composeFileValue` / `getFileUrl` / `getFileName` 等），禁止重复手写类似逻辑 |

### A.8.1a 确认弹窗规范（ElMessageBox.confirm）

**所有二次确认弹窗必须遵守以下格式**，保证一致体验：

```js
import { ElMessageBox } from 'element-plus'

await ElMessageBox.confirm(
  `<div style="white-space:nowrap;margin-bottom:4px">确认操作 <span class="highlight-name">${目标名称}</span> 吗？</div><div>操作后果说明。</div>`,
  '弹窗标题',
  {
    confirmButtonText: '确认按钮文案',
    cancelButtonText: '取消',
    type: 'warning',        // 危险操作用 danger
    dangerouslyUseHTMLString: true,
    customClass: 'confirmDdMsgBox'  // 自适应宽度
  }
)
```

| 规则 | 说明 |
|------|------|
| 文案结构 | 第一行 `<div style="white-space:nowrap;margin-bottom:4px">` 放问句（不换行），第二行 `<div>` 放后果说明 |
| 目标名称 | 用 `<span class="highlight-name">名称</span>` 高亮 |
| 问号断句 | `？` 之前的内容不能换行，`？` 之后的内容另起一行 |
| `dangerouslyUseHTMLString` | 必须设为 `true` |
| `customClass` | 统用 `'confirmDdMsgBox'`，配套全局 CSS 见下方 |
| 取消处理 | `try { await ElMessageBox.confirm(...) } catch { return }` 用户取消不执行后续 |

**全局 CSS**（在组件非 scoped `<style>` 块或 `index.scss` 中）：

```css
.confirmDdMsgBox { width: auto !important; max-width: 520px; }
```

### A.8.1b Dialog 弹窗表单规范

**所有 `el-dialog` 弹窗必须遵守以下格式**：

```vue
<el-dialog v-model="visible" title="弹窗标题" :close-on-click-modal="false" class="xxx-dialog">
  <el-form label-width="85px" label-position="right">
    <el-form-item label="字段名" required>
      <el-input v-model="form.field" ... />
    </el-form-item>
  </el-form>
  <template #footer>
    <el-button @click="visible = false">取消</el-button>
    <el-button type="primary" :loading="submitting" @click="handleSubmit">确认</el-button>
  </template>
</el-dialog>
```

| 规则 | 说明 |
|------|------|
| 弹窗宽度 | 不用 `width` 属性，用 `class` + 非 scoped CSS 自适应：`width: auto !important; max-width: 520px;` |
| label 宽度 | `label-width="85px"`（4字中文 required 标签最低宽度，非 required 可用 80px） |
| label 位置 | `label-position="right"` 右对齐 |
| 星号间距 | 必须覆盖默认间距：`.xxx-form .el-form-item.is-required .el-form-item__label::before { margin-right: 2px; }` |
| label 不换行 | `.xxx-form .el-form-item__label { white-space: nowrap; }`（5字中文 label 不换行） |
| label 与 content 间距 | 不写 `padding-right`，用 Element Plus 默认值（12px） |
| required 标记 | 用 `required` 属性，不用 `:required="true"` |
| 取消按钮 | 放左边，`@click="visible = false"` |
| 确认按钮 | 放右边，带 `:loading` |

**⚠️ Element Plus 样式覆盖的 `:deep()` 陷阱（重点）**：

- **非 scoped `<style>` 块里禁止使用 `:deep()`**。`:deep()` 只在 `<style scoped>` 里生效；非 scoped 里它会作为字面量选择器 `.xxx :deep(.yyy)`，匹配不到任何元素，样式**静默失效**。
- 星号间距 / label 不换行等覆盖，**必须写在实际生效的样式块**中，选择器用普通后代写法，不能带 `:deep()`。
- 写法对照：

| 样式块 | 正确写法 | ❌ 错误写法 |
|--------|----------|-------------|
| 非 scoped `<style>`（弹窗内覆盖） | `.contract-dialog .el-form-item__label { white-space: nowrap; }` | `.contract-dialog :deep(.el-form-item__label)`（失效） |
| `<style scoped>` + 表单 class | `.xxx-form :deep(.el-form-item__label)`（scoped 内 `:deep()` 才有效） | 非 scoped 里用 `:deep()` |

**完整 CSS 示例**：

```vue
<!-- 非 scoped：弹窗宽度自适应 + 星号间距 + label 不换行（不用 :deep） -->
<style>
.xxx-dialog { width: auto !important; max-width: 520px; }
.xxx-dialog .el-form-item.is-required .el-form-item__label::before { margin-right: 2px; }
.xxx-dialog .el-form-item__label { white-space: nowrap; }
</style>
</style>
```

### A.8.2 禁止

- ❌ `console.log` 提交到代码库（调试完就删）
- ❌ DOM 直接操作（`document.querySelector` 等），用 Vue 响应式
- ❌ 在 `<style scoped>` 里定义 `@import`（用全局 `index.scss` 统一导入）
- ❌ 写死路由路径字符串（用 `router.push` 时优先用命名路由 `{ name: 'Dashboard' }`）
- ❌ 不要在弹窗/提示文案中用 `「」` `【】` 括号包裹目标名称

---

## A.九、前后端对接规范

### A.9.1 响应格式

后端统一返回：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": { ... },
  "timestamp": 1751600000000
}
```

前端 `request.js` 拦截器：`code !== 200` → 自动报错。

### A.9.2 分页格式

```json
{
  "code": 200,
  "data": {
    "records": [...],
    "total": 100,
    "page": 1,
    "pageSize": 20
  }
}
```

### A.9.3 Token 机制

- accessToken 有效期 2h，存 localStorage
- refreshToken 有效期 7d，存 localStorage
- 401 时自动用 refreshToken 换新 accessToken
- 刷新失败 → 清空缓存 → 跳转 `/login`

### A.9.4 文件上传对接（显示/预览必须用原始文件名，禁止 UUID）

上传接口 `POST /admin/upload`（`src/api/upload.js`）返回：

```json
{ "url": "https://oss...", "originalName": "合同.pdf" }
```

**硬性要求：用户可见的文件名（列表/详情/预览标题/下载名）一律显示用户上传的原始文件名，禁止出现 OSS UUID。**

- **存**：上传回调必须用 `composeFileValue(url, originalName)` 存 `url|原始名`，禁止手写拼接、禁止只存 `data.url`
- **列表/详情显示**：用 `getFileName(url|原始名)` 提取原始名
- **预览/跳转**：用 `getFileUrl(url|原始名)` 取纯 URL 放进 iframe 或 `target="_blank"` 链接
- **预览标题**：来自 OSS URL 尾段，后端保证 key 为 `目录/日期/<UUID>/原始文件名.ext`（UUID 在目录层），因此标题就是原始文件名，不出现 UUID
- 禁止把 `data.url` 直接存库 / 直接展示（否则显示 OSS UUID）

```js
import { composeFileValue, getFileUrl, getFileName } from '@/utils/file'

const { data } = await uploadFile(file, 'contract')
form.attachmentUrl = composeFileValue(data.url, data.originalName) // 存库

getFileName(form.attachmentUrl)  // 列表/详情显示 "合同.pdf"
getFileUrl(form.attachmentUrl)   // 预览 iframe / 跳转
```

---

## A.十、前端启动与调试

```bash
cd srm-admin-frontend
npm install
npm run dev        # 启动在 localhost:5173，代理 /api → localhost:8080
npm run build      # 生产构建
```

浏览器 Console 看到 `ReferenceError: Cannot access 'xxx' before initialization` → 变量声明在 `watch({ immediate: true })` 后面，移到前面即可。

---

# Part B: 后端 (srm-admin-backend)

## B.一、项目概述

Spring Boot 3 + MyBatis-Plus 3.5.7，模块化单体，多租户 SAAS。

**包名** `cn.greensmartplanet.gsc` | **JDK** 17+ | **Maven** 3.9+ | **启动类** `SystemApplication.java`

## B.二、模块结构（高内聚低耦合）

```
gsc-backend/
├── gsc-common/          # 公共层：常量、枚举、异常、R（零依赖）
├── gsc-framework/       # 框架层：Security、JWT、多租户、OSS、Mail（只依赖 common）
└── gsc-module-system/   # 业务层：Controller、Service、Mapper、启动入口（依赖 framework + common）
```

**依赖方向**：`system → framework → common`（单向，禁止反向依赖）。

**接口与实现**：framework 定义接口（如 `PermissionProvider`），system 提供实现。单方法接口不单独建文件，合并到已有 Service（如 `AuthServiceImpl implements PermissionProvider`）。共享 Bean 拆到独立 `@Configuration` 类（如 `SecurityBeanConfig`），解决循环依赖。

**OSS 文件上传 key 约定**（`OssService`）：
- 用户上传 / 生成 PDF 的 OSS key 结构必须是 `目录/日期/<UUID>/原始文件名.ext`，**UUID 只放目录层，禁止拼进文件名**
- 目的：URL 尾段 = 清洗后的原始文件名 → 浏览器预览标题 / 下载文件名显示原始名，**任何用户可见位置不出现 UUID**
- 原始文件名用 `sanitizeKeyName()` 清洗（去掉 `/ \ : * ? " < > |` 和空白，中文保留），同时用 UUID 目录保证唯一、同名不覆盖

## B.三、数据库规范

**命名**：表名/字段名 小写+下划线（`tenant`、`user_role`、`tenant_id`）；索引 `uk_xxx` / `idx_xxx`；字符集 `utf8mb4` 引擎 `InnoDB`。

**必备字段**：`tenant_id` `create_time` `update_time` `is_deleted`（每表）；`create_by` `update_by`（用户相关表）。

**主键**：`BIGINT AUTO_INCREMENT`，Java 用 `Long`。

**关键规则**：禁止 `SELECT *` | 逻辑删除用 `is_deleted` | 禁止物理 DELETE | 删除前检查关联数据。

> ⚠️ **禁止直接修改 `sql/gsc.sql`**：DDL 变更（增删字段/表/索引）一律提供 SQL 语句给用户手动执行，不要改 gsc.sql 文件。

**禁止使用外键约束**：表间关联通过应用层逻辑维护，数据库层不使用 `FOREIGN KEY`。所有外键字段只定义列+索引，不加外键约束。

## B.四、Mapper 规范

每个 Mapper 方法标注 Javadoc，所有参数加 `@Param`。XML 文件放 `gsc-module-system/src/main/resources/mapper/`。

| 规则 | 说明 |
|------|------|
| 所有查询带 `is_deleted = 0` | 逻辑删除过滤 |
| UPDATE/DELETE 带 `AND is_deleted = 0` | 防止误改已删数据 |
| 禁止 `SELECT *` | 显式列出字段 |
| 动态条件用 `<if>` | 不用 LambdaQueryWrapper |
| `useGeneratedKeys="true"` | INSERT 返回自增主键 |
| 大小写敏感 | 登录等精确匹配用 `BINARY username = #{username}` |

## B.五、Java 代码规范

### Controller

```java
@RestController @RequestMapping("/api/admin") @RequiredArgsConstructor
@Tag(name = "用户管理")
public class UserController {
    private final UserService userService;  // 只注入 Service，严禁注入 Mapper

    /** 分页查询用户列表 */
    @GetMapping("/users") @Operation(summary = "用户分页")
    public R<Map<String, Object>> list(...) {
        return R.ok(userService.listUsers(...));  // 委托 Service，不写业务逻辑
    }
}
```

| 规则 | 说明 |
|------|------|
| 只注入 Service | 禁止注入 Mapper |
| 统一返回 `R<T>` | 入参用 DTO + `@Valid`，禁止 `@RequestBody Map` |
| 不写业务逻辑 | 树构建、数据转换、错误判断 → 下沉到 Service |
| 不 try-catch | 异常由 GlobalExceptionHandler 统一处理 |
| `@AuditLog` operation 只用基本 CRUD | `CREATE` / `UPDATE` / `DELETE`，具体操作含义写在 `desc` 里（如"分配事务所"、"审核入驻申请"），禁止自定义 `ASSIGN_FIRM`、`REVIEW` 等类型 |

### Service

```java
@Service @RequiredArgsConstructor
public class XxxServiceImpl implements XxxService {
    @Override @Transactional(rollbackFor = Exception.class)
    public void create(CreateDTO dto) {
        // 1. 校验 → 2. 转换 → 3. 入库 → 4. 返回
    }
}
```

| 规则 | 说明 |
|------|------|
| `@Transactional(rollbackFor = Exception.class)` | rollbackFor 不可省略 |
| `BizException(ErrorCode.xxx)` | 禁止裸 int 码，禁止 `RuntimeException` |
| 步骤注释 | 复杂逻辑写 `// 1. 校验 → 2. 转换 → 3. 入库 → 4. 返回` |
| `@Service` 不用 `@Component` | 接口+实现分离 |
| 单方法接口不单独建文件 | 合并到已有 Service（如 `AuthServiceImpl` 实现 `PermissionProvider`）|

### DTO/VO/Entity

- **Entity**：映射数据库表，纯数据，不暴露给 Controller
- **DTO**：Controller 入参/出参
- **VO**：前端展示用
- Controller 返回 `R<XxxVO>`，禁止返回 `R<Menu>`、`R<Tenant>` 等 Entity

## B.六、多租户规范

平台级表（不受租户隔离）：`tenant` `permission` `menu` `audit_log`。

```java
Long tenantId = TenantContextHolder.getTenantId();  // 获取当前租户
```

超管 `tenant_id = 1`（平台租户）。

## B.七、异常处理规范

```
GlobalExceptionHandler（所有异常唯一出口）
├─ BizException                → HTTP 400 + R.fail(code, msg)
├─ AccessDeniedException       → HTTP 403 + "无权限访问"
├─ MethodArgumentNotValidException → HTTP 400 + 第一个字段错误
├─ IOException                 → HTTP 500 + e.getMessage()
└─ Exception（兜底）           → HTTP 500 + "服务器内部错误"
```

**强制规则**：

| 规则 | 正确 | 错误 |
|------|------|------|
| 用 ErrorCode 枚举 | `throw new BizException(ErrorCode.USERNAME_EXISTS)` | `throw new BizException(400, "xxx")` |
| 不用 RuntimeException | `throw new BizException(ErrorCode.INTERNAL_ERROR)` | `throw new RuntimeException("xxx")` |
| 日志带上下文 | `log.error("审核失败, id={}", id, e)` | `log.error(e.getMessage())` |
| 不泄露内部信息 | 返回 "服务器内部错误" | 返回 endpoint / 堆栈 |

**ErrorCode 编码**：1xxx 通用 | 2xxx 认证 | 3xxx 租户 | 4xxx 用户 | 5xxx 角色 | 6xxx 入驻 | 7xxx 订阅 | 8xxx AI护照 | 9xxx 菜单权限

## B.八、Git 提交规范

```
feat: / fix: / refactor: / docs: / style: / test:
```

## B.九、启动

```bash
cd srm-admin-backend && mvn clean install -DskipTests
# 运行 SystemApplication.java（spring.profiles.active: dev）
```

## B.十、代码质量

### 写操作 + 防御 + 可复用

| 规则 | 说明 |
|------|------|
| `@Transactional(rollbackFor = Exception.class)` | 所有 INSERT/UPDATE/DELETE |
| 防御性编程 | 查前判空，删前校验（如系统角色不可删） |
| 通用逻辑抽 Service | 多处复用的查询/校验放 Service |
| 工具放 common | 字符串、日期、加密 |
| 枚举统一管理 | 状态码放 `gsc-common/enums/` |

### 禁止循环依赖

```
❌ SecurityConfig → PermissionProvider(AuthServiceImpl) → PasswordEncoder(SecurityConfig)
✅ SecurityBeanConfig(PasswordEncoder) ← AuthServiceImpl
   SecurityConfig → PermissionProvider → AuthServiceImpl
```
共享 Bean 拆到独立 `@Configuration` 类（如 `SecurityBeanConfig`）。

### 代码可读性

- 每个方法写 Javadoc（`@param` `@return`）
- **每一行代码都要有注释**：解释做什么、为什么
- 复杂逻辑分步骤：`// 1. 校验 → 2. 转换 → 3. 入库 → 4. 返回`
- 异常日志带上下文：`log.error("xxx失败, id={}", id, e)`

### 禁止全限定类名

**代码中只能使用 import 导入后的短类名，禁止在方法签名或方法体中使用全限定类名。**

```java
// ✅ 正确：import 后直接用短类名
import cn.greensmartplanet.gsc.system.entity.DueDiligence;
import java.util.List;

void saveOrUpdate(Long id, DueDiligence dd);
List<DueDiligenceVO> getFirmWorkbench(Long firmId);
```

```java
// ❌ 错误：全限定类名
void saveOrUpdate(Long id, cn.greensmartplanet.gsc.system.entity.DueDiligence dd);
java.util.List<cn.greensmartplanet.gsc.system.vo.DueDiligenceVO> getFirmWorkbench(Long firmId);
```

| 规则 | 说明 |
|------|------|
| `import` 具体类 | 禁止 `import cn.greensmartplanet.gsc.system.entity.*` 等通配符导入（`import java.util.*` 同样禁止） |
| 不用全限定名 | 类名只在 import 语句中出现全路径，代码中用短名 |

## B.十一、安全规范

| 规则 | 说明 |
|------|------|
| 禁止硬编码密钥 | JWT secret、AK/SK 通过 `${ENV_VAR:}` 注入，不留默认值 |
| 不泄露堆栈 | 兜底异常返回 "服务器内部错误"，不暴露 `e.getMessage()` |
| CORS 白名单 | 生产环境指定具体域名，禁用 `*` + `allowCredentials(true)` |
| 用户状态实时校验 | 禁用/锁定用户即使 JWT 未过期也立即拒绝 |

---

# Part C: 全栈通用规范

## C.一、环境相关 URL 统一放配置文件

**禁止在 Java 代码中硬编码 URL 默认值。** 所有区分本地/生产的 URL 必须在 `application-{profile}.yml` 或 `.env` 文件中配置：

```yaml
# 后端 application-dev.yml
gsc:
  login-url: http://localhost:5173/login
  website-url: http://localhost:5174/join
```

```java
// ✅ 正确：不带默认值
@Value("${gsc.login-url}")
private String loginUrl;

// ❌ 错误：硬编码默认值
@Value("${gsc.login-url:http://localhost:5173/login}")
private String loginUrl;
```

```js
// ✅ 前端：用 import.meta.env
const API_BASE = import.meta.env.VITE_API_BASE

// ❌ 错误：硬编码 fallback
const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080'
```

## C.二、优先使用 Stream / 函数式数组方法

集合/数组的过滤、映射、转换、分组等操作优先用声明式方法，不要手写 for 循环：

```java
// ✅ Java Stream
List<ApplicationVO> records = pageRecords.stream()
        .map(ApplicationVO::fromEntity)
        .toList();
```

```js
// ✅ JS filter/map/reduce
const activeUsers = users.filter(u => u.status === 'active')
const ids = users.map(u => u.id)
```

```java
// ❌ 手写 for 循环
List<ApplicationVO> records = new ArrayList<>();
for (Application a : pageRecords) {
    records.add(ApplicationVO.fromEntity(a));
}
```

**例外**：Redis 批量操作等纯副作用场景用 `forEach` 即可。

## C.三、Setter 操作使用建造者模式

创建对象并设置多个字段（5个以上）时，优先用 Lombok `@Builder`：

```java
// ✅ Builder 模式
User admin = User.builder()
        .tenantId(tenantId)
        .username(username)
        .passwordHash(passwordEncoder.encode(password))
        .realName(app.getContactName())
        .email(app.getContactEmail())
        .status("active")
        .build();
```

```java
// ❌ 逐行 Setter
User admin = new User();
admin.setTenantId(tenantId);
admin.setUsername(username);
admin.setPasswordHash(...);
admin.setRealName(...);
admin.setEmail(...);
admin.setStatus("active");
```

**Entity 需加 `@Builder` + `@NoArgsConstructor` + `@AllArgsConstructor`**（MyBatis-Plus 要求无参构造）。

## C.四、多表数据兜底在 Service 层实现，禁止裸 SQL 跨表修复

**关联表的字段缺失问题必须在后端 Service 层处理，不允许直接写 SQL UPDATE/JOIN 修数据。**

当 1:1 关联的两张表存在重叠字段时（如 `due_diligence` 与 `application`），查询时应遵循"新表优先、旧表兜底"原则：

```java
// ✅ 正确：Service 层惰性查第二张表做兜底
private void fillFromApplication(DueDiligence dd) {
    boolean needFallback = dd.getApplicantRole() == null
            || dd.getCompanyName() == null /* ... 其他重叠字段 */;
    if (!needFallback) return;  // 大部分情况下不需要查第二张表

    Application app = applicationMapper.selectById(dd.getApplicationId());
    if (app == null) return;
    if (dd.getApplicantRole() == null) dd.setApplicantRole(app.getApplicantRole());
    if (dd.getCompanyName() == null) dd.setCompanyName(app.getCompanyName());
    // ... 其他重叠字段同理
}
```

```sql
-- ❌ 错误：用数据库 SQL 直接跨表 UPDATE 修数据
UPDATE due_diligence dd JOIN application a ON dd.application_id = a.id
SET dd.applicant_role = a.applicant_role WHERE dd.applicant_role IS NULL;
```

**规则要点**：
- 数据兜底逻辑属于业务规则，必须在 Java 代码中体现，不藏在 SQL 里
- 惰性查第二张表（先判断是否需要兜底），避免每次都多表 JOIN 影响性能
- 数据迁移 SQL（如 `migration-due-diligence.sql`）是一次性 DDL/数据搬迁操作，不在此限制内

---

# Part D: 前后端联调（项目代码位置与地址）

## D.一、项目位置总览

三个项目分别在两个父目录下：

```
Global SCM _backend/           # 后台管理系统（管理后台 + 后端 API）
├── srm-admin-frontend/        # 后台管理前端（Vue 3，当前项目）
└── srm-admin-backend/         # 后端 API 服务（Spring Boot）

Global SCM_fontend/            # 前台官网
└── srm-official-website/      # 官网前端（Vue 3，面向公众展示）
```

联调时需要查后端接口、DTO、Controller 时去 `srm-admin-backend` 目录读，不用再问。

## D.二、后端服务地址

| 环境 | 地址 | 说明 |
|------|------|------|
| 本地开发 | `http://localhost:8080` | 前端 `vite.config.js` 代理 `/api → localhost:8080` |
| 生产 | `https://admin.greensmartplanet.cn` | 后端 `application-prod.yml` 配置 |

**Swagger 接口文档（本地）**：`http://localhost:8080/swagger-ui.html`（Knife4j 中文界面）

## D.三、接口路径前缀约定

后端 Controller 的 `@RequestMapping` 前缀，对接时按此拼接：

| 前缀 | 用途 | 示例 Controller |
|------|------|-----------------|
| `/api/auth` | 认证（登录、刷新、忘记密码） | `AuthController` |
| `/api/admin` | 后台管理（用户、角色、租户、审核、文件、日志） | `UserController`、`RoleController`、`TenantController` |
| `/api/admin/menu-manage` | 菜单管理 | `MenuController` |
| `/api/passport` | AI 护照 | `PassportController` |
| `/api`（无子前缀） | 公开接口（入驻申请等，无需登录） | `PublicApplicationController` |

> 前端 `request.js` 的 `baseURL` 已是 `/api`，所以 API 文件里写路径时**不要重复带 `/api`**。
> 例：后端 `@RequestMapping("/api/admin")` + `@GetMapping("/users")` → 前端写 `request.get('/admin/users')`。

## D.四、后端环境配置速查

- 配置文件：`gsc-module-system/src/main/resources/application-dev.yml`（本地）、`application-prod.yml`（生产）
- 本地 DB / Redis：`120.26.30.114`（dev 环境用的是远程测试库，非 localhost）
- 启动类：`gsc-module-system` 下的 `SystemApplication.java`，默认 `spring.profiles.active: dev`
- 启动命令：`cd srm-admin-backend && mvn clean install -DskipTests` 后运行 `SystemApplication`

## D.五、前台官网（srm-official-website）

**本地路径**：`C:\Users\Administrator\Desktop\Global SCM_fontend\srm-official-website`

面向公众展示的官网项目，与技术文档/博客无关，纯前端展示。

| 项目 | 技术栈 | 本地端口 |
|------|--------|----------|
| 前台官网 | Vue 3 + Nuxt + Tailwind CSS | 默认 `http://localhost:5174` |

```bash
cd /c/Users/Administrator/Desktop/Global\ SCM_fontend/srm-official-website
npm install
npm run dev    # 启动在 localhost:5174
npm run build  # 生产构建
```

---

# Part E: 防御性编程与全局异常规范

> **面向 Vanpine（10年全球供应链资深开发工程师）的编码标准。** 所有代码必须遵循本规范。

## E.一、异常处理铁律

### 后端三层分工

| 层 | 职责 | 禁做 |
|------|------|------|
| **Controller** | 接收请求、调 Service、返回 `R<T>` | ❌ 不写业务逻辑、不 try-catch |
| **Service** | 校验 → 转换 → 入库 → 返回 | ❌ 不吞异常不处理、不让异常泄漏内部信息 |
| **GlobalExceptionHandler** | 全局异常 → 统一 JSON 响应 | ❌ 唯一异常出口，Service 不要再 catch 后转义 |

### Service 层异常处理原则

```java
// ✅ 正确：防御式校验 + 抛业务异常，由全局处理器统一返回
@Override
@Transactional(rollbackFor = Exception.class)
public void assignFirm(Long applicationId, AssignFirmDTO dto) {
    Application app = applicationMapper.selectById(applicationId);
    if (app == null) {
        throw new BizException(ErrorCode.APPLICATION_NOT_FOUND);  // → 400 + 友好提示
    }
    // ... 正常业务逻辑，不 try-catch
}

// ❌ 错误：自己 catch 后返回 null 或吞掉异常
public void badExample() {
    try {
        doSomething();
    } catch (Exception e) {
        log.error("出错了");  // 前端不知道发生了什么
        return;                // 事务可能没回滚
    }
}
```

### 允许 try-catch 的场景（少数例外）

1. **旁路非关键操作**：如发邮件、推第三方，失败不影响主流程
   ```java
   try {
       mailService.sendFirmCredentials(...);
   } catch (Exception e) {
       log.warn("邮件发送失败, firmId={}", firmId, e);  // 不抛，主流程继续
   }
   ```

2. **外部 API 调用**：外部不可控，需兜底返回空/降级数据
   ```java
   public List<FirmInfo> search(String keyword) {
       try {
           // HTTP 调用
       } catch (Exception e) {
           log.error("事务所查询异常", e);
           return Collections.emptyList();  // 降级，不阻塞业务流程
       }
   }
   ```

### 全局异常处理器已覆盖的类型

| 异常类型 | HTTP 状态 | 前端看到 |
|----------|:--:|------|
| `BizException` | 400 | `{"code": xxx, "message": "具体业务错误"}` |
| `DuplicateKeyException` | 400 | `"该用户名已被使用"` / `"该邮箱已被注册"` |
| `DataIntegrityViolationException` | 400 | `"数据操作失败，请检查输入信息"` |
| `MethodArgumentNotValidException` | 400 | 第一个校验失败的字段提示 |
| `AccessDeniedException` | 403 | `"无权限访问"` |
| `IOException` | 500 | 具体 IO 错误信息 |
| `Exception`（兜底） | 500 | `"服务器内部错误"` |

> **Service 新增业务逻辑时，只抛 `BizException(ErrorCode.xxx)`，剩下的交给全局处理器。**

## E.二、前端错误处理

### API 调用规范

```javascript
// ✅ 正确：成功自己弹，失败靠拦截器
async function handleSubmit() {
  try {
    await assignFirm(id, data)
    ElMessage.success('分配成功')
    emit('success')
  } catch {
    // 拦截器已弹错误，这里只做状态恢复
  } finally {
    submitting.value = false
  }
}

// ❌ 错误：catch 块里弹错误
async function badSubmit() {
  try {
    await assignFirm(id, data)
  } catch (e) {
    ElMessage.error(e.message)  // 拦截器已弹，双重弹窗！
  }
}
```

### 防御性数据访问

```javascript
// ✅ 所有来自 API 的数据使用前判空
const firmName = row?.ddFirmName || '未分配'
const list = data?.records || []

// ❌ 直接访问，可能 undefined 报错
const firmName = row.ddFirmName.length > 0 ? row.ddFirmName : '未分配'
```

## E.三、新增接口检查清单

每次新增后端接口，确认以下几点：

- [ ] Controller 只做参数接收和委托，不写业务逻辑
- [ ] Service 异常情况抛 `BizException(ErrorCode.xxx)`，不 return null
- [ ] 外部 API 调用有 try-catch 兜底，失败不阻塞主流程
- [ ] 写操作有 `@Transactional(rollbackFor = Exception.class)`
- [ ] 前端 catch 块只做状态恢复，不弹错误提示
- [ ] 前端数据访问有 `?.` 或 `||` 兜底
