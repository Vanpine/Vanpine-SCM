"""
对话压缩 — 滑动窗口外的老对话 → 摘要。

增量式：旧摘要 + 这批老消息 → 一份新摘要。由 Java 侧在上下文超阈值时异步调用，
压缩失败由调用方兜底，本模块只管压。
"""
import logging

from app.services.context_budget import _chunk_text
from app.services.llm import _get_chat_llm
from app.services.message_builder import _image_desc_text

logger = logging.getLogger("srm-ai.compaction")



# ==================== 对话压缩（滑动窗口外的老对话 → 摘要） ====================

# 提示词要求的目标长度（字）。摘要只是「用户目标 / 已确认决定 / 约束」的备忘，
# 不留查询明细——明细随时可重查，写进摘要只会把真正不可重算的信息挤出去。
COMPACT_MAX_CHARS = 600
# 代码硬上限（字）：模型不听话时兜底截断，且按句边界收口，不切出半句
COMPACT_HARD_CAP = 800

_COMPACT_SYSTEM_PROMPT = "你是对话压缩器，只输出摘要正文，不加标题、不寒暄。"

_COMPACT_INSTRUCTION = """把下面的对话压缩成一份长期备忘，不是复述对话内容。

输出结构（总共不超过 {limit} 字）：
- 用户身份与目标：
- 已确认的结论 / 决定：
- 约束与偏好：
- 未完成的待办：

规则：
- 禁止罗列查询结果（合同/询盘/交付单的清单、表格、逐条明细）。这些数据随时可以重新查询，
  写进来只会挤掉真正有价值的信息。
- 只保留「用户明确要记住、或据此做过决定」的业务对象，最多 3 个，
  格式：类型 + 编号 + 标题（如「询盘 RFQ20261001《PCB采集》」）。
- 用户明确表达的目标、约束、偏好必须保留（如「只看已过期的」「我是采购方」）。
- 用户已确认的决定必须保留（如「这单不做了」「改到 11 月」）。
- 不要写助手的建议、不要写「助手认为」。
- 与【已有摘要】合并去重，不要分开写，不要分段重复。
直接输出备忘正文。"""


async def compact_history(old_summary: str, messages: list[dict]) -> str:
    """把老消息压成摘要（增量：旧摘要 + 新消息 → 一份新摘要）。

    由 Java 侧在上下文超阈值时异步调用；压缩失败由调用方兜底，本函数只管压。

    Args:
        old_summary: 已有摘要，空表示首次压缩
        messages: 待压缩的老消息 [{role, content}]（升序）

    Returns:
        合并后的新摘要；没有可压内容时原样返回 old_summary
    """
    lines = []
    for m in messages or []:
        content = (m.get("content") or "").strip()
        # 图片描述随消息一起进压缩：历史不带原图，描述是图信息的唯一载体，丢了就永久丢失
        desc_text = _image_desc_text(m)
        if desc_text:
            content = f"{content}（{desc_text}）" if content else f"（{desc_text}）"
        if not content:
            continue
        role = "用户" if m.get("role") == "user" else "助手"
        lines.append(f"{role}：{content}")
    if not lines:
        return old_summary or ""

    parts = [_COMPACT_INSTRUCTION.format(limit=COMPACT_MAX_CHARS)]
    if old_summary and old_summary.strip():
        parts.append(f"【已有摘要】\n{old_summary.strip()}")
    parts.append(f"【新增对话】\n" + "\n".join(lines))

    llm = _get_chat_llm()
    response = await llm.ainvoke([("system", _COMPACT_SYSTEM_PROMPT), ("user", "\n\n".join(parts))])
    summary = _chunk_text(getattr(response, "content", None)).strip()
    # 字符上限只写在提示词里不可靠，模型超字数时必须在返回前截断：
    # 摘要每轮都随历史一起送模型，超出预算等于多吃一份上下文
    original_len = len(summary)
    summary = _cut_at_boundary(summary, COMPACT_HARD_CAP)
    if original_len > COMPACT_HARD_CAP:
        logger.warning("摘要超硬上限被截断: %d -> %d 字（提示词要求 %d，模型未遵守）",
                       original_len, len(summary), COMPACT_MAX_CHARS)
    logger.info("对话压缩完成: 消息=%d 旧摘要=%d字 新摘要=%d字",
                len(lines), len(old_summary or ""), len(summary))
    return summary


def _cut_at_boundary(text: str, limit: int) -> str:
    """超长时在句末/换行处收口，避免切出半句话。

    截断本身是兜底（提示词已要求 {limit} 字内），但必须切在可读边界——
    切出「报价截止2026-」这种半截内容会让模型把残缺当事实。

    Args:
        text: 待截断文本
        limit: 字符上限

    Returns:
        截断后的文本（带省略号）；未超限时原样返回
    """
    if len(text) <= limit:
        return text
    cut = text[:limit]
    # 从后往前找句子/段落边界，落在末 40% 才采用（否则宁可硬切也不只剩一句）
    for sep in ("\n", "。", "；", "！", "？"):
        pos = cut.rfind(sep)
        if pos > limit * 0.6:
            return cut[:pos + 1] + "…"
    return cut + "…"

