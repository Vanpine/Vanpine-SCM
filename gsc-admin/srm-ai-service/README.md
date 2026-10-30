# SRM AI Service

SRM AI Service 是全球供应链管理平台（Global SCM / SRM）的 Python AI 服务，负责供应商画像向量化、采购需求智能匹配、报价分析、授标理由生成和询盘内容辅助等 AI 能力。Java 后端通过内网 HTTP 接口调用本服务，本服务再组合 Embedding、Milvus 向量检索与 DeepSeek 大模型完成语义计算和结果解释。

## 核心能力

- **供应商画像向量化**：将供应商品类、产品、资质、地域、简介等画像文本转换为向量，并按 `tenant_id` 写入 Milvus。
- **供应商智能匹配**：对采购需求进行语义召回，结合品类、资质、产能、地域、出口经验、画像完整度等多维度评分，并生成可读的匹配理由。
- **采购报价分析**：对询盘报价进行综合排名、风险识别和授标建议输出。
- **授标理由生成**：基于中标报价与其他报价的对比结果，自动生成授标说明。
- **询盘内容辅助**：根据采购品类和基础信息推荐规格模板、付款条件、交期建议和注意事项。
- **健康检查**：提供服务运行状态与已向量化供应商数量，便于运维监控。

## 技术架构

```text
Java 后端服务
    │ HTTP JSON
    ▼
FastAPI 服务（SRM AI Service）
    ├── Embedding API：供应商画像 / 采购需求向量化
    ├── Milvus：向量持久化与语义检索
    ├── 规则引擎：多维度供应商评分与排序
    └── DeepSeek：匹配理由、报价分析、授标理由等自然语言生成
```

### 技术栈

| 类别 | 技术 |
| --- | --- |
| Web 框架 | FastAPI |
| 运行时 | Python 3.9+ |
| ASGI 服务器 | Uvicorn |
| 数据校验 | Pydantic v2 |
| 向量数据库 | Milvus |
| Embedding | OpenAI 兼容 API，默认 SiliconFlow `BAAI/bge-m3` |
| 大语言模型 | DeepSeek `deepseek-chat`，通过 LangChain OpenAI 适配器调用 |

## 项目结构

```text
srm-ai-service/
├── app/
│   ├── api/                 # API 路由层
│   │   └── routes.py
│   ├── models/              # Pydantic 请求 / 响应模型
│   │   └── schemas.py
│   ├── services/            # 业务能力层
│   │   ├── embedding.py     # Embedding API 调用与缓存
│   │   ├── matching.py      # 语义召回与综合评分
│   │   ├── quote_analysis.py # 报价分析与授标建议
│   │   └── reasoning.py     # DeepSeek 推理与文本生成
│   ├── store/               # 向量存储访问层
│   │   └── qdrant_store.py
│   └── main.py              # FastAPI 应用入口
└── requirements.txt         # Python 依赖清单
```

## API 一览

服务默认暴露在 `http://127.0.0.1:8100`，所有接口均以 `/api` 为前缀。完整请求和响应字段可访问 Swagger 文档：

```text
http://127.0.0.1:8100/docs
```

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/health` | 健康检查，返回服务状态与供应商向量数量 |
| POST | `/api/embed` | 供应商画像向量化并写入 Milvus |
| POST | `/api/match` | 采购需求与候选供应商智能匹配 |
| POST | `/api/risk/assess` | 供应商风险评估（Phase 2 预留） |
| POST | `/api/inventory/alert` | 库存 / 供应预警（Phase 2 预留） |
| POST | `/api/procurement/analyze-quotes` | 询盘报价综合分析 |
| POST | `/api/procurement/award-reason` | 生成授标理由 |
| POST | `/api/procurement/inquiry-suggest` | 生成询盘填写建议 |

### 健康检查示例

```bash
curl -sS http://127.0.0.1:8100/api/health
```

正常返回示例：

```json
{
  "status": "ok",
  "supplier_count": 0
}
```

## 快速开始

### 1. 准备环境

建议使用 Python 3.9 或以上版本：

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
```

### 2. 配置环境变量

生产环境必须通过环境变量注入密钥和内网服务地址，不要将密钥提交到 Git 仓库。

| 环境变量 | 必填 | 说明 |
| --- | --- | --- |
| `EMBEDDING_API_KEY` | 是 | Embedding API 密钥 |
| `EMBEDDING_BASE_URL` | 否 | OpenAI 兼容 Embedding API 地址 |
| `EMBEDDING_MODEL` | 否 | Embedding 模型名称，默认 `BAAI/bge-m3` |
| `EMBEDDING_VECTOR_DIM` | 否 | 向量维度，默认 `1024` |
| `MILVUS_HOST` | 是 | Milvus 服务地址，生产环境建议使用内网地址 |
| `MILVUS_PORT` | 否 | Milvus 服务端口，默认 19530 |
| `DEEPSEEK_API_KEY` | 是 | DeepSeek API 密钥 |

本地启动前可按需导出：

```bash
export EMBEDDING_API_KEY="your-embedding-api-key"
export MILVUS_HOST="127.0.0.1"
export MILVUS_PORT="19530"
export DEEPSEEK_API_KEY="your-deepseek-api-key"
```

### 3. 启动服务

```bash
python -m uvicorn app.main:app \
  --host 127.0.0.1 \
  --port 8100
```

如需让同机 Java 服务访问，绑定 `127.0.0.1` 即可；如需让其他内网机器访问，应绑定内网 IP 或内网网卡，并通过防火墙限制来源。

### 4. 验证服务

```bash
curl -i http://127.0.0.1:8100/api/health
python -m compileall -q app
```

## Java 后端集成

Java 后端通过以下配置指定 AI 服务地址：

```yaml
gsc:
  ai:
    service:
      url: ${AI_SERVICE_URL:http://localhost:8100}
```

生产环境中，如果 Java 后端与 AI 服务部署在同一台服务器，建议配置：

```env
AI_SERVICE_URL=http://127.0.0.1:8100
```

主要调用方包括：

- 供应商画像同步：画像创建或更新后调用 `/api/embed`
- 供应商智能匹配：询盘匹配流程调用 `/api/match`
- 采购 AI 流程：分别调用 `/api/procurement/*` 系列接口

## 生产部署建议

### 1. 部署目录

示例：

```text
/opt/srm-ai-service
```

将代码同步到部署目录后，创建独立虚拟环境并安装依赖：

```bash
cd /opt/srm-ai-service
python3 -m venv .venv
.venv/bin/python -m pip install --upgrade pip
.venv/bin/python -m pip install -r requirements.txt
```

### 2. 环境变量文件

创建只有运维人员可读的环境变量文件，例如 `/etc/srm-ai-service.env`：

```env
EMBEDDING_API_KEY=your-embedding-api-key
EMBEDDING_BASE_URL=https://api.siliconflow.cn/v1
EMBEDDING_MODEL=BAAI/bge-m3
EMBEDDING_VECTOR_DIM=1024
MILVUS_HOST=your-milvus-internal-host
MILVUS_PORT=19530
DEEPSEEK_API_KEY=your-deepseek-api-key
```

```bash
sudo chmod 600 /etc/srm-ai-service.env
sudo chown root:root /etc/srm-ai-service.env
```

### 3. systemd 示例

创建 `/etc/systemd/system/srm-ai-service.service`：

```ini
[Unit]
Description=SRM AI Service
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=srm
Group=srm
WorkingDirectory=/opt/srm-ai-service
EnvironmentFile=/etc/srm-ai-service.env
ExecStart=/opt/srm-ai-service/.venv/bin/python -m uvicorn app.main:app --host 127.0.0.1 --port 8100
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

启动并设置开机自启：

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now srm-ai-service
sudo systemctl status srm-ai-service --no-pager
```

### 4. 运维检查

```bash
curl -sS http://127.0.0.1:8100/api/health
sudo journalctl -u srm-ai-service -f
```

## 安全注意事项

- 生产环境密钥只能通过环境变量、密钥管理系统或部署平台注入。
- Milvus 和 AI 服务建议部署在内网，并通过安全组、防火墙或 Nginx 访问控制限制来源。
- 不要将 `.venv`、`__pycache__`、日志文件或本地环境配置提交到 Git。
- API Key 一旦提交过仓库，应在服务商后台立即作废并更换。

## 开发说明

修改代码后建议执行：

```bash
source .venv/bin/activate
python -m compileall -q app
python -c "import app.main"
python -m uvicorn app.main:app --host 127.0.0.1 --port 8100
```

接口契约以运行中的 `/docs` Swagger 文档为准。新增或调整字段时，需要同步更新 `app/models/schemas.py`、服务层逻辑以及 Java 后端调用模型。
