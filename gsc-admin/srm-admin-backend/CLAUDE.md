# CLAUDE.md — gsc-backend 项目规范

## 0. Quick Reference

### 常用命令

| 场景 | 命令 |
|------|------|
| 全量编译 | `mvn -DskipTests compile` |
| 编译指定服务 | `mvn -pl gsc-service-trade -am -DskipTests compile` |
| 全量测试 | `mvn test` |
| 指定服务测试 | `mvn -pl gsc-service-trade test` |
| 打包 | `mvn -DskipTests package` |

### 开发环境

| 依赖 | 地址 |
|------|------|
| Nacos | `http://120.77.169.224:8848/nacos`，namespace: `dev` |
| MySQL | `120.26.30.114:3306` |
| Redis | `120.26.30.114:6379` |
| RabbitMQ | `120.26.30.114:5672`，管理台 `http://120.26.30.114:15672` |
| Zipkin | `http://120.26.30.114:9411` |
| AI Service | 默认 `http://127.0.0.1:8100`，联调时用 `AI_SERVICE_URL` 覆盖 |

服务端口：gateway `18080`（nginx 对外 8080），platform `3081`，supplier `3082`，trade `3083`，delivery `3084`。
凭据一律从环境变量/Nacos 注入，不要新增明文密码。
默认使用上述远程开发环境；`deploy/docker-compose.yml` 仅用于离线本地调试，不要在常规任务中默认执行。

### 启动与验证

1. 确认 Nacos 可访问，`dev` namespace 已导入 `gsc-shared.yaml` 和对应服务配置。
2. 按项目 README 顺序启动：gateway → platform → supplier → trade → delivery。
3. 修改 Java 后必须执行对应模块 `compile`；涉及 Mapper/XML 时再执行对应模块 `test`。
4. 修改 Feign 契约时必须同时检查 provider 与 consumer。
5. 修改配置时优先改 Nacos；`application.yml` 只允许端口、服务名、Nacos 引导和兜底配置。

### 常用环境变量

| 变量 | 用途 |
|------|------|
| `NACOS_ADDR` | Nacos 地址，默认已指向远程开发环境 |
| `NACOS_NAMESPACE` | 配置/注册 namespace，开发环境为 `dev` |
| `NACOS_USERNAME` / `NACOS_PASSWORD` | Nacos 凭据 |
| `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_DATABASE` / `MYSQL_USERNAME` | MySQL 连接（jdbc url 拼接） |
| `MYSQL_PASSWORD` | MySQL 密码 |
| `REDIS_PASSWORD` | Redis 密码 |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | RabbitMQ 凭据 |
| `AI_SERVICE_URL` | Python AI 服务地址 |
| `GSC_WORKER_ID` | 雪花 ID worker，多实例必须唯一 |

### 测试策略

- 状态流转、权限校验、授标数量计算等业务规则，优先补 Service 单元测试。
- Feign 依赖用测试替身隔离，测试重点放在调用次数、降级策略和组装结果。
- Mapper/XML 变更优先补集成测试；无法自动验证时必须说明 SQL 手工验证方式和索引结论。
- Bug 修复尽量先补能复现问题的用例，再提交修复。

---

## 一、项目概述
Global SCM 后台管理系统，Spring Boot 3.3.7 + Spring Cloud 2023 + Spring Cloud Alibaba 2023 微服务架构，多租户 SAAS。

- **包名：** `cn.greensmartplanet.gsc`
- **JDK：** 17+ / **Maven：** 3.9+

---

## 二、模块结构
```
gsc-backend/
├── gsc-common/              # 公共层：常量、枚举、异常、统一响应体
├── gsc-framework/           # 框架层：Security、JWT、多租户、AOP、自动装配
├── gsc-gateway/             # 网关：JWT 校验、路由、限流（WebFlux）
├── gsc-service-platform/    # 平台域：用户/角色/菜单/租户/入驻/权限/审计/通知
├── gsc-service-trade/       # 交易域：采购需求/询盘/报价/合同/分期计划
├── gsc-service-delivery/    # 交付域：交付单/物流事件/风险/补救工单
└── gsc-service-supplier/    # 供应商域：尽调/画像/AI 匹配/对接
```

- **依赖方向：** `业务服务 -> framework -> common`（单向，禁止反向）
- **网关独立：** `gsc-gateway` 不依赖 `gsc-framework`（WebFlux 与 Servlet 不兼容）

---

## 三、技术栈

| 分类 | 技术 |
|------|------|
| 注册/配置中心 | Nacos |
| 网关 | Spring Cloud Gateway (WebFlux) |
| 同步调用 | OpenFeign |
| 异步通知 | RabbitMQ（spring-boot-starter-amqp 原生） |
| 链路追踪 | Micrometer Tracing + Brave → Zipkin（日志带 traceId，Feign/MQ 自动透传） |
| 定时任务 | Spring @Scheduled（XXL-JOB 暂缓，备选） |
| 缓存 | Redis |
| 数据库 | MySQL 8 (utf8mb4, InnoDB) |
| ORM | MyBatis-Plus 3.5.7 |
| API 文档 | Knife4j 4.5.0 |

---

## 四、数据库规范

- 表名/字段名：小写 + 下划线，如 `audit_log`、`tenant_id`
- 索引：唯一 `uk_字段名`，普通 `idx_字段名`
- 主键：`BIGINT`，Java 用 `Long`，MyBatis-Plus 注解 `@TableId(type = IdType.ASSIGN_ID)`（雪花算法，由各服务 workerId 发号）
- 每张表必备字段：
```sql
tenant_id     BIGINT     NOT NULL DEFAULT 0
create_time   DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP
update_time   DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
is_deleted    TINYINT(1) NOT NULL DEFAULT 0
```

---

## 五、MyBatis-Plus 规范

### 5.1 核心原则：简单用 MP，复杂写 XML

| 场景 | 用什么 |
|------|--------|
| 单表 CRUD | MP 内置（`selectById` / `insert` / `updateById` / `deleteById`） |
| 单表条件查询 | `LambdaQueryWrapper` |
| 单表分页 | `baseMapper.selectPage(page, wrapper)` |
| 多表关联 / 聚合统计 / 复杂动态条件 | 手写 XML |

#### MP Wrapper 注释规范

1. **`LambdaQueryWrapper` / `LambdaUpdateWrapper` 必须写等价 SQL 或条件说明**
   - 复杂 wrapper、非默认条件、排序、聚合、多表语义时，必须在注释中写出 MP 生成的等价 SQL。
   - 简单单条件查询也必须在方法 Javadoc 或行内注释中写清条件、返回粒度和业务含义。
   - 使用 `selectOne()` 前必须确认业务唯一性；存在多条可能时，应加数据库唯一约束、`last("LIMIT 1")` 或改用 `selectList()`，不能依赖“现在恰好只有一条”。
   - 使用逻辑删除时，注释必须说明 MP 是否会自动追加 `is_deleted = 0`，避免 SQL 理解偏差。

### 5.2 Mapper 写法
```java
@Mapper
public interface UserMapper extends BaseMapper<User> {
    // 简单 CRUD 不用写方法，BaseMapper 全有
    // 复杂查询才写 XML
    List<User> selectPage(@Param("tenantId") Long tenantId, @Param("keyword") String keyword);
}
```

### 5.3 XML 存放位置
每个微服务自己的 `resources/mapper/` 下。

### 5.4 wrapper vs lambdaQuery()

| 场景 | 用什么 |
|------|--------|
| 条件固定查一条 | `lambdaQuery().eq(...).one()` |
| 动态条件（if 拼接） | `LambdaQueryWrapper` |
| 分页 / 批量 / 统计 | `LambdaQueryWrapper` |
| 更新指定字段 | `LambdaUpdateWrapper` |

```java
// 动态条件示例
LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
wrapper.eq(User::getTenantId, tenantId);
if (StringUtils.hasText(keyword)) {
    wrapper.like(User::getUsername, keyword);
}
return baseMapper.selectList(wrapper);
```

### 5.5 Mapper 拆分与 SQL 规范

1. **Mapper 按数据表 / 聚合根划分，不按业务场景拆分**
   - 一个主表对应一个核心 Mapper，例如 `InquiryMapper`、`ContractMapper`、`QuoteMapper`。
   - 明确子表可以独立 Mapper，例如 `InquiryInviteMapper`、`InquiryAwardMapper`、`ContractBatchPlanMapper`。
   - 统计查询可独立只读 Mapper，例如 `TradeStatsMapper`，不需要继承 `BaseMapper`。
   - 禁止按读写、列表、详情、内部契约继续拆出 `XxxReadMapper`、`XxxWriteMapper`、`XxxListMapper`、`XxxInternalMapper`。

2. **Mapper 是持久化边界，不承载业务规则**
   - Mapper 只做数据查询、插入、更新、删除和统计，不做权限校验、状态判断、事务控制。
   - 业务校验、状态流转、资源归属校验必须放在 Service 层。
   - Mapper 方法名必须描述 SQL 语义，例如 `selectActiveByBuyer`、`countByInquiryId`、`updateStatus`，禁止 `doQuery`、`handleData`。

3. **XML 必须显式列字段，禁止 `SELECT *`**
   - 每个实体表在对应 XML 中定义 `<sql id="Base_Column_List">`，查询时使用 `<include>`。
   - 新增字段时必须同步维护 `Base_Column_List` 和 `resultMap`。
   - 只需要少量字段时优先定义专用投影，而不是复用全量列。

4. **聚合/联表查询默认返回投影 DTO，`Map<String, Object>` 仅限例外场景**
   - Group By、Count、Join、报表等查询**默认**定义投影 DTO（如 `QuoteCountRow`、`InviteStatusCountRow`），统一放当前域 `projection` 包，字段名与 SQL 别名一致。
   - 允许 `Map` 的例外：字段结构动态（如透视报表/动态维度，无法用固定 DTO 表达）或仅作方法内部临时载体（不跨层、不返回给 Controller/前端/跨服务）。
   - 例外场景的 Map **不得作为 Controller 或跨服务返回值**；跨服务批量传输允许 `Map<Long, XxxDTO>`（key 为业务 ID，见 7.1 铁律 2）。
   - 历史返回 `Map<String, Object>` 的方法在修改时必须同步 DTO 化，禁止在 Service 层新增 `(Number) row.get(...)` 强转。

#### 投影 DTO（Projection Row）完整规范

投影 DTO 是 Mapper 聚合/联表查询的**类型安全返回载体**，替代 `Map<String,Object>`（黑箱 + 运行时强转 → 编译期契约）。定义规范：

1. **命名**：`{业务名}{语义}Row`（如 `QuoteCountRow`、`ConnectionCountRow`、`ShipmentPlanRow`），放在当前域 `projection` 包（如 `cn.greensmartplanet.gsc.delivery.projection`）。
2. **字段名与 SQL 别名一致**：XML `AS xxxId` 必须对应 DTO 字段 `xxxId`（驼峰），禁止 `Map.get("xxx")`。
3. **ID 类字段必须加 `@JsonSerialize(using = ToStringSerializer.class)`**：投影里的雪花主键/外键（`xxxId`、`xxxTenantId`）不依赖全局 Long→String 配置，显式钉死为字符串，防止前端精度丢失。
4. **数值字段用真实类型**（`Integer`/`Long`/`BigDecimal`/`LocalDate` 等）：聚合 count、数量、金额等按 SQL 实际类型声明，序列化为 JSON number。
5. **`resultType` 写全限定名**：XML 用 `resultType="cn.xxx.projection.XxxRow"`，不写 `map`/`LinkedHashMap`。
6. **跨域回填字段**（如采购方企业名）作为普通字段放 DTO，由 Service 层批量 Feign 后 `setXxx` 回填。

参考实现（新建照抄结构）：

```java
// projection/CreatableContractRow.java
@Data
public class CreatableContractRow {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contractId;      // 雪花主键 → 字符串，防精度丢失
    private String contractNo;    // 普通字段 → 字符串
    private Integer contractQty;  // 数值字段 → number
    private BigDecimal amount;
    private Long allocatedQty;    // 聚合值，SQL 返回 LONG → number
}
```

```xml
<!-- XML resultType 写全限定名，字段别名与 DTO 字段驼峰一致 -->
<select id="selectCreatableContracts" resultType="cn.xxx.projection.CreatableContractRow">
    SELECT c.id AS contractId, c.contract_no AS contractNo, ... FROM contract c ...
</select>
```

```java
// Mapper 接口声明
List<CreatableContractRow> selectCreatableContracts(@Param("tenantId") Long tenantId);
```

5. **自定义 `insert` 必须与主键策略一致**
   - 实体使用 `@TableId(type = IdType.ASSIGN_ID)` 时，XML `insert` 必须显式写入 `id = #{id}`，由 MP 生成雪花 ID。
   - 实体使用数据库自增时，XML 可使用 `useGeneratedKeys`，实体 ID 不能声明 `ASSIGN_ID`。
   - 修改 Mapper 前必须核对建表 DDL；无法确认时先输出风险，不允许凭猜测改主键生成方式。

6. **查询性能与索引自检**
   - 新增列表、排序、JOIN、EXISTS、统计查询时，必须核对过滤条件、租户条件、排序字段和关联字段是否命中索引。
   - 重点关注 `tenant_id + status + create_time`、外键关联列、软删除列的组合索引。
   - 不允许在 Mapper 层隐式引入慢查询；无法本地验证索引时必须在变更说明中列出索引建议。

---

## 六、多租户规范

- 所有业务 SQL 必须带 `tenant_id`，不能只靠拦截器
- 网关从 JWT 提取 `tenant_id` -> 注入 `X-Tenant-Id` 请求头
- 各服务通过 `TenantContextHolder` 读取租户

---

## 七、微服务规范

- **服务注册：** 每个 `application.yml` 配 Nacos discovery
- **同步调用：** OpenFeign（`@FeignClient`），规范见下方 7.1 Feign 契约规范
- **异步通知：** RabbitMQ 原生（`spring-boot-starter-amqp`：RabbitTemplate 发送 / @RabbitListener 消费，messageId 幂等 + event_id 唯一索引，消费失败重试 3 次进 .dlq 死信）
- **MQ 基础设施：** `RabbitMqInfrastructureConfig`（Converter + RabbitTemplate + 发送确认回调）在 `gsc-framework/mq/`，各服务引 `starter-amqp` 自动获得，禁止重复声明
- **MQ 目录规范：** 各服务的消息收发统一放 `<域>/mq/` 包——发送器在 `mq/producer/`（`XxxPublisher`，RabbitTemplate），消费器在 `mq/consumer/`（`XxxConsumer`，@RabbitListener）；公共载荷在 `gsc-common/event/`；交换机/队列/DLQ 拓扑声明在消费域的 `config/RabbitMqConfig`（只声明业务拓扑，不含基础设施）
- **禁止：** 直接用 `RestTemplate` 调其他微服务
- **网关路由：** `Path` 匹配 -> `lb://服务名` -> Nacos 找实例
- **配置管理：** 公共配置放 Nacos `gsc-shared.yaml`，各服务放 `gsc-xxx.yaml`，本地只留兜底

### 7.0 服务间通信铁律（异步任务按选型、跨组件通信走 Feign）

| 场景 | 必须用 | 禁止 |
|------|--------|------|
| 跨服务查数据 / 校验 / 状态回写 | OpenFeign（gsc-api 契约） | 直查他域表、RestTemplate |
| **异步任务**（通知、审计、事务提交后的后续动作） | 按下方「异步选型」决策 | 自建 `new Thread` / 自建线程池 |
| 用户可见的通知消息 | MQ → platform 消费落库（复用 NotificationService） | 直写 notification 表 |

#### 异步选型：MQ 还是 @Async（视情况而定）

**默认走 MQ**。命中以下**任意一条** → 必须 RabbitMQ（自产自销或跨域发布）：

- **不能丢**：涉及数据一致性（建单、状态回写、跨域落库、生成凭证）
- **跨域解耦**：通知、审计等由其他域消费
- **需要重试 / 死信 / 削峰**：依赖外部不稳定服务（第三方 API、AI 服务），失败要能重试并进 DLQ 排查

**允许 @Async（本地线程池）**。必须**同时**满足：

- **尽力而为、丢了可接受**：旁路增强类（向量同步、统计刷新、埋点、非关键缓存预热）
- **同域内**：无跨服务解耦需求
- **有兜底补偿**：定时对账 / 下次触发自然补齐 / 用户可手动重试
- 任务轻量、对延迟不敏感

选 @Async 时必须在代码注释里写明「为什么这里不用 MQ」（丢任务的影响面 + 兜底手段）。

**⚠️ @Async 三个坑：**

1. **禁止同类自调用**：`this.asyncMethod()` 绕过 Spring AOP 代理，`@Async` 静默失效、退化成同步执行（阻塞调用线程）。必须拆到独立 Bean 注入调用。
2. **异常不外传**：异步方法内的异常不会回到调用方，必须自己 try-catch + log。
3. **线程池统一**：由框架 `AsyncExecutorConfig` 提供默认 `@Async` 线程池
   （`gscAsyncExecutor`，core/max/queue 由 `gsc.async.*` 配置，队列满走 `CallerRunsPolicy` 反压）。
   禁止 `new Thread` / 自建 `ExecutorService`。不配默认池时 Spring 会用 `SimpleAsyncTaskExecutor`
   ——每个任务新建线程、无上限，阻塞型任务（等外部 HTTP）会随并发量堆线程。
   **长任务必须独立成池**：任务时长差一个数量级就不要共用（如 AI 流式对话最长 120s，
   与秒级的压缩/同步混用会互相饿死），且长任务池的拒绝策略要用 `AbortPolicy` 快速失败——
   `CallerRunsPolicy` 会让任务落到 tomcat 请求线程，把请求线程占满。
   参考实现：`AiStreamExecutorConfig`（AI 流式专用池）。

**为什么不一刀切**：一致性关键路径走 MQ，保证不丢 + 可重试 + DLQ 可排查；纯旁路增强用 @Async，避免为一条轻量任务引入 producer + consumer + 拓扑三件套。

**MQ 消费者三件套**（照 ContractConfirmedConsumer 模式）：

1. **不信任载荷**：消费入口必须重查数据校验业务状态（如合同仍为 signed / 交付单仍为 accepted），载荷字段仅作触达依据
2. **幂等**：以业务键查重（contract_id / delivery_id + 状态），MQ 重放不产生重复副作用
3. **失败语义**：消费抛异常 → 队列重试（listener.retry 3 次：10s/30s/60s）→ 仍失败进 DLQ（`.dlq` 后缀队列）

**事务提交后发布模式**：业务方法内写库，`@TransactionalEventListener(AFTER_COMMIT)` 发消息，
消息在事务提交后才发出，避免消费端查到未提交数据。

### 7.1 Feign 契约规范（新增/修改契约必须遵守）

#### 契约存放：统一在 gsc-api 模块

```
gsc-api/src/main/java/cn/greensmartplanet/gsc/api/
├── constant/ServiceNameConstants.java   # 服务名常量，@FeignClient(name) 一律引用
├── {domain}/XxxClient.java              # 契约接口（消费方声明）
├── {domain}/dto/XxxDTO.java             # 契约 DTO（跨服务传输的字段子集）
└── {domain}/factory/XxxClientFallbackFactory.java  # 降级工厂
```

#### 七条铁律

1. **`path` 前缀**：重复路径前缀必须提取到 `@FeignClient(path = "/internal/xxx")`，
   方法注解只写相对路径（如 `@GetMapping("/{id}")`）。
2. **禁止 `Map<String, Object>` 跨服务传值**：契约返回/入参一律有类型 DTO，
   字段由编译期保证一致。批量场景允许 `Map<Long, XxxDTO>`（key 为业务 ID）。
3. **提供方 Controller 独立声明，不 implements 契约接口**：
   契约接口只是消费方的镜像声明；提供方 Controller 自己写（薄壳委托 Service），
   路径一致性靠 Controller 类注释的 `⚠️ 路径一致性：与 gsc-api 的 XxxClient 保持一致` 约定同步修改。
4. **Controller 薄壳化**：internal Controller 只做 `R.ok(xxxService.getXxx())`，
   查询与 Entity→DTO 转换全部在 Service 层，Controller 不碰 Mapper。
5. **查询语义：不存在返回 `data=null`（HTTP 200）**，调用方按 null 走业务分支；
   **HTTP 错误仅代表服务故障**，由 FallbackFactory 接管（GlobalExceptionHandler 的
   BizException 转 HTTP 400，Feign 默认解码器只抛 FeignException 不解析 R 体，
   不能靠错误码传业务语义）。
6. **降级策略二分**：
   - 数据底座性质（校验/初始化/状态回写）→ 抛 BizException 或返回 null/404/false 让调用方拒绝，**不可静默通过**
   - 展示性字段（文案企业名/计数/标注）→ 返回 null/空/0 放行 + warn 日志
   - FallbackFactory 必须记 `log.warn/error` 并携带 cause
7. **服务启动类必须**：`@EnableFeignClients("cn.greensmartplanet.gsc.api")`
   + `@ComponentScan({"本服务包", "cn.greensmartplanet.gsc.api"})` 双包并列
   （显式 @ComponentScan 会覆盖默认扫描，漏列 gsc.api 会导致 FallbackFactory 不注册、
   漏列本包会导致业务 Controller 不注册）。

#### 参考实现（现有契约，写新契约照抄结构）

| 契约 | 提供方 |
|------|--------|
| `ApplicationClient`（platform） | InternalApplicationController |
| `ProfileClient` / `ConnectionClient`（supplier） | InternalProfileController / InternalConnectionController |
| `InquiryClient` / `RequirementClient` / `ContractClient`（trade） | InternalInquiryController / InternalRequirementController / InternalContractController |
| `DeliveryClient`（delivery） | InternalDeliveryController |

#### 公共配置（framework 自动装配，各服务禁止自建）

- Knife4j（`Knife4jAutoConfiguration`）、MyBatis-Plus 分页/自动填充（`MyBatisPlusAutoConfiguration`）、RabbitMQ 基础设施（`RabbitMqInfrastructureConfig`：Converter + RabbitTemplate）
  已下沉 framework，`@MapperScan("服务.mapper")` 写在各服务启动类上。

### 7.2 多实例约束（新增任何功能前必过）

**总则：本项目是微服务，任何设计决策前先问一句「多实例部署时会怎样」。**
单实例下正确 ≠ 多实例下正确。下面五类是已知高发区，命中就必须按规范处理。

#### 7.2.1 定时任务 / 启动初始化 —— 必须加分布式锁

`@Scheduled`、`@PostConstruct`、`CommandLineRunner`、`ApplicationReadyEvent` 在**每个实例各跑一遍**。

标准写法（照 `ProfileVectorReconcileScheduler`）：

```java
String lockKey = "{业务}:{动作}:lock";          // 如 profile:reconcile:lock
try {
    Boolean locked = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, "1", Duration.ofSeconds(TTL));
    if (!Boolean.TRUE.equals(locked)) {
        return;                                  // 别的实例在跑，跳过本轮
    }
} catch (Exception e) {
    log.warn("抢锁失败", e);
}
try {
    doWork();
} finally {
    redisTemplate.delete(lockKey);               // 释放失败也不怕，TTL 兜底
}
```

**抢不到锁怎么办，看任务性质（两种都要显式选一种，不能含糊）：**

| 任务性质 | 行为 | 现有例子 |
|---|---|---|
| 后台兜底 / 定时对账 | **跳过**——避免 N 个实例重复打下游 | `ProfileVectorReconcileScheduler` |
| 用户触发的按需生成 | **继续执行**——抢不到锁也得保证功能可用 | `MatchQueryServiceImpl` / `WeatherAdviceServiceImpl` |

**锁参数规范：**

- key：`{业务}:{动作}:lock`
- TTL：常量放 `SystemConstants`（命名 `XXX_LOCK_TTL`），**必须大于任务最长耗时**——否则锁提前过期，第二个实例会进来
- 简单版锁**没有持有者校验**（A 超时后可能删掉 B 的锁），所以 TTL 要留足余量；要更严谨需引入 Redisson 看门狗（项目暂未引入）

> 长期方向：迁 XXL-JOB（调度中心天然「单机执行」），届时可去掉锁。

#### 7.2.2 进程内状态 ≠ 业务状态

| 禁止 | 多实例后果 | 替代 |
|---|---|---|
| `static` / `volatile` 可变字段存业务状态 | 各实例各一份，互相覆盖 | Redis / DB |
| `AtomicBoolean` 做「任务运行中」闸门 | 每个实例都能 CAS 成功，闸门形同虚设 | Redis 锁 |
| 进程内 Map 做缓存 / 索引 | 各实例内容不同，同一次查询结果不一致 | Redis，或每次查 DB |
| `synchronized` / `ReentrantLock` 做跨请求互斥 | 只锁本 JVM | Redis 锁 |

**只允许用于**：纯计算、无状态工具方法、真正的常量。

#### 7.2.3 读-改-写（check-then-act）必须有数据库兜底

「先 SELECT 判断，再 INSERT/UPDATE」在并发下必然出问题（多实例、多线程都算）：

```java
// ❌ 并发下会插两条
DeliveryRisk existing = riskMapper.selectByDeliveryId(id);
if (existing == null) riskMapper.insert(risk);

// ✅ 数据库兜底，三选一：
// a) 唯一索引 + ON DUPLICATE KEY UPDATE / catch DuplicateKeyException
// b) 乐观锁（version CAS）
// c) 分布式锁串行化
```

**只靠应用层判空 = 没有保护。** 典型高危：序号生成（`selectMaxXxx + 1`）、「只预警/只通知一次」类逻辑、先查后插的幂等。

#### 7.2.4 @Async 任务只活在发起实例

见 §7.0 异步选型，多实例视角补充两点：

- 任务只在**发起请求的那个实例**的线程池里跑，**重启即丢**，其它实例完全不知情
- **同类自调用会绕过 Spring 代理，`@Async` 静默失效、退化成同步执行**——本项目已发现两处（`SanctionSyncServiceImpl:90`、`ProfileSyncService:79`）

#### 7.2.5 实例本地语义偏差

| 场景 | 偏差 |
|---|---|
| Sentinel 单机流控 | 全局有效阈值 = 配置值 × 实例数（网关限流、登录防爆破都中招） |
| 内存会话 / SSE 长连接 | 连接只挂在某实例，跨实例收不到，需粘性会话或广播 |
| 本地文件读写 | 各实例各一份，跨实例读不到 |

#### 已知待修复清单（2026-10-19 全仓审计，改到这些位置时必须补保护）

| 位置 | 问题 | 严重度 | 状态 |
|---|---|---|---|
| `SanctionSyncServiceImpl` | 进程内 AtomicBoolean + `@Async` 自调用 → N 实例各下载 70MB | 高 | ✅ 已修 |
| `SanctionNameIndex` | volatile 内存索引各实例独立 → 筛查结果不一致 | 高 | ✅ 已修（fanout 广播） |
| `DeliveryRiskLifecycleServiceImpl` + `DeliveryRiskScheduler`（每 5 分钟） | 无锁 + 先查后插 → 重复扫描 / 异常中断整轮 | 高 | ✅ 已修（锁 + 捕获 DuplicateKeyException） |
| `DeliveryDelayWarningServiceImpl:35` + `DeliveryDelayScheduler`（每天 9 点） | 无锁 + NOT EXISTS 竞态 → 重复预警通知 | 中 | 待修 |
| `DeliveryLifecycleServiceImpl:364` | `selectMaxBatchNo+1` 无锁 → 重复批次号 | 中 | 待修 |
| `AiChatServiceImpl:174` / `AiContextCompactService:89` | 先查后插 / 无 CAS → 并发重复行 | 中 | ✅ 已修（生成锁 + 压缩锁 + CAS 写回） |
| `GatewayRateLimitFilter:69` | Sentinel 单机流控，阈值 × 实例数 | 低 | 待修 |

---

## 八、代码规范

- **分层：** `Controller -> Service -> Mapper`，Controller 只做参数校验和返回
- **注释两步走：** 方法头 javadoc 写"做什么/为什么" + **方法内部关键步骤必须加步骤注释**（`// 1.` `// 2.` 序号，解释每步在干什么），不只写方法头——复杂方法（多分支/多查询/多状态流转）内部每步都要有
- **业务实现类注释强制：** 新增/修改 `*ServiceImpl` 时，方法内每个业务代码片段都必须有中文注释；校验、查询、组装、事务写库、状态流转、循环、Stream 链、远程调用、异常降级尤其不能裸写。注释要解释"为什么这样做/业务含义"，不要机械翻译代码
- **DTO 不跨服务：** 每个微服务用自己的 Req/Resp
- **接口文档：** Controller 必须加 `@Tag` + `@Operation`
- **审计日志：** 除 GET 外，必须加 `@AuditLog`
- **统一响应：** 返回 `R<T>`
- **禁止 `SELECT *`：** 显式列字段
- **逻辑删除：** 用 `is_deleted`，不用物理 DELETE
- **URL 不硬编码：** 统一放 YML，`@Value` 不带默认值
- **优先 Stream：** 集合操作用 Stream，不手写 for
- **建造者模式：** 多字段赋值用 `@Builder` 或链式调用

### 8.1 Service 业务方法质量红线

后续新增或修改业务方法时，必须同步满足以下约束；不能满足时需在 PR/提交说明中写明原因和补偿方案。

1. **禁止循环远程调用（N+1）**
   - 对列表/详情中的关联数据，必须先收集 ID、去重、过滤 null，再调用批量接口。
   - 典型写法：`Set<Long> ids -> xxxClient.getByIds(ids) -> Map<Long, DTO> -> 记录组装时直接 get(id)`。
   - 同一个请求内不允许对同一远程服务反复调用；必要时复用一次批量查询结果。
   - 提交前必须检查 `for/map/forEach` 内是否出现 `xxxClient`、`RestClient`、`selectById` 等逐条调用。

2. **租户与资源归属校验是业务逻辑的一部分**
   - 只要方法接收 `id` 并返回或修改租户数据，就必须校验资源归属；不能只依赖 Controller、网关或前端权限。
   - 查询详情、AI 分析、报表、通知等读接口也要校验：采购方、供应商、被邀请方、被授权方等至少满足一种合法身份。
   - 关联 ID 必须校验属于当前主资源，例如 `quoteId` 必须属于 `inquiryId`，不能只判断非空或存在。
   - 校验失败统一抛 `BizException`，不得返回其他租户可见的数据。

3. **事务边界必须覆盖完整业务不变量**
   - 一个方法内多次写库、状态流转、关联表更新时，必须加 `@Transactional(rollbackFor = Exception.class)`。
   - 查询-only 方法不强制加事务；跨多个写操作的方法不得遗漏事务。
   - 事务内尽量避免 Feign、AI、文件、邮件等慢 I/O；确实需要远程数据时，应在进事务前批量取回，或在事务提交后异步处理后续动作。
   - 用户可见通知必须保持 MQ 方案，并优先使用事务提交后发布，避免主业务与通知副作用不一致。

4. **返回值与类职责必须可控**
   - 对外 Service 结果优先使用 VO/DTO/分页对象；新增接口禁止继续返回无类型 `Map<String, Object>`。
   - 单个 Service 实现类避免继续膨胀；新增独立业务能力时优先放入职责更细的类（查询、生命周期、邀请、授标、AI 等）。
   - 方法过长时必须拆私有步骤方法或独立 Service，保持主流程可读；新增复杂逻辑必须保留关键步骤注释。
   - 新增私有工具方法、批量组装方法、Stream/Map 转换时，必须注释输入、输出结构、批量查询目的和降级语义；禁止提交大段无注释实现。

5. **列表与 SQL 性能自检**
   - 分页参数必须在入口校验合法范围；默认值应限制在常量中，禁止直接把页码换算成负 offset。
   - 复杂查询必须显式列字段，禁止 `SELECT *`。
   - 新增列表条件、排序、JOIN、EXISTS 时必须同步确认索引；至少评估 `tenant_id + status + 排序时间` 和关联表外键索引。
   - 聚合统计必须按当前页记录批量查询，再组装 Map，禁止逐条 count。

6. **降级策略必须匹配数据性质**
   - 核心业务数据、权限校验、状态回写失败必须抛异常或拒绝操作。
   - 公司名称、展示文案、非关键计数等辅助字段可降级为空/0，但必须记录 warn/error 日志并说明降级原因。
   - 不允许用宽泛 `catch` 掩盖应当阻断业务的一致性错误。

### 8.2 Controller 命名与拆分规范

1. **按资源命名，不按菜单聚合**
   - Controller 类名必须体现具体业务资源，例如 `InquiryController`、`QuoteController`、`ContractController`。
   - 禁止继续把询盘、邀请、授标、AI、报价等多个资源塞进 `ProcurementController` 这类泛化类。
   - URL 可以按前端模块保持 `/api/admin/procurement/...`，类名必须按资源拆分，保证前端零感知。

2. **一 Controller 一资源**
   - 一个 Controller 只负责一个业务资源的入口，不复用给多个无明显归属的资源。
   - 复杂资源可继续细分，例如 `InquiryController`、`InquiryInviteController`、`InquiryAwardController`、`InquiryAiController`。
   - 合同签署、完成、终止等如果方法继续膨胀，也应拆成独立 Controller，而不是回填到综合 Controller。

3. **内部契约统一 `InternalXxxController`**
   - 内部接口类名必须以 `Internal` 开头，路径必须以 `/internal/{domain}` 开头。
   - 内部 Controller 只做 Feign 契约委托，不写业务规则、不直接访问 Mapper。
   - 契约路径与 `gsc-api` 消费方声明必须保持一致。

4. **Controller 与 Service 职责对齐**
   - Controller 只做参数接收、鉴权用户信息提取、服务委托和 `R` 包装，禁止写业务判断、事务、数据组装。
   - 命名应与依赖服务语义对应，例如 `InternalInquiryController` 优先依赖 `InquiryQueryService`，避免继续依赖旧的聚合 `InquiryService`。
   - 新增接口时优先注入细粒度 Query/Lifecycle/Domain Service，不再把多个业务域服务集中注入同一个 Controller。

5. **接口暴露规范**
   - 每个 Controller 必须有 `@Tag`，每个接口必须有 `@Operation`。
   - 除 GET 外必须加 `@AuditLog`。
   - 返回统一使用 `R<T>`；列表分页后续统一迁移到分页 DTO，不再新增 `Map<String, Object>` 返回。

### 8.3 AI 工具写操作规范

AI 写操作（工具名含 create/update/close/cancel 等写语义）必须遵守：

1. **二次确认 + 审计**：模型只能生成「待确认提案」存 Redis（`AiToolProposalService`），落库只在用户点确认后由 `AiToolConfirmController` 触发并打 `@AuditLog`；模型永远无法直接落库。
2. **身份强制取登录态**：写操作的 `tenantId/userId` 一律由 AI 域从登录态/tool token 注入，禁止从模型参数取值（防越权）。
3. **复用现有 Service 方法**：AI 写工具不得重写业务逻辑，直接复用各域已有的生命周期 Service（如 `InquiryLifecycleService.update/close/cancel/extendDeadline`）；语义不同（如「部分更新」vs「全覆盖更新」）才在域内新增方法。
4. **确认文案禁用裸 ID**：`confirm_required` 事件的 `actionSummary` 必须用「编号 + 标题」这类中文有含义的字段 + 具体改动组成完整提示，禁止只露雪花 ID。
   - 各实体展示字段：询盘 `inquiryNo`+`title`、合同 `contractNo`+`title`、报价 `quoteNo`、交付单 `deliveryNo`（按实体实际字段）
   - 格式：`<动作>：<实体类型> <编号>《<标题>》，<具体改动>`
   - 生成提案时若缺编号/标题，先 Feign 查询（如 `AiInquiryClient.getInquiry`）补全；查不到才退化为「实体类型 #id」兜底（异常分支）
5. **字段范围白名单**：AI 只放开安全的业务字段，结构性/关联字段（外键、附件 key、状态）不放开给模型乱改；语义独立的操作拆成独立工具（如「改字段」与「延长截止」分离，后者走 `extendDeadline` 支持 open/quoting/expired）。

---

## 九、重要提醒
1. 简单 CRUD 用 MP，复杂查询写 XML
2. 多租户 SQL 必须带 `tenant_id`
3. 禁止 `SELECT *`
4. 生产密码必须用环境变量
5. 逻辑删除用 `is_deleted`
6. 模块单向依赖：common 不依赖 framework
7. 网关不依赖 framework（WebFlux 与 Servlet 不兼容）
8. 服务间同步用 Feign，异步按 7.0 选型（一致性走 MQ，旁路可用 @Async）
9. 删除前检查关联数据
10. 接口必须加 Knife4j `@Tag` + `@Operation`
11. **新增/修改 Feign 契约必须遵守 7.1 七条铁律**（path 前缀、契约 DTO、Controller 不 implements、薄壳委托、data=null 语义、降级二分、启动类双包并列）
12. **异步任务选型见 7.0**：一致性关键路径（不能丢 / 跨域解耦 / 需重试死信）走 MQ；旁路增强（丢了可接受 + 有兜底补偿）可用 @Async；跨服务查数据/回写一律走 Feign，禁止直查他域表
13. **新增/修改业务 Service 必须执行 8.1 质量红线自检**：无循环远程调用、资源归属校验完整、事务覆盖全部写库、列表 SQL 批量化
14. **任何决策前先过一遍多实例（见 7.2）**：定时任务/启动初始化必须加 Redis 分布式锁；进程内状态（static/volatile/AtomicBoolean/本地 Map）不得承载业务状态；读-改-写必须有唯一索引/乐观锁/分布式锁兜底；@Async 任务只活在发起实例且禁止同类自调用
