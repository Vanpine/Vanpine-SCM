"""
消息组装 — 历史裁剪、图片描述渲染、build_messages、上下文规模统计。

依赖方向：message_builder → prompts / context_budget，反向无依赖，不成环。
"""
from app.services.context_budget import _content_tokens, _enforce_total_budget
from app.services.prompts import build_system_prompt
from app.services.tokenizer import count_tokens




# 历史单条 assistant 消息保留的最大字符数。
# 只保留开头结论：长回答（天气表/询盘大表/合同清单）整段进历史会诱导模型下一轮
# 把表复述一遍（跨业务串台）。压到「一句结论」量级，表格主体整块丢弃——
# 注意是按段落切（见 _trim_assistant_answer），不是按字数硬切，避免切出半截表格。
MAX_HISTORY_MESSAGE_CHARS = 120


def _knowledge_context_text(chunks: list[dict]) -> str:
    """把检索到的知识块拼成一段带来源标注的文本（放 user 消息，不塞 system）。"""
    sections = []
    for i, c in enumerate(chunks):
        section_path = " > ".join(c.get("heading_path") or [])
        where = f"｜位置：{section_path}" if section_path else ""
        sections.append(
            f"【资料{i + 1}｜来源：{c.get('file_name', '未知文档')}{where}】\n{c.get('content', '')}"
        )
    return "\n\n".join(sections)


def _is_table_block(text: str) -> bool:
    """是否 markdown 表格块（块内有以 | 开头的行）。"""
    return any(line.lstrip().startswith("|") for line in text.splitlines())


def _trim_assistant_answer(content: str, limit: int = MAX_HISTORY_MESSAGE_CHARS) -> str:
    """压缩 assistant 长回答：保留开头结论，整块丢弃表格。

    原来按字数硬切（content[:120] + 省略号），会切出「| 数量 | 50」这种半截表格——
    模型会把 50 当成完整数字读。改为按段落累加，且遇到表格整块丢弃：
    表格里的数据模型可以重新查（本来也要求每问独立查最新数据），
    但结论句里的指代信息（「第 2 条是 pcb采集」）不能丢。

    Args:
        content: assistant 原文
        limit: 保留字符数上限

    Returns:
        压缩后的文本（未超限则原样返回）
    """
    if len(content) <= limit:
        return content
    kept: list[str] = []
    used = 0
    for para in content.split("\n\n"):
        if _is_table_block(para):
            break  # 表格整块丢弃，不切一半
        if kept and used + len(para) > limit:
            break
        kept.append(para)
        used += len(para)
    if not kept:
        # 一上来就是表格，没有可保留的结论句
        return "（上一轮回复了一个表格，内容已省略）"
    result = "\n\n".join(kept)
    # 首段是无条件保留的（`if kept and ...` 对首段恒假），所以一个用单个 \n 组织的
    # 长回答会整段留下——必须补硬截断，否则「截断」反而让结果比原文更长。
    suffix = "…（历史答案已截断）"
    budget = limit - len(suffix)
    if len(result) > budget:
        result = result[:budget]
    return result + suffix


def _image_desc_text(item: dict) -> str:
    """把一条消息的图片描述列表渲染成一行文本。

    按序号标注（附图1 / 附图2），用户问「第 2 张」时模型才能对上；
    拼成一整段不带序号就分不清指哪张。

    Args:
        item: 历史项，含 imageDescriptions: [{index, imageId, description}]

    Returns:
        形如「附图1：合同扫描件，编号 HT001；附图2：发票，金额 320 万」；无描述返回空串
    """
    descs = item.get("imageDescriptions") or []
    parts = []
    for d in descs:
        if not isinstance(d, dict):
            continue
        # Java 侧恒带 index；缺失时按已渲染条数兜底，保证总有序号
        idx = d.get("index")
        if not isinstance(idx, int) or idx <= 0:
            idx = len(parts) + 1
        text = (d.get("description") or "").strip()
        if not text:
            # 保留序号占位：直接跳过会让「第 N 张」错位，模型也会以为这张不存在，
            # 从而拿别张的内容作答——答错比答不出更糟
            text = "（该图未识别出内容）"
        parts.append(f"附图{idx}：{text}")
    return "；".join(parts)


def _history_message(item: dict) -> tuple | None:
    """把一条历史编成 LLM 消息；带图走多模态块，纯图且无文案时用占位句。

    2026-09-17 改动：
    - 含 tool_calls / tool_call_id 的消息跳过（工具 raw JSON 不进 history）
    - assistant 长答案压缩：保留开头结论、整块丢弃表格（见 _trim_assistant_answer）
    2026-10-20 改动：
    - 历史轮次不带原图，改用 Java 生成的 imageDesc（图片 token 成本是文字的 10 倍）
    """
    role = item.get("role", "")
    if role not in ("user", "assistant"):
        return None
    # 工具消息不进 history
    if item.get("tool_calls") or item.get("tool_call_id"):
        return None
    content = item.get("content") or ""
    # assistant 长答案压缩：保留开头结论，丢弃大表主体（按段落切，不切出半截表格）
    if role == "assistant" and content:
        content = _trim_assistant_answer(content)
    # 图片描述：让「刚才那张图 / 第 2 张」这类追问在历史轮次仍可定位
    desc_text = _image_desc_text(item)
    if desc_text:
        content = f"{content}\n（{desc_text}）" if content else f"（{desc_text}）"
    if not content:
        return None
    # 历史轮次不带原图：图片 token 是文字的 10 倍，而历史窗口预算只有 3000。
    # Java 侧只下发 imageDescriptions（见上），这里不再把 item["images"] 编成多模态块——
    # 之前保留该分支时，一旦 Java 往 history 里塞 images 就会静默撑爆预算。
    # 需要多模态历史时，必须连带把图片 token 计入预算后再恢复。
    return (role, content)


def build_messages(question: str, history: list[dict], chunks: list[dict],
                   images: list[str] | None = None, route: str = "agent",
                   history_summary: str = "") -> list[tuple]:
    """组装 LLM 消息列表（system + 摘要 + 历史 + 当前提问）

    2026-10 改动（P1-1）：
      - system prompt 按 route 拆分：knowledge（知识问答）/ agent（业务工具），不再混塞
      - 检索资料移到「当前 user 消息」里（而非 system），降低注意力污染

    Args:
        question: 当前用户提问（纯图片提问时由 Java 侧兜底文案填充）
        history: 历史消息 [{role, content, images?}]（升序；images 为 [{url}]）
        chunks: 检索到的知识块 [{file_name, content, ...}]
        images: 本轮提问的图片 URL 列表（最多 4 张）
        route: knowledge | agent（决定 system prompt 与资料注入方式）
        history_summary: 早前对话摘要（滑动窗口外的老对话压缩而成，空则不加）

    Returns:
        [(role, content)] 消息列表，供 langchain astream 直接使用
    """
    system_content = build_system_prompt(question, route)
    messages = [("system", system_content)]
    # 早前对话摘要：放 system 之后、最近对话之前，并明确标注是压缩过的旧信息，
    # 避免模型把它当成用户刚说的话。
    summary_message = None
    if history_summary and history_summary.strip():
        summary_message = ("user", f"[早前对话摘要]\n{history_summary.strip()}")
        messages.append(summary_message)
    # 拼接历史（限制最近若干轮，由 Java 侧控制数量；带图的 user 走多模态块）
    history_start = len(messages)
    for h in history or []:
        packed = _history_message(h)
        if packed:
            messages.append(packed)
    # 当前提问：带图时用 OpenAI 兼容的多模态 content 块数组，否则保持纯文本
    if images:
        # 本轮图以原图直接进多模态块，没有任何「第 N 张」标记——模型只能自己数，
        # 几张相似的合同扫描件很容易数错。补一句编号说明，与历史图的「附图N」对齐。
        numbered_question = (
            f"{question}\n（本次共 {len(images)} 张图，按上传顺序编号 1~{len(images)}）"
        )
        content_blocks = [{"type": "text", "text": numbered_question}]
        for url in images:
            content_blocks.append({"type": "image_url", "image_url": {"url": url}})
        messages.append(("user", content_blocks))
    else:
        # 知识问答：把检索资料随 user 消息一起给模型（不进 system，避免污染业务规则）
        if route == "knowledge" and chunks:
            user_content = (
                f"{question}\n\n"
                f"[参考资料（仅当与问题直接相关时使用）]\n{_knowledge_context_text(chunks)}"
            )
            messages.append(("user", user_content))
        else:
            messages.append(("user", question))
    # 总预算兜底：各部分上限（system / 摘要 / 历史 / 工具结果）各自成立不代表总和可控。
    # 历史是从最老的开始丢，system、摘要、当前提问永远保留（丢了没法回答本轮问题）。
    return _enforce_total_budget(messages, history_start, summary_message)


def measure_context(question: str, history: list[dict]) -> dict:
    """统计本轮送进模型的上下文规模（纯观测，不改变任何行为）。

    按 build_messages 的实际组装口径算：system prompt + **截断后的**历史 + 当前提问。
    不含工具结果——那是循环内逐轮追加的，入口处无法预估。

    用途：先跑一段时间收集真实分布，再据此定摘要触发阈值；
    不靠拍脑袋定数字（多数会话可能连 1000 token 都到不了）。

    Args:
        question: 本轮提问（已改写）
        history: 历史消息（原始，未经 _history_message 截断）

    Returns:
        {"systemTokens", "historyTokens", "questionTokens", "historyCount", "totalTokens"}
    """
    system_tokens = count_tokens(build_system_prompt(question, "agent"))
    history_tokens = 0
    history_count = 0
    for item in history or []:
        packed = _history_message(item)
        if not packed:
            continue
        # 用 _content_tokens 而非 count_tokens(_chunk_text(...))：后者漏算图片块
        history_tokens += _content_tokens(packed[1])
        history_count += 1
    question_tokens = count_tokens(question or "")
    return {
        "systemTokens": system_tokens,
        "historyTokens": history_tokens,
        "questionTokens": question_tokens,
        "historyCount": history_count,
        "totalTokens": system_tokens + history_tokens + question_tokens,
    }
