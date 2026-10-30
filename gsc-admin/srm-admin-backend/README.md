# GSC Backend (Global SCM)

Spring Cloud Alibaba 微服务后端，从单体 `srm-admin-backend` 拆分而来。

## 技术栈

| 分类 | 选型 |
|------|------|
| 基础框架 | Spring Boot 3.3.7 + Spring Cloud + Spring Cloud Alibaba |
| 服务注册/配置 | Nacos 2.3.2 |
| 网关 | Spring Cloud Gateway (WebFlux) |
| 服务间调用 | OpenFeign + Spring Cloud LoadBalancer |
| 持久层 | MyBatis-Plus 3.5.7 + MySQL 8 |
| 缓存 | Redis 7 (Lettuce) |
| 消息队列 | RabbitMQ 3.13 |
| 链路追踪 | Micrometer Tracing + Zipkin |
| API 文档 | Knife4j (OpenAPI 3) |
| 定时任务 | Spring @Scheduled（XXL-JOB 备选） |

## 模块说明

```
gsc-backend
├── gsc-common              # 公共工具类、异常、枚举、R 响应体
├── gsc-api                 # Feign 契约接口 + DTO（服务间调用共享）
├── gsc-framework           # 公共框架配置（安全、MQ、Redis、CORS、日志等，自动装配）
├── gsc-gateway             # 网关（路由、CORS、内部接口拦截）
├── gsc-service-platform    # 平台域（认证、入驻、用户/角色/权限、通知、审计）
├── gsc-service-trade       # 交易域（采购需求、询盘、合同）
├── gsc-service-delivery    # 交付域（发货、验收、风险扫描）
├── gsc-service-supplier    # 供应商域（画像、尽调、智能匹配）
└── deploy                  # 部署相关
    ├── docker-compose.yml      # 基础设施一键编排
    └── nacos-config/           # Nacos 配置备份（5 个 yaml）
```

## 快速开始

### 1. 启动基础设施

```bash
cd deploy
docker compose up -d
```

拉起 Nacos、RabbitMQ、MySQL、Redis、Zipkin、XXL-JOB。

### 2. 导入 Nacos 配置

1. 打开 http://localhost:8848/nacos（nacos/nacos）
2. 新建 `dev` 命名空间
3. 将 `deploy/nacos-config/` 下的 5 个 yaml 依次导入：
   - `gsc-shared.yaml`（共享配置，全栈一致）
   - `gsc-platform.yaml` / `gsc-trade.yaml` / `gsc-delivery.yaml` / `gsc-supplier.yaml`（各服务专属）

### 3. 启动服务

按顺序启动：

| 顺序 | 服务 | 端口 | 启动类 |
|------|------|------|--------|
| 1 | gsc-gateway | 18080（nginx 反代 8080） | GscGatewayApplication |
| 2 | gsc-service-platform | 3081 | GscPlatformApplication |
| 3 | gsc-service-supplier | 3082 | GscSupplierApplication |
| 4 | gsc-service-trade | 3083 | GscTradeApplication |
| 5 | gsc-service-delivery | 3084 | GscDeliveryApplication |

## 配置三层结构

```
application.yml          → 端口、服务名、Nacos 连接、import 哪些配置（代码仓库管）
├── Nacos gsc-shared.yaml    → 全栈共享：MySQL/Redis/RabbitMQ/MyBatis-Plus/JWT/OSS/日志/Zipkin
├── Nacos gsc-{service}.yaml → 服务专属：permit-paths/match.engine 等
└── application-dev.yml      → 本地兜底：Nacos 不可用时接管，内容与 Nacos 保持一致
```

改配置去 Nacos 控制台，本地文件只管启动引导和保命兜底。

## 环境信息

| 中间件 | 开发环境地址 |
|--------|-------------|
| Nacos | 120.77.169.224:8848 |
| MySQL | 120.26.30.114:3306 |
| Redis | 120.26.30.114:6379 |
| RabbitMQ | 120.26.30.114:5672（管理台 15672） |
| Zipkin | 120.26.30.114:9411 |