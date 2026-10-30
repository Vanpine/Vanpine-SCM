"""
查询改写与指代消解

把「代词问题 / 序数指代」改写成可独立检索的完整问题，提升
embedding / rerank / 工具选择的命中率。例如：
    「他呢」          → 「查询刚才提到的供应商的详情」
    「第一条详情」    → 「查询刚才列表中第一条详情」
    「上面那个合同状态」→ 「查询刚才提到的合同的状态」

只做「无歧义的指代消解」——仅在问题里出现代词/序数时才改，改写结果是干净的
完整问句（无旁白前缀）。无指代线索的短问一律不改（宁可不改，不改错）：
曾经「短句硬拼上一问」的做法会把独立短问（如「看看我的简历」）污染成上一问语义，
导致模型选错工具，已移除。

约束：
  - 只用当前会话最近几轮历史，不跨会话
  - 改写结果只用于内部（embedding/rerank/工具选择），不展示给用户
  - 先确定性规则；规则不足以消解时保留原问题（宁可不改，不改错）

可选：若配置 QUERY_REWRITE_LLM=true，则在规则无法消解时调一次 LLM
（temperature 0.1）兜底；默认关闭，保持零额外延迟。
"""
import logging
import os
import re

logger = logging.getLogger("srm-ai.query_rewriter")

# 是否启用 LLM 兜底改写（默认关闭：规则够用且零延迟）
LLM_REWRITE_ENABLED = os.environ.get("QUERY_REWRITE_LLM", "false").lower() in ("1", "true", "yes")

# 代词 / 指代词（出现即视为需要消解）
_PRONOUNS = (
    "他", "她", "它", "他们", "它们", "该供应商", "这家", "那家", "这家供应商", "那家供应商",
    "上面那个", "上面那", "刚才那个", "刚才那", "刚刚那个", "上一个", "这个", "那个", "它家",
    # 补充常用指代（按长度降序优先匹配长词，避免「该公司」被「该」截断）
    "该公司", "这家公司", "那家公司", "该合同", "该询盘", "该交付单", "该订单",
    "前者", "后者", "两者", "此单", "此合同", "其状态", "其详情",
)
# 单字指代（该 / 此 / 其）只在「后接业务对象名词」时才算代词：
#   「该合同」是指代；「应该查合同吗」的「该」是副词；「其他供应商」的「其」是固定搭配；
#   「因此 / 如此 / 就此」的「此」同理。
# 不做这个限制会让无指代关系的问句被替换成乱句（如「应该查合同吗」→「钢材合同查合同吗」），
# 进而选错工具。
_SINGLE_PRONOUN_RE = re.compile(
    r"[该此其](?=供应商|合同|询盘|交付单|订单|单据|公司|状态|详情|信息|列表|单)"
)

# 序数指代（"第一条""第2个"）→ 用于解析出第 N 个，按序号定位实体
_ORDINAL_HINTS = ("第一条", "第二条", "第三条", "第1条", "第2条", "第3条", "第一个", "第二个",
                  "第三个", "第1个", "第2个", "第3个", "最后一条", "最后那个")

# 从「第X条/第X个」抽出序号：支持阿拉伯数字与中文数字
_ORDINAL_NUM_RE = re.compile(r"第\s*([一二三四五六七八九十\d]+)\s*[条个]")
_CN_NUM = {"一": 1, "二": 2, "三": 3, "四": 4, "五": 5,
           "六": 6, "七": 7, "八": 8, "九": 9, "十": 10}

# 「另一条」指代：指不是当前那条的另一条（取列表第二个）
_ANOTHER_HINTS = ("另一条", "另一个")


def _parse_ordinal_index(q: str) -> int | None:
    """从问题里解析「第 N 条/个」的 N（1-based）；「最后一条/那个」返回 -1；无则 None。

    -1 作为「取最后一个」的哨兵，供调用方索引 context_entities。
    """
    if "最后一条" in q or "最后那个" in q:
        return -1
    m = _ORDINAL_NUM_RE.search(q)
    if not m:
        return None
    token = m.group(1)
    if token.isdigit():
        return int(token)
    # 中文数字：仅处理 1-10（够本场景用；「十一」等超范围返回 None 走退化）
    return _CN_NUM.get(token)

# 对象名词 → 用于补全"的详情/的状态"的宾语
_OBJECT_NOUNS = ("供应商", "合同", "询盘", "交付单", "交付", "订单", "报告", "需求", "报价")


def _last_assistant_text(history: list[dict]) -> str:
    """取最近一条 assistant 消息文本（用于判断"他/那个"指谁）。"""
    for h in reversed(history or []):
        if h.get("role") == "assistant":
            c = h.get("content") or ""
            if isinstance(c, list):
                c = " ".join(b.get("text", "") for b in c if isinstance(b, dict))
            return str(c)
    return ""


def _infer_object_from_history(history: list[dict]) -> str | None:
    """从最近历史里猜主体对象类型（供应商/合同/询盘/交付单…）。"""
    text = _last_assistant_text(history)
    if not text:
        return None
    # 按出现位置取最后提到的对象（越靠后越可能是当前话题）
    best, best_pos = None, -1
    for noun in _OBJECT_NOUNS:
        pos = text.rfind(noun)
        if pos > best_pos:
            best, best_pos = noun, pos
    return best


def parse_entities(context_entities: str | list | None) -> list[dict]:
    """解析会话业务对象上下文（JSON 字符串或已解析列表）→ 列表。

    容忍空串/非法 JSON：解析失败返回空列表（不影响主流程）。
    """
    if not context_entities:
        return []
    if isinstance(context_entities, list):
        return [e for e in context_entities if isinstance(e, dict)]
    try:
        import json
        data = json.loads(context_entities)
        return [e for e in data if isinstance(e, dict)] if isinstance(data, list) else []
    except Exception:
        logger.debug("context_entities 解析失败，忽略")
        return []


def _entity_display(e: dict) -> str:
    """业务对象的可读指称（优先 title，其次 objectType 中文名）。"""
    title = (e.get("title") or "").strip()
    if title:
        return title
    return _TYPE_CN.get(e.get("objectType", ""), "该对象")


def _entity_by_ordinal(entities: list[dict], idx: int | None) -> str | None:
    """按序数 idx 取实体指称。idx: 1-based 序号；-1 表示最后一个；None 取第一个。

    越界返回 None——调用方据此保持原句不改写。曾回退「最后一条」，
    但用户说「第三条」而列表只有两条时，指到第二条是答错，比不改写更糟。
    """
    if not entities:
        return None
    if idx is None:
        return _entity_display(entities[0])
    if idx == -1:
        return _entity_display(entities[-1])
    if 1 <= idx <= len(entities):
        return _entity_display(entities[idx - 1])
    # 越界：不猜，交给调用方保持原句
    logger.info("序数越界: 请求第%s条, 实际仅%s条, 保持原句不改写", idx, len(entities))
    return None


# 对象类型 → 中文名（补全"的详情"时的宾语）
_TYPE_CN = {
    "supplier": "供应商", "contract": "合同", "inquiry": "询盘",
    "delivery": "交付单", "order": "订单", "requirement": "需求", "quote": "报价",
}


def rewrite(question: str, history: list[dict] | None = None,
            context_entities: str | list | None = None) -> str:
    """改写问题，返回用于检索/工具选择的完整问题。

    优先用 context_entities（Java 提供的精确业务对象）消解指代；
    缺失时退化为从 history 文本猜对象；再不行原样返回（宁可不改，不改错）。
    仅供内部使用，不展示给用户。

    Args:
        question: 用户原始提问
        history: 历史消息 [{role, content, ...}]（升序，最近几轮）
        context_entities: 会话业务对象上下文（JSON 字符串或列表）

    Returns:
        改写后的问题（可能与原问题相同）
    """
    q = (question or "").strip()
    if not q:
        return q

    history = history or []
    entities = parse_entities(context_entities)
    has_pronoun = any(p in q for p in _PRONOUNS) or bool(_SINGLE_PRONOUN_RE.search(q))
    ordinal_index = _parse_ordinal_index(q)
    has_ordinal = ordinal_index is not None
    has_another = any(h in q for h in _ANOTHER_HINTS)

    # 无代词/序数/「另一条」指代 → 不改写（完整问题与无指代短问都走这里，零开销）
    if not (has_pronoun or has_ordinal or has_another):
        return q

    # 序数指代 + 有 entities：按「第 N 条」的 N 定位实体（-1 = 最后一个）
    if entities and has_ordinal:
        target = _entity_by_ordinal(entities, ordinal_index)
        # 越界（如说「第三条」但只有两条）：target=None，不改写，避免指错对象
        if target is not None:
            rewritten = f"查询{target}{q}" if not q.startswith("查询") else q
            logger.info("序数指代(entities): %r → %r [序号=%s→%s]", q, rewritten, ordinal_index, target)
            return rewritten
        return q

    # 「另一条」+ 有 entities：取不是当前对象（第一条）的那条；
    # 只有一条时无法定位，保持原句不改（宁可不改，不改错）
    if entities and has_another:
        if len(entities) >= 2:
            target = _entity_display(entities[1])
            rewritten = _complete_if_needed(_replace_another(q, target))
            logger.info("另一条指代(entities): %r → %r [对象=%s]", q, rewritten, target)
            return rewritten
        return q

    # 代词 + 有 entities（最精确）
    if entities and has_pronoun:
        target = _entity_display(entities[0])   # 最近操作的对象
        rewritten = _replace_pronoun(q, target)
        rewritten = _complete_if_needed(rewritten)
        logger.info("指代消解(entities): %r → %r [对象=%s]", q, rewritten, target)
        return rewritten

    # 退化：从 history 猜对象（原逻辑）
    obj = _infer_object_from_history(history)

    if has_pronoun and obj:
        replaced = q
        for p in _PRONOUNS:
            if p in replaced:
                replaced = replaced.replace(p, f"刚才提到的{obj}")
                break
        rewritten = _complete_if_needed(replaced)
        logger.info("指代消解(history): %r → %r", q, rewritten)
        return rewritten

    if has_ordinal:
        rewritten = f"查询刚才列表中{q}" if not q.startswith("查询") else q
        logger.info("序数指代改写: %r → %r", q, rewritten)
        return rewritten

    # 注意：曾在此处做「短追问硬拼上一问」（`（延续上一问：xxx）当前问`）。
    # 已删除——条件太松（短句 + 历史任意位置出现过对象名词就触发），会把无指代关系的
    # 独立短问（如「看看我的简历」）拼成上一问语义，污染喂给模型的问题 → 模型选错工具、
    # 答非所问（实测致知识库检索不再触发）。无指代线索的短问一律不改（宁可不改，不改错）。
    return q


def _replace_pronoun(q: str, target: str) -> str:
    """把问题里首个（按字符串位置）出现的代词替换成具体对象名。

    按位置选最早出现的代词，而非按词表顺序——否则「这家供应商的合同和它的状态」
    会先命中词表里靠前的词，替换位置与用户语义焦点不符。
    多字词优先（降序）：避免「该公司」被单字「该」截断。
    单字代词只在「后接业务对象名词」时才算（见 _SINGLE_PRONOUN_RE），
    否则「应该查合同吗」的「该」会被误当指代。
    """
    best_pos = -1
    best_word = ""
    for p in sorted(_PRONOUNS, key=len, reverse=True):
        pos = q.find(p)
        if pos != -1 and (best_pos == -1 or pos < best_pos):
            best_pos = pos
            best_word = p
    for m in _SINGLE_PRONOUN_RE.finditer(q):
        if best_pos == -1 or m.start() < best_pos:
            best_pos = m.start()
            best_word = m.group()
    if not best_word:
        return q
    # 按位置切片替换：单字代词可能在前文有「不算指代」的同形字（如「应该」的「该」），
    # 用 replace 会换错那一处
    return q[:best_pos] + target + q[best_pos + len(best_word):]


def _replace_another(q: str, target: str) -> str:
    """把「另一条 / 另一个」替换成具体对象名。

    用 replace 而非正则：字面词无需转义，且只换第一处，保留原句其余部分。
    """
    for hint in _ANOTHER_HINTS:
        if hint in q:
            return q.replace(hint, target, 1)
    return q


def _complete_if_needed(text: str) -> str:
    """无谓语的碎问句（如「他呢」「上面那个」）补成「…的详情」。"""
    if not _has_predicate(text):
        return f"查询{text.rstrip('呢？?')}的详情"
    return text


def _has_predicate(text: str) -> bool:
    """粗略判断句子里是否已有谓语（查/查询/看/状态/详情/多少…），无则需补。"""
    verbs = ("查", "看", "状态", "详情", "多少", "怎么", "如何", "什么", "什么时候", "谁")
    return any(v in text for v in verbs)
