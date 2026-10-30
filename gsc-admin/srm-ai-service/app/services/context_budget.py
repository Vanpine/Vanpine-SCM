"""
上下文预算 — token 估算、总预算裁剪、工具结果压缩。

预算常量集中在本模块，所有读取方（build_messages / 工具循环）都从这里取，
保证「改一处即全局生效」，不会出现两份阈值漂移。
"""
import logging

from langchain_core.messages import ToolMessage  # 工具结果消息（压缩时按类型筛）

from app.services.tokenizer import count_tokens  # token 估算（与 Java TokenEstimator 同源）

logger = logging.getLogger("srm-ai.context-budget")



# ==================== 总上下文预算 ====================
# 各部分（system / 摘要 / 历史 / 工具结果）都有各自上限，但总和仍需一条总闸：
# 任何一处配置漂移或上游换实现，都可能让总输入无界增长。
TOTAL_CONTEXT_TOKEN_BUDGET = 16000
# tools schema 不在 messages 里，组装时量不到，按实测（15 个工具约 5200）预留
TOOLS_RESERVE_TOKENS = 6000
# 单张图片的 token 估算（保守取上限）。各家按分辨率计费口径不同，取 1500 是为了不低估——
# 低估会让总预算失效，高估只是少带一点历史。图片必须计入，否则 4 张图 ≈ 6000 token
# 白占预算，总闸形同虚设。
IMAGE_TOKEN_COST = 1500
# 工具循环内，早期轮次的工具结果超过这个长度就压成一行（保留最近 1 轮原文）
TOOL_RESULT_ELIDE_THRESHOLD = 200
TOOL_RESULT_ELIDED_SUFFIX = "…（早期工具结果已省略）"


def _enforce_total_budget(messages: list[tuple], history_start: int,
                          summary_message: tuple | None) -> list[tuple]:
    """超出总预算时从最老历史开始丢；丢完后仍超则丢摘要。

    预算口径是「messages 文本」，不含 tools schema（调用方加得上就加），
    因此实际阈值留了余量：TOOLS_RESERVE_TOKENS。

    Args:
        messages: 已组装好的消息（system 在前，当前提问在最后）
        history_start: 历史消息起始下标（之前是 system + 摘要）
        summary_message: 摘要消息（可能为 None），最后才会被丢

    Returns:
        裁剪后的消息列表；system 与当前提问永远保留
    """
    def total() -> int:
        return _messages_tokens(messages)

    limit = TOTAL_CONTEXT_TOKEN_BUDGET - TOOLS_RESERVE_TOKENS
    if total() <= limit:
        return messages
    # 1. 从最老历史开始丢，直到进预算；至少保留 system + 当前提问
    while total() > limit and len(messages) - 1 > history_start:
        del messages[history_start]
        logger.info("总上下文超预算，丢弃最老一条历史: 剩余 %d token", total())
    # 2. 历史丢完仍超 → 丢摘要
    if total() > limit and summary_message is not None and summary_message in messages:
        messages.remove(summary_message)
        logger.warning("总上下文超预算，历史已清空，已丢弃摘要: %d token", total())
    if total() > limit:
        # 3. 只剩 system + 当前提问：当前提问有 Java 侧 2000 token 上限，不再继续丢
        logger.warning("总上下文超预算但已无历史可丢: %d token", total())
    # 4. 丢历史后可能出现「窗口开头是 assistant」的悬空回答，剥掉直到以 user 开头
    #    （只动窗口开头，当前提问永远保留）
    while len(messages) - 1 > history_start and messages[history_start][0] == "assistant":
        del messages[history_start]
    return messages


def _chunk_text(content) -> str:
    """把 LLM chunk/message 的 content 收成纯文本（兼容 str 与多模态块列表）。"""
    if not content:
        return ""
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts = []
        for block in content:
            if isinstance(block, str):
                parts.append(block)
            elif isinstance(block, dict):
                parts.append(block.get("text") or "")
        return "".join(parts)
    return str(content)


def _msg_content(message) -> object:
    """取一条消息的 content，兼容 (role, content) 元组与 LangChain 消息对象。

    工具循环里两种形态混存：build_messages 产出元组，循环内 append 的是
    AIMessage / ToolMessage 对象。预算统计必须两种都认。
    """
    if isinstance(message, tuple):
        return message[1] if len(message) > 1 else ""
    return getattr(message, "content", "") or ""


def _content_tokens(content) -> int:
    """估算一段 content 的 token：文本按 tokenizer，图片按 IMAGE_TOKEN_COST。

    图片必须单独计：`_chunk_text` 只取 text 块，image_url 块贡献 0——
    4 张图 ≈ 6000 token 会被当成 0，总预算闸门形同虚设。
    """
    if isinstance(content, list):
        used = 0
        for block in content:
            if isinstance(block, dict):
                if block.get("type") == "image_url":
                    used += IMAGE_TOKEN_COST
                else:
                    used += count_tokens(block.get("text") or "")
            elif isinstance(block, str):
                used += count_tokens(block)
        return used
    return count_tokens(content or "")


def _messages_tokens(messages) -> int:
    """估算一组消息的总 token（含图片与工具结果）。"""
    return sum(_content_tokens(_msg_content(m)) for m in messages)


def _shrink_old_tool_results(messages: list, keep_recent: int = 1) -> int:
    """把较早轮次的工具结果压成一行，保留最近 keep_recent 轮原文。

    为什么不直接删消息：OpenAI 协议要求 AIMessage(tool_calls) 必须紧跟其 ToolMessage，
    删任一边请求就非法。改 content 则结构不变，只减体积。

    Args:
        messages: 工具循环的完整消息列表（就地修改）
        keep_recent: 保留最近几轮工具结果的原文

    Returns:
        释放的字符数（观测用）
    """
    tool_messages = [m for m in messages if isinstance(m, ToolMessage)]
    victims = tool_messages[:-keep_recent] if keep_recent > 0 else tool_messages
    saved = 0
    for m in victims:
        text = m.content if isinstance(m.content, str) else _chunk_text(m.content)
        if len(text) > TOOL_RESULT_ELIDE_THRESHOLD:
            saved += len(text) - TOOL_RESULT_ELIDE_THRESHOLD
            m.content = text[:TOOL_RESULT_ELIDE_THRESHOLD] + TOOL_RESULT_ELIDED_SUFFIX
    return saved


def _enforce_agent_budget(messages: list) -> None:
    """工具循环内的预算兜底：超预算就把早期工具结果压成一行。

    入口处的 _enforce_total_budget 管不到循环内——每轮 append 一条 ToolMessage
    （单条上限 6000 字符），4 轮累积可远超总预算，且全程无二次校验。
    """
    limit = TOTAL_CONTEXT_TOKEN_BUDGET - TOOLS_RESERVE_TOKENS
    used = _messages_tokens(messages)
    if used <= limit:
        return
    saved = _shrink_old_tool_results(messages, keep_recent=1)
    logger.info("工具循环超预算，压缩早期工具结果: 用量=%d/%d, 释放=%d 字符",
                used, limit, saved)

