"""
API 路由模块
定义所有 REST 端点，委托给 Service 层处理业务逻辑
所有端点均有 try-except 兜底 + 日志记录，防止单次请求异常导致 uvicorn 进程崩溃
"""
import asyncio  # 捕获客户端取消，避免把停止生成记成服务异常
import json  # JSON 序列化（SSE 事件）
import logging  # 日志模块
from fastapi import APIRouter, HTTPException  # FastAPI 路由与异常
from fastapi.responses import StreamingResponse  # 流式响应（SSE）
from app.models.schemas import (  # Pydantic 数据模型
    EmbedRequest, EmbedResponse,
    RequirementEmbedRequest,
    MatchRequest, MatchResponse, MatchResult,
    MatchRequirementsRequest, MatchRequirementsResponse,
    RiskRequest, RiskResponse,
    InventoryAlertRequest, InventoryAlertResponse,
    QuoteAnalysisRequest, QuoteAnalysisResponse,
    AwardReasonRequest, AwardReasonResponse,
    InquirySuggestRequest, InquirySuggestResponse,
    ChatRequest, DocumentParseRequest,
    CompactRequest, CompactResponse,
    DescribeImageRequest, DescribeImageResponse,
    WeatherAdviceRequest, WeatherAdviceResponse,
)
from app.services.matching import (
    match as do_match,
    embed_profile as do_embed,
    embed_requirement as do_embed_requirement,
    remove_requirement as do_remove_requirement,
    match_requirements as do_match_requirements,
)
from app.services.weather_advice import generate_weather_advice
from app.services.image_desc import describe_image  # 图片内容描述（历史图指代用）
from app.services.quote_analysis import (  # 比价分析服务
    analyze_quotes as do_analyze_quotes,
    generate_award_reason as do_generate_award_reason,
    suggest_inquiry_fields as do_suggest_inquiry_fields,
)
from app.services import rag, chat  # RAG 检索与流式对话
from app.services.intent import classify  # 意图预路由（元问题直答，防业务大表劫持）
from app.services.query_rewriter import rewrite as rewrite_query  # 查询改写与指代消解
from app.store.milvus_store import get_count  # 向量库统计

# 模块日志器
logger = logging.getLogger("srm-ai.routes")

# 创建 API 路由器，所有端点统一加 /api 前缀
router = APIRouter(prefix="/api")


# ==================== 健康检查 ====================

@router.get("/health")
def health() -> dict:
    """健康检查端点，返回服务状态和向量库供应商数。

    Milvus 故障时返回 503，而不是继续返回 200；
    这样负载均衡和运维探针能及时发现存储层异常。
    """
    try:
        count = get_count()  # 查询向量库中的供应商数量
        return {
            "status": "ok",  # 服务运行中
            "supplier_count": count,  # 已向量化的供应商数量
        }
    except Exception as e:  # 向量库异常兜底
        logger.error("健康检查异常: %s", e, exc_info=True)  # 记录完整堆栈
        raise HTTPException(status_code=503, detail="AI 服务存储层不可用") from e


# ==================== 供应商画像向量化 ====================

@router.post("/embed", response_model=EmbedResponse)
def embed(req: EmbedRequest) -> EmbedResponse:
    """供应商画像向量化接口

    Java 后端在供应商画像创建/更新后调用此接口，
    将画像文本编码为向量并存入 Milvus 库。
    """
    try:
        logger.info("收到画像向量化请求: tenantId=%s, text_len=%d", req.tenant_id, len(req.text))
        do_embed(req.tenant_id, req.text)  # 调用业务层：编码 + 存库
        logger.info("画像向量化成功: tenantId=%s", req.tenant_id)
        return EmbedResponse(tenant_id=req.tenant_id, status="ok")  # 成功响应
    except Exception as e:  # 网络/编码/向量库异常兜底
        logger.error("画像向量化异常: tenantId=%s, error=%s", req.tenant_id, e, exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))  # 转 HTTP 500，不崩进程


@router.post("/embed-requirement")
def embed_requirement_endpoint(req: RequirementEmbedRequest) -> dict:
    """公开需求向量化，写入 open_requirements。"""
    try:
        do_embed_requirement(req.model_dump())
        return {"requirement_id": req.requirement_id, "status": "ok"}
    except Exception as e:
        logger.error("需求向量化异常: requirementId=%s, error=%s", req.requirement_id, e, exc_info=True)
        raise HTTPException(status_code=500, detail=str(e)) from e


@router.delete("/embed-requirement/{requirement_id}")
def delete_requirement_endpoint(requirement_id: int) -> dict:
    """需求关闭/下架后删除向量。"""
    try:
        do_remove_requirement(requirement_id)
        return {"requirement_id": requirement_id, "status": "ok"}
    except Exception as e:
        logger.error("需求向量删除异常: requirementId=%s, error=%s", requirement_id, e, exc_info=True)
        raise HTTPException(status_code=500, detail=str(e)) from e


@router.post("/match-requirements", response_model=MatchRequirementsResponse)
def match_requirements_endpoint(req: MatchRequirementsRequest) -> MatchRequirementsResponse:
    """找需求：本厂画像 + SQL 候选，语义补召回后精排。"""
    try:
        results = do_match_requirements(
            req.profile.model_dump(),
            [c.model_dump() for c in req.candidates],
        )
        return MatchRequirementsResponse(results=results)
    except Exception as e:
        logger.error("找需求匹配异常: %s", e, exc_info=True)
        raise HTTPException(status_code=500, detail=str(e)) from e


# ==================== AI 智能匹配 ====================

@router.post("/match", response_model=MatchResponse)
def match_suppliers(req: MatchRequest) -> MatchResponse:
    """AI 智能匹配接口

    接收采购需求 + 候选供应商列表，返回 AI 评分排序后的结果。
    流程：语义召回 → 合并 → 过滤 → 打分；LLM 理由默认关闭
    """
    try:
        logger.info("收到匹配请求: title=%s, candidates=%d",
                     req.requirement.title, len(req.candidates))
        requirement = req.requirement.model_dump()  # Pydantic → dict
        candidates = [c.model_dump() for c in req.candidates]  # 列表转换
        if not candidates:  # 空候选池
            logger.info("匹配请求: 候选池为空，返回空结果")
            return MatchResponse(results=[])  # 返回空列表
        results = do_match(requirement, candidates, need_llm_reasons=req.need_llm_reasons)
        logger.info("匹配完成: 返回 %d 条结果", len(results))
        return MatchResponse(results=[MatchResult(**r) for r in results])  # dict → Pydantic
    except Exception as e:  # Embedding API / 向量库 / DeepSeek 异常兜底
        logger.error("匹配异常: error=%s", e, exc_info=True)  # 记录完整堆栈
        raise HTTPException(status_code=500, detail=str(e))  # 转 HTTP 500，不崩进程


# ==================== 风险预测 (Phase 2 预留) ====================

@router.post("/risk/assess", response_model=RiskResponse)
def risk_assess(req: RiskRequest) -> RiskResponse:
    """供应商风险预测 — Phase 2 实现"""
    return RiskResponse(tenant_id=req.tenant_id, risk_level="low", score=10.0, summary="风险预测功能开发中")


# ==================== 库存预警 (Phase 2 预留) ====================

@router.post("/inventory/alert", response_model=InventoryAlertResponse)
def inventory_alert(req: InventoryAlertRequest) -> InventoryAlertResponse:
    """库存/供应预警 — Phase 2 实现"""
    return InventoryAlertResponse(
        category=req.category,
        demand_count=0, supplier_count=0, ratio=0.0,
        alert="库存预警功能开发中",
    )


@router.post("/delivery/weather-advice", response_model=WeatherAdviceResponse)
def weather_advice_endpoint(req: WeatherAdviceRequest) -> WeatherAdviceResponse:
    """天气异常处置建议：风险已由 Java 规则判定，这里只写动作。"""
    try:
        lines = generate_weather_advice(req.model_dump())
        return WeatherAdviceResponse(lines=lines)
    except Exception as e:
        logger.error("天气建议异常: error=%s", e, exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))


# ==================== 采购 AI：比价分析 ====================

@router.post("/procurement/analyze-quotes", response_model=QuoteAnalysisResponse)
def analyze_quotes_endpoint(req: QuoteAnalysisRequest) -> QuoteAnalysisResponse:
    """AI 综合比价分析

    接收询盘 + 全部报价数据，返回 AI 排名、风险提示、授标建议。
    Java 后端 ProcurementController 代理调用此接口。
    """
    try:
        inquiry = req.inquiry.model_dump()
        quotes = [q.model_dump() for q in req.quotes]
        logger.info("收到比价分析请求: inquiry=%s, quotes=%d",
                     inquiry.get("inquiryNo"), len(quotes))
        result = do_analyze_quotes(inquiry, quotes)
        logger.info("比价分析成功: rankings=%d, risks=%d",
                     len(result.get("rankings", [])), len(result.get("risks", [])))
        return QuoteAnalysisResponse(**result)
    except Exception as e:
        logger.error("比价分析异常: error=%s", e, exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))


# ==================== 采购 AI：授标理由生成 ====================

@router.post("/procurement/award-reason", response_model=AwardReasonResponse)
def award_reason_endpoint(req: AwardReasonRequest) -> AwardReasonResponse:
    """AI 授标理由生成

    接收中标报价 + 其他报价对比，返回一段授标理由文本。
    """
    try:
        inquiry = req.inquiry.model_dump()
        winning = req.winningQuote.model_dump()
        others = [q.model_dump() for q in req.otherQuotes]
        logger.info("收到授标理由请求: winner=%s", winning.get("supplierCompanyName"))
        reason = do_generate_award_reason(inquiry, winning, others)
        logger.info("授标理由生成成功: len=%d", len(reason))
        return AwardReasonResponse(reason=reason)
    except Exception as e:
        logger.error("授标理由生成异常: error=%s", e, exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))


# ==================== 采购 AI：询盘内容辅助 ====================

@router.post("/procurement/inquiry-suggest", response_model=InquirySuggestResponse)
def inquiry_suggest_endpoint(req: InquirySuggestRequest) -> InquirySuggestResponse:
    """AI 询盘内容辅助

    根据品类推荐规格模板、付款条件、交期建议。
    """
    try:
        logger.info("收到询盘建议请求: category=%s", req.category)
        result = do_suggest_inquiry_fields(
            title=req.title,
            category=req.category,
            quantity=req.quantity,
            unit=req.unit,
        )
        logger.info("询盘建议生成成功: specTemplate_len=%d, paymentOptions=%d",
                     len(result.get("specTemplate", "")),
                     len(result.get("paymentSuggestions", [])))
        return InquirySuggestResponse(**result)
    except Exception as e:
        logger.error("询盘建议生成异常: error=%s", e, exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))


# ==================== AI 对话机器人：流式对话 ====================

def _sse(event: str, data: dict) -> str:
    """构造一条 SSE 事件（data: 行承载含 type 的 JSON，前端按 type 分流，Java 透传）"""
    payload = {"type": event, **data}
    return f"data: {json.dumps(payload, ensure_ascii=False)}\n\n"



@router.post("/chat")
async def chat_endpoint(req: ChatRequest) -> StreamingResponse:
    """AI 对话机器人流式回答

    流程：Embedding 提问 → Milvus 召回知识块（按租户过滤）→ DeepSeek 流式生成。
    返回 SSE 流：citations（引用来源）→ chunk（增量文本）→ done。
    引用提前推送：检索在生成前已完成，用户中途停止时半段回答仍能带上来源。
    """
    logger.info("收到对话请求: tenant_id=%s, question_len=%d, history=%d",
                req.tenant_id, len(req.question), len(req.history))

    async def generate():
        """按 citations → (tool_call/tool_result) → chunk → done 的顺序生成 SSE 事件流。"""
        try:
            # 0.1 查询改写：对短问题/代词/追问做指代消解（只用本会话历史）。
            #     改写结果仅用于内部分流/检索/工具选择，不展示给用户（前端仍显示原问题）。
            history_raw = [m.model_dump() for m in req.history]
            internal_question = rewrite_query(req.question, history_raw, req.context_entities)
            # 0. 意图预路由：元问题（身份/能力/闲聊）零检索零工具直答，
            #    避免「我是谁」「你能做什么」被上一轮业务大表劫持
            intent = classify(internal_question or req.question, bool(req.images))
            if intent.intent == "identity_self":
                yield _sse("chunk", {"content": "你是当前登录用户，可在页面右上角查看你的账号与角色。"})
                yield _sse("done", {})
                return
            if intent.intent == "identity_assistant":
                yield _sse("chunk", {"content": (
                    "我是 Global SCM AI 助手，可以帮你查询供应商、合同、询盘、交付单等业务数据，"
                    "也能执行一些需要你确认的写操作。想看完整能力可以问我「你能做什么」。"
                )})
                yield _sse("done", {})
                return
            if intent.intent == "capability":
                yield _sse("chunk", {"content": (
                    "我当前支持这些能力：\n"
                    "【查询】\n"
                    "- 供应商：按关键词搜索、查详情（资质 / 准入状态 / 尽调进度）\n"
                    "- 合同：查合同列表（状态 / 金额 / 交期）\n"
                    "- 询盘：查询盘列表（状态 / 报价截止）\n"
                    "- 交付：查交付单列表与详情（状态 / 运单 / 承诺交期 / 退货信息）\n"
                    "- 天气：按地点查实时天气与近几日预报（物流节点当地气候）\n"
                    "【操作】（都需你确认后才执行）\n"
                    "- 给供应商记备注\n"
                    "- 修改询盘、延长报价截止\n"
                    "- 提交承诺交期、填写退货物流\n"
                    "【重要操作需你手动完成】\n"
                    "- 授标 / 合同签署与磋商 / 验收交付单 / 关闭或取消询盘\n"
                    "  这些涉钱涉权的关键动作我不代办，会指引你到对应页面自己操作。\n"
                    "【其他】\n"
                    "- 基于上传文档问答、图片识别与信息抽取"
                )})
                yield _sse("done", {})
                return
            if intent.intent == "smalltalk":
                yield _sse("chunk", {"content": "你好，我是 Global SCM AI 助手，可以帮你查询供应商、合同、询盘、交付单，也能执行一些需确认的写操作。有什么可以帮你？"})
                yield _sse("done", {})
                return
            if intent.intent == "personal_fact":
                yield _sse("chunk", {"content": (
                    "已了解你的这条个人偏好/情况。当前平台没有个人偏好信息入口，我不会默认保存这类信息。"
                    "如果你希望把它作为某家供应商或采购需求的业务约束，请明确告诉我目标对象；写入前我会先给你确认。"
                )})
                yield _sse("done", {})
                return

            # 1. 统一走 Agent 工具循环：知识库是其中一种工具（search_knowledge_base），
            #    由模型自行选择要查业务数据还是查文档，不再前置关键词分流。
            #    天气问句由 chat 层强制走 get_weather（模型自行拒绝会导致否认能力）。
            image_urls = [img.url for img in req.images]
            history = history_raw  # 已在开头物化（查询改写用同一份）
            # 2.1 上下文规模观测（纯记录，不改变任何行为）：先收集真实分布，
            #     再据此定摘要触发阈值——多数会话可能连 1000 token 都不到，别拍脑袋定数字。
            ctx = chat.measure_context(internal_question, history)
            logger.info(
                "上下文规模: tenant=%s 历史条数=%d 系统=%d 历史=%d 提问=%d 合计=%d token",
                req.tenant_id, ctx["historyCount"], ctx["systemTokens"],
                ctx["historyTokens"], ctx["questionTokens"], ctx["totalTokens"],
            )
            # 2. 分流：
            #    带工具令牌且非图片 → Agent 工具循环（业务/知识工具由模型自选；
            #                        search_knowledge_base 命中时循环内推 citations）
            #    其余（纯对话/图片）→ 纯对话流式（含多模态 content 块）
            # 用改写后的问题驱动 LLM/工具（指代已消解），前端仍显示用户原话。
            answer_len = 0  # 累计回答字符数（观测用，不记内容）
            citation_count = 0  # 引用条数（来自知识库工具事件）
            if req.tool_token and not image_urls:
                async for event in chat.stream_chat_agent(
                        internal_question, history, [], req.tool_token, req.tenant_id,
                        req.history_summary):
                    if event["type"] == "chunk":
                        answer_len += len(event.get("content") or "")
                    elif event["type"] == "citations":
                        citation_count += len(event.get("sources") or [])
                    yield _sse(event["type"], {k: v for k, v in event.items() if k != "type"})
            else:
                async for text in chat.stream_chat(internal_question, history, [], image_urls,
                                                   req.history_summary):
                    answer_len += len(text or "")
                    yield _sse("chunk", {"content": text})
            # 3. 结构化观测日志（不记文档内容/密钥/敏感信息）
            logger.info(
                "对话完成: tenant=%s route=%s 原问题=%r 改写=%r 引用=%d 回答长度=%d",
                req.tenant_id, intent.intent, (req.question or "")[:60],
                (internal_question or "")[:60], citation_count, answer_len,
            )
            # 4. 结束标记
            yield _sse("done", {})
        except asyncio.CancelledError:
            logger.info("对话流被客户端取消: tenant_id=%s", req.tenant_id)
            raise
        except Exception as e:
            logger.error("对话流式生成异常: %s", e, exc_info=True)
            yield _sse("error", {"message": "AI 服务异常，请稍后重试"})
            # 异常路径也要发 done：前端按 done 收尾（停 spinner / 关流），
            # 只发 error 不发 done 会让界面一直挂到连接超时
            yield _sse("done", {})

    return StreamingResponse(
        generate(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "Connection": "keep-alive"},
    )


# ==================== AI 对话机器人：上下文压缩 ====================

@router.post("/compact", response_model=CompactResponse)
async def compact_endpoint(req: CompactRequest) -> CompactResponse:
    """把滑动窗口外的老对话压成摘要（Java 侧超阈值时异步调用）。

    增量式：传入的 old_summary + messages 合并成一份新摘要，Java 直接覆盖写回
    ai_conversations.summary。首次压缩时 old_summary 为空。
    """
    try:
        logger.info("收到压缩请求: messages=%d, old_summary_len=%d",
                    len(req.messages), len(req.old_summary or ""))
        summary = await chat.compact_history(
            req.old_summary, [m.model_dump() for m in req.messages])
        logger.info("压缩成功: summary_len=%d", len(summary))
        return CompactResponse(summary=summary)
    except Exception as e:
        logger.error("压缩异常: error=%s", e, exc_info=True)
        raise HTTPException(status_code=500, detail=str(e)) from e


# ==================== AI 对话机器人：图片描述 ====================

@router.post("/images/describe", response_model=DescribeImageResponse)
async def describe_image_endpoint(req: DescribeImageRequest) -> DescribeImageResponse:
    """生成图片内容描述（Java 侧首次把历史图纳入上下文时调用）。

    图片 token 成本是同体积文字的 10 倍，历史轮次一律不带原图；
    描述把图片压成一段文字（≈100 token），让后续轮次仍能指代「刚才那张图」。
    Java 侧只调一次并落库，后续直接读缓存。
    """
    try:
        logger.info("收到图片描述请求: url_len=%d", len(req.image_url or ""))
        desc = await describe_image(req.image_url)
        return DescribeImageResponse(description=desc)
    except Exception as e:
        logger.error("图片描述异常: error=%s", e, exc_info=True)
        raise HTTPException(status_code=500, detail=str(e)) from e


# ==================== AI 对话机器人：文档解析 ====================

@router.post("/documents/parse")
def parse_document_endpoint(req: DocumentParseRequest) -> dict:
    """文档解析：下载原始文件 → 切分 → 向量化 → 写 Milvus

    返回分块数量 + 父块列表；Java 据此回写 ai_documents.status/chunk_count，
    并把父块落 ai_doc_parent_chunks（parent-child 回填用）。
    """
    try:
        logger.info("收到文档解析请求: document_id=%s, file_type=%s", req.document_id, req.file_type)
        chunk_count, parents = rag.parse_and_index(
            document_id=req.document_id,
            tenant_id=req.tenant_id,
            oss_url=req.oss_url,
            file_name=req.file_name,
            file_type=req.file_type,
        )
        logger.info("文档解析成功: document_id=%s, chunks=%d, parents=%d",
                    req.document_id, chunk_count, len(parents))
        return {"chunk_count": chunk_count, "parents": parents}
    except Exception as e:
        logger.error("文档解析异常: document_id=%s, error=%s", req.document_id, e, exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))


# ==================== AI 对话机器人：删除文档向量 ====================

@router.delete("/documents/{document_id}/vectors")
def delete_document_vectors_endpoint(document_id: int, tenant_id: int) -> dict:
    """删除某文档的全部分块向量（文档删除时由 Java 调用；父块由 Java 侧逻辑删）"""
    try:
        logger.info("收到删除向量请求: document_id=%s, tenant_id=%s", document_id, tenant_id)
        from app.store.milvus_store import delete_by_document
        delete_by_document(str(document_id), tenant_id)
        return {"status": "ok"}
    except Exception as e:
        logger.error("删除向量异常: document_id=%s, error=%s", document_id, e, exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))
