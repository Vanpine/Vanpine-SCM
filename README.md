# GSC - Global SCM / SRM 供应链管理系统

全球供应链管理平台（Global SCM / SRM）前后端源码集合，覆盖供应商管理、采购交易、履约交付与 AI 智能匹配等业务域。

## 目录结构

```
gsc/
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
