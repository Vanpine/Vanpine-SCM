# 全球供应链 AI 赋能平台后台管理系统

> 🚀 新会话先读 `PROJECT_OVERVIEW.md`（3 分钟速览）→ 本文件（核心规则）→ `CODING_STANDARDS.md`（详细规范，按需查阅）

---

## 🚫 Git 禁令

**永远不要自动执行 `git commit`。** 代码写完后告知用户改了哪些文件，由用户自行提交。

**禁止 `git checkout` / `git restore` / `git reset --hard` 等回退操作。** 文件误删时先告知用户，让用户自己决定如何恢复。未经确认不得丢弃任何未提交的修改。

---

## CodeGraph 优先

**问函数、架构、代码关系、调用链时，必须先用 CodeGraph，禁止直接 grep/Read：**

| 场景 | 做法 |
|------|------|
| 问"XX 函数怎么工作" | `codegraph_explore` 查该函数，拿源代码 + 调用方 + 影响范围 |
| 问"项目架构" | `codegraph_explore` 查核心符号（router/stores/request/Controller/Service），看 blast radius |
| 问"XX 和 YY 的关系" | `codegraph_explore` 同时查两个符号，CodeGraph 返回调用路径 |
| 修改代码前 | `codegraph_explore` 查要改的符号，看 blast radius 确认影响范围 |

- **前后端都建了 CodeGraph 索引**：前端项目路径 `srm-admin-frontend`，后端项目路径 `srm-admin-backend`
- **脑暴/规划/实现必须全栈**：涉及业务功能时，必须分别查前端和后端两个 CodeGraph 索引（通过 `projectPath` 切换），结合两端现有代码给出全栈建议，不能只看前端
- CodeGraph 返回的是**当前磁盘上的真实代码**，等价于 Read，不要再重复打开
- 一个 `codegraph_explore` 调用通常就能回答整个问题，远比 grep + Read 循环高效

---

## 设计原则

1. **高内聚低耦合**：前端按业务分文件夹，后端 common → framework → 业务服务（platform/trade/delivery/supplier）单向依赖
2. **可复用优先**：通用逻辑抽取到公共层，避免重复
3. **高可用**：防御性编程，查前判空、删前校验，统一错误处理
4. **文档简洁**：注释解释"为什么"而非"做什么"

## 分阶段验收

**多步骤任务必须分阶段执行，禁止一次性做完。**

| 规则 | 说明 |
|------|------|
| 阶段粒度 | 每阶段 2-5 个文件，可独立验证 |
| 先计划后执行 | 列出阶段+涉及文件，用户确认后再动手 |
| 阶段结束=停下来 | 告知"Phase X 完成，验证通过后告诉我" |
| 禁止跳阶段 | 用户确认后才能开始下一阶段 |
| SQL 独立提供 | DDL 不写进 `gsc.sql`，直接给 SQL |
| 高风险优先 | 删除代码/字段的阶段排前面 |

## 四步工作流

**非简单任务（>50 行或多文件）必须按顺序执行，禁止跳过或并行：**

| # | Skill | 用途 |
|:--:|------|------|
| 1 | `superpowers:brainstorming` | 头脑风暴 — 理解需求、探索方案 |
| 2 | `planning-with-files:planning-with-files-zh` | 文件规划 — task_plan.md / findings.md / progress.md |
| 3 | `superpowers:test-driven-development` | TDD — Red → Green → Refactor |
| 4 | `superpowers:systematic-debugging` | 系统化调试 — 根因追踪，不跳过直接修 |

### 规划文件位置（重要）

**所有规划文件、设计文档、预留方案一律放 `.plan/` 目录，禁止在项目根目录乱生成文件。**

- `task_plan.md` / `findings.md` / `progress.md` → `.plan/`（不是项目根）
- 设计文档（design）→ `.plan/`，命名 `YYYY-MM-DD-主题-design.md`
- 预留方案（暂不实施的）→ `.plan/`，命名 `YYYY-MM-DD-主题-预留.md`
- 后端相关的规划/设计放后端项目 `.plan/`（`srm-admin-backend/.plan/`），前端相关的放前端 `.plan/`（`srm-admin-frontend/.plan/`）

**不要**在项目根目录、`docs/`、`plan/` 等位置散落规划文件。除非用户明确指定位置。

---

## 技术栈

| | 前端 | 后端 |
|------|------|------|
| 框架 | Vue 3 + Element Plus + Pinia + Vue Router 4 + Vite + Tailwind CSS 4 | Spring Boot 3.3.7 + Spring Cloud 微服务（Nacos 注册/配置）+ MyBatis-Plus 3.5.7 |
| 语言 | JavaScript（不用 TS） | Java 17+ |
| 包管理 | npm | Maven |
| 端口 | 5173 | 网关 8080（platform 8081 / supplier 8082 / trade 8083 / delivery 8084） |
| 主键 | — | 雪花算法（IdType.ASSIGN_ID，后端 Long→String 序列化，前端拿到的 id 是字符串） |
| 中间件 | — | MySQL / Redis / RabbitMQ / Zipkin（链路追踪） |

---

## 前端速查

### 列表页模板

```
search-card（搜索表单）→ content-card（table-toolbar + el-table + pagination-wrap）
```

- 双卡片结构，搜索和表格**不能**挤同一张卡片
- `search-card` `content-card` `table-toolbar` `pagination-wrap` 是全局样式类，**不要**在 scoped 里重复写
- 页面最外层用 `<div>` 不带 class，不用 `page-container`
- 空状态：`<el-empty v-if="!loading && list.length === 0" :description="keyword ? '未找到匹配的XXX' : '暂无XXX'" />`

### 表格列宽

| 类型 | 属性 | 示例 |
|------|------|------|
| 文本列 | `min-width="N"` | `min-width="300" show-overflow-tooltip` |
| 固定列 | `width="N"` | `type="index" width="55"` |
| 时间列 | `min-width="120"` | YYYY-MM-DD HH:mm |
| 时间列(秒) | `min-width="160"` | YYYY-MM-DD HH:mm:ss |

### 搜索框

- label 准确（多字段用"关键词"），placeholder 列出可搜字段用 `/` 分隔，width 220-300px
- 必须绑 `@keyup.enter="handleSearch"`

### 操作列 ⋮ 下拉

```vue
<el-dropdown trigger="click" @command="(cmd) => handleCmd(cmd, row)">
  <el-icon :size="16" style="cursor:pointer;color:#409eff;margin-left:4px;margin-top:5px;transform:rotate(90deg)">
    <MoreFilled />
  </el-icon>
  <template #dropdown>
    <el-dropdown-menu>
      <el-dropdown-item command="view">详情</el-dropdown-item>
      <el-dropdown-item command="delete" divided style="color:#f56c6c">删除</el-dropdown-item>
    </el-dropdown-menu>
  </template>
</el-dropdown>
```

≤2 个操作直接平铺，≥3 个保留最重要 1 个外露其余收进 `⋮`，危险操作放最后 `divided` + 红色。

### Element Plus 样式覆盖

**不用 `:deep()` 穿透，用非 scoped `<style>` 块 + 命名空间 class + `!important`：**

```vue
<style>
.firm-right .el-descriptions__label { width: 120px !important; }
</style>
```

### 确认弹窗

```js
await ElMessageBox.confirm(
  `<div style="white-space:nowrap;margin-bottom:4px">确认操作 <span class="highlight-name">${名称}</span> 吗？</div><div>后果说明。</div>`,
  '标题',
  { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning',
    dangerouslyUseHTMLString: true, customClass: 'confirmDdMsgBox' }
)
```

- `？` 前不换行，目标名用 `<span class="highlight-name">` 高亮，不用 `「」` `【】`
- `customClass: 'confirmDdMsgBox'`（全局 CSS：`width: auto !important; max-width: 520px;`）
- `try { await ElMessageBox.confirm(...) } catch { return }`

### Dialog 弹窗

```vue
<el-dialog v-model="visible" title="标题" :close-on-click-modal="false" class="xxx-dialog">
  <el-form label-width="85px" label-position="right">...</el-form>
  <template #footer>
    <el-button @click="visible = false">取消</el-button>
    <el-button type="primary" :loading="submitting" @click="handleSubmit">确认</el-button>
  </template>
</el-dialog>
```

非 scoped CSS：`width: auto !important; max-width: 520px;` | label 4字中文 required→85px，非 required→80px

### 错误处理

- **拦截器是唯一错误弹窗源**：`code !== 200` / HTTP 400/403/500 → 拦截器弹 `ElMessage.error`
- **组件**：成功 → 组件弹 `ElMessage.success()`；失败 → catch 只做状态恢复，不弹错误
- **防御性访问**：`row?.firmName || '未分配'`，`data?.records || []`

### 前后端对接

- 响应：`{ code: 200, message: "", data: {...} }`，`code !== 200` 拦截器自动报错；失败响应带 `traceId`（报障时贴给开发，Zipkin/日志可定位整条调用链）
- 分页：`{ records: [], total: N, page: 1, pageSize: 20 }`
- Token：accessToken 2h + refreshToken 7d，401→刷新→失败→清空→`/login`
- 路径：后端 `/api/auth` `/api/admin` `/api/passport`；前端 baseURL 已是 `/api`，**不要重复带**
- **id 是字符串**：后端 Long 全局序列化为 String（雪花主键超出 JS 精度），比较必须用 `String()` 双端兼容（兼容 localStorage 里旧的数字值）

---

## 前端禁止

- ❌ `console.log` 提交到代码库（调试完就删）
- ❌ `document.querySelector` 等 DOM 直接操作，用 Vue 响应式
- ❌ scoped 里 `@import` / `@keyframes`
- ❌ 弹窗文案用 `「」` `【】`
- ❌ 写死路由路径字符串，用 `{ name: 'Dashboard' }`
- ❌ API 错误在组件 catch 里重复弹窗
- ❌ 自创 `search-form` `page-container` 等临时 class

## 后端禁止

- ❌ 硬编码 URL 默认值（用 `@Value` 不带 fallback）
- ❌ 裸 int 错误码（用 `ErrorCode` 枚举）
- ❌ `RuntimeException`（用 `BizException`）
- ❌ 全限定类名（import 后用短名）
- ❌ 通配符 import（`import ...entity.*`）
- ❌ `SELECT *` / 物理 DELETE / 外键约束
- ❌ Controller 写业务逻辑 / try-catch

## 后端强制：微服务通信铁律

**异步通知走 MQ、跨组件通信走 Feign（详见后端 CLAUDE.md 7.0 节）：**

| 场景 | 必须用 | 禁止 |
|------|--------|------|
| 跨服务查数据/校验/状态回写 | OpenFeign（gsc-api 契约） | 直查他域表、RestTemplate |
| 异步通知/解耦任务（通知、审计、事务提交后的动作） | RabbitMQ（可自产自销） | @Async 本地线程池 |

- 跨服务传输一律契约 DTO（禁止 `Map<String, Object>`）；查询不存在返回 `data=null`（HTTP 200），HTTP 错误仅代表服务故障
- MQ 消费者三件套：不信任载荷（重查校验业务状态）/ 业务键幂等 / 失败重试 3 次进 DLQ
- 事务提交后发布：`@TransactionalEventListener(AFTER_COMMIT)` 发消息

## 后端强制：所有写操作必须加 @AuditLog

**增/删/改方法一律打 `@AuditLog`，查询（GET）不需要。** 登录/登出等非业务数据操作**不记** `@AuditLog`。

```java
// ✅ 写操作：必须加
@AuditLog(module = "询盘管理", operation = "CREATE", desc = "创建询盘", targetType = "Inquiry")
@PostMapping("/inquiry")
public R<InquiryVO> createInquiry(...) { }

// ❌ 查询：不需要
@GetMapping("/inquiry")
public R<...> listInquiries(...) { }
```

**参数（全部必填）：**

| 参数 | 说明 | 示例 |
|------|------|------|
| `module` | 操作模块 | `"询盘管理"` / `"用户管理"` |
| `operation` | 操作类型，**仅限固定三选一，禁止自定义**：新增=`CREATE`，修改/状态流转=`UPDATE`，删除=`DELETE` | `"CREATE"` |
| `desc` | 操作描述 | `"创建询盘"` |
| `targetType` | 目标实体 | `"Inquiry"` / `"Quote"` / `"Contract"` / `"User"` |

**已覆盖 14 个模块**：询盘/报价/合同/需求/对接/画像/用户/角色/菜单/租户/入驻/尽调/认证/文件

---

## 项目位置

```
Global SCM _backend/
├── srm-admin-frontend/   # 当前（管理后台）
└── srm-admin-backend/    # 后端 API

Global SCM_fontend/
└── srm-official-website/ # 前台官网（Nuxt，端口 5174）
```

## 启动

```bash
# 前端
cd srm-admin-frontend && npm install && npm run dev       # → localhost:5173

# 后端（网关 8080 + 4 业务服务 8081-8084，启动类见 README「启动顺序」）
cd srm-admin-backend && mvn clean install -DskipTests
# 依次运行 5 个启动类（spring.profiles.active: dev）：
#   GscGatewayApplication → GscPlatformApplication → GscSupplierApplication
#   → GscTradeApplication → GscDeliveryApplication
# 中间件：Nacos 120.77.169.224:8848，MySQL/Redis/RabbitMQ 120.26.30.114
```

## 详细规范

| 内容 | 查阅 |
|------|------|
| 前端全量（目录/组件/API/Store/路由/样式/质量/留言列/嵌套菜单） | `CODING_STANDARDS.md` Part A |
| 后端全量（模块/数据库/Mapper/Java/多租户/异常/安全） | `CODING_STANDARDS.md` Part B |
| 全栈通用（配置/Stream/Builder/多表兜底） | `CODING_STANDARDS.md` Part C |
| 联调（地址/路径前缀/环境配置/官网） | `CODING_STANDARDS.md` Part D |
| 防御性编程（异常铁律/接口检查清单） | `CODING_STANDARDS.md` Part E |
