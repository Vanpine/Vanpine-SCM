# vanpine-srm · 全球供应商管理系统

> 全球供应链 AI 赋能平台 · 赋能地方资源 · 连接全球市场

面向跨境采购场景的一体化供应链协同平台，把**寻源 → 询盘 → 比价 → 签约 → 履约 → 风控**整条链路搬到线上，并用 AI 引擎做匹配、评估与预警。

## 项目背景

传统 SRM 解决的是"企业内部如何管供应商"，而跨境采购真正的痛点在外部不确定性——地缘冲突、关税突变、极端天气、制裁名单变更、供应商突然失联。本项目把这些**外部风险信号**接入采购流程，让每一次选供应商、每一笔交付都有数据支撑，而不是靠经验拍板。

## 核心功能

**1. 供应商入驻与认证** — 企业注册申请、资质材料上传、平台审核、租户隔离，支持采购商/供应商双角色。

**2. 供应商全域画像** — 企业基本信息、产品品类、产能、出口经验、认证资质、财务与履约记录形成结构化画像；画像文本向量化后进入向量库，供语义匹配调用。

**3. 智能供需匹配** — 采购需求发布后，AI 对需求做语义解析，向量召回候选供应商，再叠加品类、资质、产能、地域、出口经验、画像完整度等多维度评分排序，并**生成可读的匹配理由**。同时提供买卖双向广场，支持主动发现与直接对接。

**4. 尽职调查与合规筛查** — 制裁名单实时比对、多层股权穿透追溯实际控制人与关联方、诉讼与行政处罚记录自动汇总；尽调任务可派单给第三方机构，机构拥有独立工作台，分钟级生成尽调报告。

**5. 采购交易全链路** — 采购需求 → 询盘邀请 → 结构化 RFP/RFQ 生成 → 报价归集 → 多维度比价（TCO 成本拆解、异常报价识别）→ 授标（AI 生成授标理由）→ 电子合同签署 → 履约跟踪。

**6. 交付与物流履约** — 发货、收货、验收三段管理，物流轨迹追踪，关键节点自动存证，并结合天气数据给出运输建议（如台风预警时提示调整航线或提前发货）。

**7. 供应保障驾驶舱** — 聚合在途订单与供应商运行状态，风险事件自动关联到具体供应商与航线，触发分级预警，并支持应急处置推演：备选供应商、替代航线、调拨方案。

**8. 平台治理** — 多租户管理、用户/角色/菜单权限、审计日志、文件服务、消息通知、支付回调。

## AI 能力

AI 服务独立部署，负责"计算与生成"，业务数据由后端通过内网接口提供。

| 能力 | 说明 |
|------|------|
| 供应商画像向量化 | Embedding API + Milvus，按 `tenant_id` 隔离写入 |
| 需求语义匹配 | 向量召回 + 规则引擎多维打分 + 理由生成 |
| 报价分析 | 综合排名、风险识别、授标建议 |
| 授标理由生成 | 对比中标报价与其他报价，自动成文 |
| 询盘内容辅助 | 按品类推荐规格模板、付款条件、交期建议 |
| 供应风险预判 | 多因子风险打分，输出风险描述 |
| 库存预警 | 消耗速率预测，提前提示补货 |
| 运输天气建议 | 航线天气查询 + 时效影响解读 |
| AI 助理 | RAG 知识库问答（支持 PDF / Word / Excel 解析入库）、多轮对话、上下文压缩、**写操作工具调用需人工确认** |

## 架构设计

### 工程规模

553 个 Java 源文件（含测试 583）、53 个 REST Controller、9 个 Maven 模块；70 个 Vue 单文件组件（后台 55 + 官网 15）；Python AI 服务 65 个源文件（应用 35 + 测试 30）。

### 设计取舍

1. **微服务但单体仓库** —— 按业务域拆分（平台 / 交易 / 交付 / 供应商 / AI），放在同一父 POM 下统一构建，避免早期过度拆分带来的运维成本。
2. **AI 与业务解耦** —— AI 服务只做计算与生成，不持有业务事务；服务不可用时不阻塞主流程。
3. **AI 写操作需确认** —— 助理可调用业务接口，但写操作必须人工确认后执行，避免模型幻觉直接改数据。
4. **多租户数据隔离** —— 画像向量按租户分区，防止跨企业数据串号。

## 目录结构

```
vanpine-srm/
├── gsc-admin/
│   ├── srm-admin-backend     # Java 微服务后端（Spring Cloud Alibaba）
│   ├── srm-admin-frontend    # 后台管理前端（Vue 3）
│   └── srm-ai-service        # Python AI 服务（FastAPI）
└── gsc-fronted/
    └── srm-official-website  # 官网前端（Vue 3）
```

## 项目一览

| 项目 | 技术栈 | 默认端口 | 说明 |
|------|--------|----------|------|
| srm-admin-backend | Java 17 · Spring Boot 3.3.7 · Spring Cloud Alibaba · MyBatis-Plus · MySQL 8 · Redis · RabbitMQ · Nacos | 网关 18080<br>业务服务 3081–3085 | 多模块微服务单体仓库 |
| srm-admin-frontend | Vue 3 · Vite · Element Plus · Pinia · Vue Router · Vue I18n · Tailwind CSS 4 | 5173（Vite） | 后台管理系统 |
| srm-ai-service | Python · FastAPI · LangChain · Milvus · DeepSeek · Embedding API | 8100 | 供应商匹配、报价分析、授标理由生成 |
| srm-official-website | Vue 3 · Vite · Tailwind CSS 4 · GSAP · Lenis · Leaflet · Vue I18n | 5173（Vite） | 公司官网 |

### srm-admin-backend 模块

```
gsc-backend
├── gsc-common              # 公共工具、异常、枚举、R 响应体
├── gsc-api                 # Feign 契约接口 + DTO
├── gsc-framework           # 安全、MQ、Redis、CORS、日志等公共配置
├── gsc-gateway             # API 网关（路由、CORS、内部接口拦截）
├── gsc-service-platform    # 平台域：认证、入驻、用户/角色/权限、通知、审计
├── gsc-service-trade       # 交易域：采购需求、询盘、合同
├── gsc-service-delivery    # 交付域：发货、验收、风险扫描
├── gsc-service-supplier    # 供应商域：画像、尽调、智能匹配
└── gsc-service-ai          # AI 域：对接 srm-ai-service
```

业务服务端口：platform 3081、supplier 3082、trade 3083、delivery 3084、ai 3085，统一经网关 18080 访问。

## 快速开始

### 后端

```bash
# 1. 启动基础设施（MySQL / Redis / RabbitMQ / Nacos）
cd gsc-admin/srm-admin-backend/deploy
docker compose up -d

# 2. 编译并启动网关与服务
cd ..
mvn clean install -DskipTests
java -jar gsc-gateway/target/*.jar
```

### 后台前端

```bash
cd gsc-admin/srm-admin-frontend
npm install
npm run dev
```

### AI 服务

```bash
cd gsc-admin/srm-ai-service
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env          # 填入 EMBEDDING_API_KEY / DEEPSEEK_API_KEY 等
uvicorn app.main:app --host 127.0.0.1 --port 8100
```

### 官网

```bash
cd gsc-fronted/srm-official-website
npm install
npm run dev
```

## 环境变量

各项目通过 `.env` / `application-*.yml` 注入配置。仓库中仅保留 `.env.example` 模板，真实密钥与连接信息需在本地自行配置，不会提交到版本库。

## 仓库说明

- 依赖与构建产物（`node_modules`、`target`、`dist` 等）不纳入版本管理。
- 各子项目在本地仍保留独立的 git 历史，本仓库为源码合集快照。
