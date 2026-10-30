"""
AI 工具调用模块 — DeepSeek Function Calling 的工具 schema + Java 回调执行
职责：定义喂给模型的工具清单（与 Java AiToolRegistry 保持一致）、把模型生成的
tool_call 回传给 Java 的 /internal/ai/tool 执行，返回结构化结果。
Python 不直接连业务库，Java 是唯一数据出口（权限/租户/审计都在 Java 侧）。
"""
import os  # 环境变量
import logging  # 日志
import asyncio  # 把同步的知识库检索链路丢进线程池，避免阻塞事件循环

import httpx  # 异步 HTTP 客户端（回调 Java，连接池复用）

logger = logging.getLogger("srm-ai.tools")  # 模块日志器

# ==================== 配置 ====================

# Java AI 工具 API 地址（gsc-service-ai 的 /internal/ai/tool）。
# 默认本机 3085（与 gsc-service-ai 的 server.port 对齐）；生产由环境变量覆盖为内网地址。
TOOL_API_URL = os.environ.get("AI_TOOL_API_URL", "http://127.0.0.1:3085/internal/ai/tool")

# 异步 HTTP 客户端：execute_tool 本身跑在事件循环里，直接 await 即可，不必往线程池丢同步请求。
# 模块级单例复用连接池——原来每次工具调用都 urlopen，等于每次重新 TCP/TLS 握手。
_client = httpx.AsyncClient(timeout=30.0)

# 单次 tool loop 最多执行工具轮数（search → detail 两轮 + 兜底，防死循环）
MAX_TOOL_ROUNDS = 4

# ==================== 工具 schema（与 Java AiToolRegistry 一一对应） ====================
# 喂给 DeepSeek 的 OpenAI Function Calling 格式。字段命名 camelCase，与 Java 契约 DTO 对齐。

TOOL_SCHEMAS = [
    {
        "type": "function",
        "function": {
            "name": "search_suppliers",
            "description": "按关键词搜索供应商，返回名称/行业/品类/地区/资质/准入状态",
            "parameters": {
                "type": "object",
                "properties": {
                    "keyword": {"type": "string", "description": "关键词，匹配企业名/行业/地区/品类"},
                    "page": {"type": "integer", "description": "页码，从1开始"},
                    "pageSize": {"type": "integer", "description": "每页条数"},
                },
                "required": ["keyword"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_supplier_detail",
            "description": "按供应商租户ID查详情，返回完整企业画像（成立年份/产能/规模/营业额/主要客户/交货周期/履约评分等）、资质认证、尽调进度与准入状态，以及尽调报告下载链接（ddReportUrl）",
            "parameters": {
                "type": "object",
                "properties": {
                    "tenantId": {"type": "string", "description": "供应商租户ID（雪花ID字符串）"},
                },
                "required": ["tenantId"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_my_profile",
            "description": "查当前登录用户自己的企业画像（采购方/供应商均适用），返回完整画像 + 尽调报告下载链接。无参数，后端自动取当前登录用户。用户问「我方/我们公司/我的企业画像」时用本工具",
            "parameters": {
                "type": "object",
                "properties": {},
                "required": [],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "list_contracts",
            "description": "查当前登录租户自己的合同列表（采购/供应双视角），返回编号/标题/金额/状态/交期。回答时状态用 statusText 中文（如已签署），不要输出英文 status 码",
            "parameters": {
                "type": "object",
                "properties": {
                    "status": {"type": "string", "description": "合同状态筛选"},
                    "keyword": {"type": "string", "description": "标题关键词"},
                    "role": {"type": "string", "description": "视角：buyer或supplier，默认buyer"},
                    "page": {"type": "integer", "description": "页码，从1开始"},
                    "pageSize": {"type": "integer", "description": "每页条数"},
                },
                "required": [],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "create_supplier_note",
            "description": "给指定供应商记一条备注（采购商私有标注）。此操作需用户二次确认后才真正写入。",
            "parameters": {
                "type": "object",
                "properties": {
                    "supplierTenantId": {"type": "string", "description": "被备注的供应商租户ID（雪花ID字符串）"},
                    "content": {"type": "string", "description": "备注内容"},
                },
                "required": ["supplierTenantId", "content"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "list_inquiries",
            "description": "查当前登录租户自己的询盘列表（采购/供应双视角），返回询盘编号/标题/品类/数量/交期/截止/状态，用于定位要操作的询盘。回答时状态用 statusText 中文（如报价中），不要输出英文 status 码",
            "parameters": {
                "type": "object",
                "properties": {
                    "status": {"type": "string", "description": "询盘状态筛选"},
                    "keyword": {"type": "string", "description": "询盘编号/标题/品类关键词"},
                    "role": {"type": "string", "description": "视角：buyer或supplier，默认buyer"},
                    "page": {"type": "integer", "description": "页码，从1开始"},
                    "pageSize": {"type": "integer", "description": "每页条数"},
                },
                "required": [],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "update_inquiry",
            "description": "修改询盘的字段（如交期/数量/付款条件/标题/品类/单位等）。只改传入的字段，未传字段保持不变。报价截止时间不能用本工具改，请用 extend_inquiry_deadline。此操作需用户二次确认后才真正写入。",
            "parameters": {
                "type": "object",
                "properties": {
                    "inquiryId": {"type": "string", "description": "询盘ID（雪花ID字符串，先从 list_inquiries 查得）"},
                    "title": {"type": "string", "description": "询盘标题"},
                    "category": {"type": "string", "description": "采购品类"},
                    "quantity": {"type": "number", "description": "采购数量"},
                    "unit": {"type": "string", "description": "单位"},
                    "deliveryPlace": {"type": "string", "description": "交货地点"},
                    "deliveryDate": {"type": "string", "description": "期望交付日期，格式 yyyy-MM-dd"},
                    "paymentTerms": {"type": "string", "description": "付款条件"},
                    "specText": {"type": "string", "description": "规格条款"},
                },
                "required": ["inquiryId"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "extend_inquiry_deadline",
            "description": "延长询盘的报价截止时间（支持 open/quoting/expired 状态）。此操作需用户二次确认后才真正执行。",
            "parameters": {
                "type": "object",
                "properties": {
                    "inquiryId": {"type": "string", "description": "询盘ID（雪花ID字符串，先从 list_inquiries 查得）"},
                    "newDeadline": {"type": "string", "description": "新的报价截止时间，格式 yyyy-MM-dd HH:mm:ss"},
                },
                "required": ["inquiryId", "newDeadline"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "list_deliveries",
            "description": "查当前登录租户自己的交付单列表（采购/供应双视角），返回交付单号/合同号/供应商/状态/运单号/承诺交期。回答时状态用 statusText 中文（如待发货），不要输出英文 status 码",
            "parameters": {
                "type": "object",
                "properties": {
                    "status": {"type": "string", "description": "状态筛选，逗号分隔，如 pending_shipment,shipped"},
                    "keyword": {"type": "string", "description": "交付单号/运单号关键字"},
                    "role": {"type": "string", "description": "视角：buyer或supplier，默认buyer"},
                    "contractId": {"type": "string", "description": "合同ID（雪花ID字符串）"},
                    "supplierTenantId": {"type": "string", "description": "供应商租户ID"},
                    "page": {"type": "integer", "description": "页码，从1开始"},
                    "pageSize": {"type": "integer", "description": "每页条数"},
                },
                "required": [],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_delivery_detail",
            "description": "按交付单ID查详情（含履约时间线 + 物流事件 + 承诺记录 + 退货信息）。回答时状态用 statusText 中文，不要输出英文 status 码",
            "parameters": {
                "type": "object",
                "properties": {
                    "deliveryId": {"type": "string", "description": "交付单ID（雪花ID字符串，先从 list_deliveries 查得）"},
                },
                "required": ["deliveryId"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "submit_promise_date",
            "description": "供应商对未闭环的交付单提交承诺交期（同单已有待确认承诺时不可重复提交）。此操作需用户二次确认后才真正写入。",
            "parameters": {
                "type": "object",
                "properties": {
                    "deliveryId": {"type": "string", "description": "交付单ID（雪花ID字符串，先从 list_deliveries 查得）"},
                    "promisedDate": {"type": "string", "description": "承诺交期，格式 yyyy-MM-dd"},
                    "reason": {"type": "string", "description": "承诺原因（可选，如 原材料延迟/产能紧张；不填则用默认文案）"},
                },
                "required": ["deliveryId", "promisedDate"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "fill_return_logistics",
            "description": "采购方填写退货物流（仅交付单状态为 待退回货物 return_confirmed 时可用）。此操作需用户二次确认后才真正写入。",
            "parameters": {
                "type": "object",
                "properties": {
                    "deliveryId": {"type": "string", "description": "交付单ID（雪花ID字符串，先从 list_deliveries 查得）"},
                    "logisticsCompany": {"type": "string", "description": "退货物流公司，如 顺丰/中通"},
                    "trackingNo": {"type": "string", "description": "退货运单号"},
                },
                "required": ["deliveryId", "logisticsCompany", "trackingNo"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "risk_assess_supplier",
            "description": "评估指定供应商的综合风险，返回交付/质量/合规三维健康分（0-100，越高越安全）、总评分与风险等级（low/medium/high）。",
            "parameters": {
                "type": "object",
                "properties": {
                    "tenantId": {"type": "string", "description": "待评估的供应商租户ID（雪花ID字符串，先从 search_suppliers 或 get_supplier_detail 查得）"},
                },
                "required": ["tenantId"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_weather",
            "description": "查某地实时天气与近几日预报（全球城市）。用户问天气、台风、暴雨、物流节点当地气候时调用。支持详细地点（如杭州西湖、广州天河智慧城）。问某票货在途天气时先 get_delivery_detail 取物流地点再调本工具。回答用中文，点名气温、风力、降水，不要编造未返回的灾害预警。",
            "parameters": {
                "type": "object",
                "properties": {
                    "city": {"type": "string", "description": "地点，城市或详细节点，如 上海 / 杭州西湖 / Shanghai"},
                    "days": {"type": "integer", "description": "预报天数 1-7，默认 3"},
                },
                "required": ["city"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "search_knowledge_base",
            "description": "检索企业上传的知识库文档（合同文本/资质证书/制度/规范/手册/条款等），返回相关段落与来源章节。两类问题用它：①『规定/要求/标准/政策/流程/条款/怎么写/是什么』等需查文档的知识性问题；②『某个上传文档里写了什么』『某公司的合同条款/资质/资料/文档』——即要查的是**上传文件的内容**时。注意区分：查**结构化业务数据**（有几份合同、询盘列表、供应商准入状态/画像）用对应业务工具；但查**上传文档的具体内容**（条款原文、资质明细、制度条文）用本工具。可与业务工具同时调用（如既查供应商画像、又查其合同文档）。",
            "parameters": {
                "type": "object",
                "properties": {
                    "query": {"type": "string", "description": "检索关键词/问题，用完整自然语言，如『供应商准入要求』『违约金条款怎么规定』"},
                },
                "required": ["query"],
            },
        },
    },
]


async def execute_tool(name: str, arguments: dict, tool_token: str,
                       tenant_id: int | None = None) -> dict:
    """执行一次工具调用，返回结构化结果。

    分流：
      - search_knowledge_base：知识库检索，Python 本地执行（Milvus + rerank），不回 Java
      - 其余业务工具：回传 Java（/internal/ai/tool）执行

    Args:
        name: 工具名
        arguments: 模型生成的参数（dict）
        tool_token: Java 签发的一次性工具令牌（回调时放 Authorization 头）
        tenant_id: 租户ID（知识库检索隔离用；本地工具需要）

    Returns:
        {"success": bool, "result": 任意 | None, "error": str | None,
         "requireConfirm": bool, "proposalId": str | None, "actionSummary": str | None,
         "entities": list}
        网络失败 / 无令牌也返回 success=False，不抛异常（让模型拿到错误后自己组织回答）。
        entities 为 Java 构建的业务对象（指代消解上下文），由 chat 层随工具事件回传。
    """
    # 知识库检索走 Python 本地（不回 Java）：向量库/rerank 都在本服务。
    # 这条链路是同步的（Milvus 客户端 + embedding/rerank 都是同步调用），必须丢线程池——
    # 直接在协程里调会阻塞事件循环，把所有并发中的 SSE 流一起卡住。
    if name == "search_knowledge_base":
        return await asyncio.to_thread(_search_knowledge_base, arguments, tenant_id, tool_token)

    if not tool_token:
        logger.warning("无工具令牌，跳过工具执行: tool=%s", name)
        return {"success": False, "result": None, "error": "缺少工具令牌，无法查询业务数据"}

    # 异步 POST 到 Java（超时 30s 由模块级客户端统一给）
    try:
        response = await _client.post(
            TOOL_API_URL,
            json={"toolName": name, "arguments": arguments or {}},
            headers={"Authorization": f"Bearer {tool_token}"},
        )
        response.raise_for_status()
        body = response.json()
    except Exception as e:  # 网络/超时/HTTP 错误统一降级为失败结果
        logger.error("工具执行失败: tool=%s, error=%s", name, e)
        return {"success": False, "result": None, "error": f"工具调用失败：{e}"}

    # Java 统一响应体 {code, message, data}；code != 200 视为工具执行失败
    if body.get("code") != 200:
        return {"success": False, "result": None, "error": body.get("message", "工具执行失败")}
    data = body.get("data") or {}
    if not data.get("success"):
        return {"success": False, "result": data.get("result"), "error": data.get("error")}
    # 写工具（如 create_supplier_note）返回 requireConfirm=true：带出提案ID + 操作摘要，
    # 供 chat 层短路出 confirm_required 事件给前端渲染确认按钮，不再回填模型自由生成。
    return {
        "success": True,
        "result": data.get("result"),
        "error": None,
        "requireConfirm": bool(data.get("requireConfirm")),
        "proposalId": data.get("proposalId"),
        "actionSummary": data.get("actionSummary"),
        # 业务对象（Java 侧构建）：chat 层原样放进 tool_result/confirm_required 事件回传，
        # Java 据此落 ai_conversations.context_entities，供下一轮指代消解
        "entities": data.get("entities") or [],
    }


def _search_knowledge_base(arguments: dict, tenant_id: int | None, tool_token: str) -> dict:
    """知识库检索（本地执行）：供 LLM 在需要查上传文档时调用。

    返回命中的文档块（含来源章节），低置信时 success=True 但 result 为空列表，
    让模型据「无结果」自行说明未找到，而非编造。
    """
    query = (arguments or {}).get("query", "") if isinstance(arguments, dict) else ""
    if not query or not str(query).strip():
        return {"success": False, "result": None, "error": "检索关键词为空"}
    if tenant_id is None:
        return {"success": False, "result": None, "error": "缺少租户标识，无法检索知识库"}
    try:
        from app.services.rag import retrieve
        hits = retrieve(str(query), tenant_id, tool_token=tool_token)
    except Exception as e:
        logger.error("知识库检索失败: %s", e, exc_info=True)
        return {"success": False, "result": None, "error": f"知识库检索失败：{e}"}
    # 组装可读结果：来源 + 章节 + 正文（供模型引用）
    # 同时保留 document_id / chunk_index：Python 侧转 citations 时要用它们做引用跳转定位
    items = []
    for h in hits or []:
        section = " > ".join(h.get("heading_path") or [])
        items.append({
            "source": h.get("file_name", "未知文档"),
            "section": section,
            "content": h.get("content", ""),
            "document_id": h.get("document_id"),
            "chunk_index": h.get("chunk_index"),
        })
    return {"success": True, "result": items, "error": None}


def summarize(result) -> str:
    """把工具结果压缩成一句前端可见的摘要（tool_result 事件用，不喂模型）。"""
    data = result.get("result") if isinstance(result, dict) else result
    # dict 结果（如天气）与 list 结果（列表查询）互斥，必须先判 dict，
    # 否则被下方 list 分支的 return 挡住，天气摘要永不触达（历史 bug）。
    if isinstance(data, dict):
        place = data.get("resolvedPlace") or data.get("originalQuery")
        text = data.get("weatherText")
        temp = data.get("temperature")
        if place and text:
            suffix = f"{temp}℃" if temp is not None else ""
            return f"已查到{place}天气：{text} {suffix}".strip()
        return "已查到详情"
    if not isinstance(data, list):
        return "查询完成"
    if data and isinstance(data[0], dict) and "source" in data[0]:
        return f"知识库命中 {len(data)} 条资料"
    return f"已找到 {len(data)} 条结果"
