"""
AI 对话机器人流式生成服务

流程：组装 system prompt（含知识块 + 历史） → DeepSeek 流式生成 → 逐字 yield
复用 reasoning.py 的 DeepSeek 配置常量，但用 astream 异步流式（reasoning 为同步 invoke）

支持 Function Calling：带 tool_token 时每轮流式生成，无工具调用则该轮文本即最终回答。

本模块只保留「流式对话编排」两件事：stream_chat（知识/纯对话）与
stream_chat_agent（工具循环）。其余职责已拆到同目录各模块：
  prompts          提示词常量与 system prompt 拼接
  weather_query    天气问句解析（强制短路用）
  llm              LLM 客户端工厂 + 工具清单裁剪 + 引用来源转换
  context_budget   token 估算与总预算裁剪
  message_builder  历史裁剪与消息组装
  compaction       滑动窗口外的老对话压缩成摘要

文件末尾保留 re-export：测试与旧调用方仍可从 app.services.chat 导入拆出去的符号。
"""
import logging  # 日志模块
import time  # 工具调用耗时（审计 costMs）
import uuid  # 服务端强制调天气时生成 tool_call_id

from langchain_core.messages import AIMessage, ToolMessage  # 工具循环消息

from app.services.context_budget import _enforce_agent_budget  # 工具循环内预算兜底
from app.services.llm import _get_chat_llm, _get_tool_llm, _kb_citation_sources  # LLM 客户端
from app.services.message_builder import build_messages  # 消息组装
from app.services.tool_context import project_tool_result  # 工具结果进上下文前的裁剪
from app.services.tools import MAX_TOOL_ROUNDS, execute_tool, summarize  # 工具 schema + 执行
from app.services.weather_query import is_weather_question, parse_weather_query  # 天气短路

logger = logging.getLogger("srm-ai.chat")  # 模块日志器

# ==================== 向后兼容 re-export ====================
# 拆分前的符号仍从这里可导入，避免测试与既有调用方大改。
# noqa: F401 —— 这些是「有意」的未使用导入，供外部引用。
from app.services.compaction import (  # noqa: E402,F401
    COMPACT_HARD_CAP,
    COMPACT_MAX_CHARS,
    _cut_at_boundary,
    compact_history,
)
from app.services.context_budget import (  # noqa: E402,F401
    IMAGE_TOKEN_COST,
    TOOL_RESULT_ELIDE_THRESHOLD,
    TOOL_RESULT_ELIDED_SUFFIX,
    TOOLS_RESERVE_TOKENS,
    TOTAL_CONTEXT_TOKEN_BUDGET,
    _chunk_text,
    _content_tokens,
    _enforce_total_budget,
    _messages_tokens,
    _msg_content,
    _shrink_old_tool_results,
)
from app.services.llm import (  # noqa: E402,F401
    _WEATHER_TOOL_NAME,
    _DeepSeekChatOpenAI,
    _tools_for,
)
from app.services.message_builder import (  # noqa: E402,F401
    MAX_HISTORY_MESSAGE_CHARS,
    _history_message,
    _image_desc_text,
    _is_table_block,
    _knowledge_context_text,
    _trim_assistant_answer,
    measure_context,
)
from app.services.prompts import (  # noqa: E402,F401
    AGENT_STATIC_PROMPT,
    BASE_SYSTEM_PROMPT,
    DISPLAY_RULE,
    DOMAIN_HINTS,
    HISTORY_USAGE_RULE,
    KNOWLEDGE_RULE,
    KNOWLEDGE_STATIC_PROMPT,
    LIVE_WEATHER_RULE,
    MANUAL_ACTION_RULE,
    STATIC_KNOWLEDGE_RULE,
    STATIC_SYSTEM_PROMPT,
    _domain_anchor,
    build_system_prompt,
)
from app.services.tools import TOOL_SCHEMAS  # noqa: E402,F401
from app.services.weather_query import (  # noqa: E402,F401
    WEATHER_QUERY_KEYWORDS,
    parse_weather_query,
)


def _emit_round_text(pending: list[str], has_tool_calls: bool) -> list[str]:
    """决定本轮攒下的文本要不要吐给用户（工具循环防重复吐字）。

    一轮生成里模型可能先吐「我来帮你查一下。」再决定调工具。这段是过程话术，
    不是答案；若提前流式吐出去，用户就会先看到它、再看到工具结果后的真正答案。
    因此本轮文本一律先攒着，到轮末按是否要调工具决定：

    - has_tool_calls=True  → 丢弃（过程话术）
    - has_tool_calls=False → 全部吐出（这就是最终答案）

    Args:
        pending: 本轮攒下的文本片段
        has_tool_calls: 本轮是否发起了工具调用

    Returns:
        要 yield 出去的文本片段列表
    """
    if has_tool_calls:
        if pending:
            logger.info("工具轮丢弃过程话术: len=%d", sum(len(t) for t in pending))
        return []
    return list(pending)


async def stream_chat(question: str, history: list[dict], chunks: list[dict],
                      images: list[str] | None = None, history_summary: str = ""):
    """流式生成回答，逐 token yield 文本片段

    用途：knowledge 路径（知识问答，带资料）与图片/纯对话路径。

    Args:
        question: 当前提问
        history: 历史消息列表
        chunks: 检索到的知识块列表
        images: 本轮提问的图片 URL 列表
        history_summary: 早前对话摘要（滑动窗口外的老对话压缩而成）

    Yields:
        str: 增量文本片段
    """
    # 带资料 → knowledge 路由（资料进 user 消息）；纯对话/图片 → agent 路由
    route = "knowledge" if (chunks and not images) else "agent"
    messages = build_messages(question, history, chunks, images, route, history_summary)
    llm = _get_chat_llm()
    logger.info("开始流式生成: route=%s question_len=%d, chunks=%d, history=%d, images=%d",
                route, len(question), len(chunks or []), len(history or []), len(images or []))

    async for chunk in llm.astream(messages):
        content = chunk.content if hasattr(chunk, "content") else str(chunk)
        if content:
            yield content

    logger.info("流式生成完成")


async def stream_chat_agent(question: str, history: list[dict], chunks: list[dict],
                            tool_token: str, tenant_id: int | None = None,
                            history_summary: str = ""):
    """带 Function Calling 的流式回答。

    每轮直接 astream：边吐文本边累积 tool_call。
    无工具调用则正在流的字就是最终回答（不再非流式先答一遍再扔掉重生成）。
    有工具调用则不把该轮文本当答案，去查库后再流下一轮。

    知识库作为一种工具（search_knowledge_base）由模型自行选择调用；
    该工具命中后额外 yield 一条 citations 事件，供前端展示引用来源。
    """
    from langchain_core.messages import HumanMessage

    tool_llm = _get_tool_llm(question)
    chat_llm = _get_chat_llm()
    # 「北京天气」不交给模型决定是否调工具：知识库+历史拒绝会让它直接否认能力。
    forced = parse_weather_query(question)
    if forced:
        call_id = f"weather_{uuid.uuid4().hex[:12]}"
        logger.info("天气问句强制调 get_weather: city=%s, days=%s", forced["city"], forced["days"])
        yield {"type": "tool_call", "tool": "get_weather", "status": "running", "arguments": forced}
        started = time.monotonic()
        result = await execute_tool("get_weather", forced, tool_token)
        yield {"type": "tool_result", "tool": "get_weather", "summary": summarize(result),
               "success": bool(result.get("success")), "error": result.get("error"),
               "costMs": int((time.monotonic() - started) * 1000),
               "entities": result.get("entities") or []}
        messages = list(build_messages(question, history=[], chunks=[], history_summary=history_summary))
        messages.append(AIMessage(
            content="",
            tool_calls=[{"name": "get_weather", "args": forced, "id": call_id}],
        ))
        messages.append(ToolMessage(
            content=project_tool_result("get_weather", result),
            tool_call_id=call_id,
            name="get_weather",
        ))
        async for chunk in chat_llm.astream(messages):
            text = _chunk_text(getattr(chunk, "content", None))
            if text:
                yield {"type": "chunk", "content": text}
        logger.info("天气强制查询回答完成")
        return

    messages = list(build_messages(question, history, chunks, history_summary=history_summary))
    logger.info("开始 Agent 工具循环: question_len=%d, chunks=%d, history=%d, is_weather=%s",
                len(question), len(chunks or []), len(history or []), is_weather_question(question))

    streamed_final = False
    for _ in range(MAX_TOOL_ROUNDS):
        # 每轮先兜一次预算：上一轮 append 的工具结果可能已把上下文顶超，
        # 不在这里压，后面几轮会继续往上叠，直到请求被上游拒掉。
        _enforce_agent_budget(messages)
        full = None
        # 本轮先攒文本不吐：tool_call_chunks 可能出现在流中任意位置，
        # 提前 yield 出去的话，本轮稍后决定调工具时已吐的文字收不回来，
        # 用户会先看到「我来帮你查一下。」再看到真正答案（重复噪声）。
        pending: list[str] = []
        async for chunk in tool_llm.astream(messages):
            full = chunk if full is None else full + chunk
            text = _chunk_text(getattr(chunk, "content", None))
            if text:
                pending.append(text)

        tool_calls = list(getattr(full, "tool_calls", None) or []) if full else []
        content = _chunk_text(getattr(full, "content", None)) if full else ""
        if not tool_calls:
            if "invoke" in content or "DSML" in content:
                logger.warning("检测到 DSML 伪工具调用文本，追加纠正提示: len=%d", len(content))
                messages.append(HumanMessage(
                    content="请使用已绑定的 function calling 工具，或直接用中文回答；"
                            "不要把工具调用写成 <invoke> 或 DSML 标签文本。"
                ))
                continue
            # 本轮无工具调用：攒下的就是最终答案，此刻开始流式吐给用户
            for text in _emit_round_text(pending, has_tool_calls=False):
                yield {"type": "chunk", "content": text}
            streamed_final = True
            break

        # 本轮有工具调用：pending 只是过程话术（「我来帮你查一下」），丢弃不吐
        for text in _emit_round_text(pending, has_tool_calls=True):
            yield {"type": "chunk", "content": text}  # 当前恒为空，保留分支便于将来改成状态提示
        messages.append(AIMessage(content=content, tool_calls=tool_calls))
        for tc in tool_calls:
            name = tc.get("name", "") if isinstance(tc, dict) else getattr(tc, "name", "")
            args = tc.get("args", {}) if isinstance(tc, dict) else getattr(tc, "args", {})
            call_id = tc.get("id", "") if isinstance(tc, dict) else getattr(tc, "id", "")
            logger.info("模型发起工具调用: tool=%s, args=%s", name, args)
            yield {"type": "tool_call", "tool": name, "status": "running", "arguments": args}
            started = time.monotonic()
            result = await execute_tool(name, args, tool_token, tenant_id)
            cost_ms = int((time.monotonic() - started) * 1000)
            # 知识库工具命中 → 额外推 citations 事件，让前端展示引用来源
            if name == "search_knowledge_base" and result.get("success"):
                hits = result.get("result") or []
                sources = _kb_citation_sources(hits)
                if sources:
                    yield {"type": "citations", "sources": sources}
            if result.get("requireConfirm"):
                yield {"type": "chunk", "content": "已发起操作，请确认执行。"}
                yield {
                    "type": "confirm_required",
                    "tool": name,
                    "proposalId": result.get("proposalId"),
                    "actionSummary": result.get("actionSummary"),
                    # 写操作对象也进上下文：下一轮「再给它延长一次」可直接定位
                    "entities": result.get("entities") or [],
                }
                return
            yield {"type": "tool_result", "tool": name, "summary": summarize(result),
                   "success": bool(result.get("success")), "error": result.get("error"),
                   "costMs": cost_ms,
                   "entities": result.get("entities") or []}
            messages.append(ToolMessage(
                content=project_tool_result(name, result),
                tool_call_id=call_id,
                name=name,
            ))
    else:
        logger.warning("工具循环达到最大轮数 %d，强制收尾", MAX_TOOL_ROUNDS)

    if not streamed_final:
        async for chunk in chat_llm.astream(messages):
            text = _chunk_text(getattr(chunk, "content", None))
            if text:
                yield {"type": "chunk", "content": text}

    logger.info("Agent 回答完成")
