"""PDF 文本修复（P1）：行尾连字符 + 跨页段落拼接。

PDF 的换行是版式换行，不是语义换行：
  - 英文行尾连字符会把单词劈成两半（manage- / ment），BM25 命不中；
  - 段落跨页时，一句话被页边界切成两个块，上下文断裂。
这里用保守规则修复，避免把「确实是两段」的内容误拼。
"""
import re

# 英文小写字母 + 连字符/软连字符 + 换行 + 小写字母 → 去掉连字符直接拼接
_HYPHEN_BREAK_RE = re.compile(r"([A-Za-z])[-­]\n([a-z])")

# 句末标点（含中英文）：以这些结尾说明段落已完整，不再跨页拼接
_TERMINAL_CHARS = set("。！？；.!?;:：\"”’）)】》」』")

# 下一页首行若像标题/条款/编号，说明是新结构，不拼接
_HEADING_LIKE_RE = re.compile(
    r"^\s*(?:"
    r"第\s*[一二三四五六七八九十百零〇\d]+\s*[条章节款项]"
    r"|\d+(?:\.\d+)*[、.．)]?\s"
    r"|[一二三四五六七八九十]+、"
    r"|#"
    r")"
)

# 前块至少这么长才考虑拼接（过短更像标题/残句）
MIN_PREV_CHARS = 20


def join_hyphenated_lines(text: str) -> tuple[str, int]:
    """修复英文行尾连字符断词。

    Args:
        text: 单页文本

    Returns:
        (修复后的文本, 修复处数)
    """
    fixed, count = _HYPHEN_BREAK_RE.subn(r"\1\2", text or "")
    return fixed, count


def merge_cross_page_blocks(blocks: list, min_prev_chars: int = MIN_PREV_CHARS) -> tuple[list, int]:
    """拼接跨页被切断的正文段落。

    只处理「上一页最后一个正文块 + 下一页第一个正文块」：
      - 同 heading_path；
      - 上一块不以句末标点结尾；
      - 下一块以中文/小写字母/承接标点开头，且不像标题/条款；
      - 上一块长度足够（排除标题/残句）。

    Args:
        blocks: 解析出的 Block 列表（按阅读顺序）
        min_prev_chars: 上一块最小字符数

    Returns:
        (合并后的 Block 列表, 合并处数)
    """
    out: list = []
    merged_count = 0
    last_page = None

    for block in blocks:
        page = getattr(block, "page", None)
        # 只有「新页面的第一个块」才有资格与上一页末尾拼接
        first_on_page = page is not None and last_page is not None and page != last_page

        if first_on_page and out and _should_merge(out[-1], block, min_prev_chars):
            prev = out[-1]
            prev.text = _join_text(prev.text, block.text)
            merged_count += 1
            last_page = page
            continue

        out.append(block)
        if page is not None:
            last_page = page

    return out, merged_count


def _should_merge(prev, cur, min_prev_chars: int) -> bool:
    """判断跨页两块是否应拼接。"""
    if getattr(prev, "kind", "") != "para" or getattr(cur, "kind", "") != "para":
        return False
    if getattr(prev, "page", None) is None or getattr(cur, "page", None) is None:
        return False
    if list(prev.heading_path) != list(cur.heading_path):
        return False
    if len(prev.text.strip()) < min_prev_chars:
        return False
    if not _ends_open(prev.text):
        return False
    return _starts_continuation(cur.text)


def _ends_open(text: str) -> bool:
    """上一块是否「没说完」（末尾不是句末标点）。"""
    stripped = (text or "").rstrip()
    return bool(stripped) and stripped[-1] not in _TERMINAL_CHARS


def _starts_continuation(text: str) -> bool:
    """下一块是否像上一句的延续（而不是新标题/新段落）。"""
    stripped = (text or "").lstrip()
    if not stripped or _HEADING_LIKE_RE.match(stripped):
        return False
    ch = stripped[0]
    return ch.islower() or _is_cjk(ch) or ch in "，、；：）)]】》」』"


def _is_cjk(ch: str) -> bool:
    """判断是否为中日韩字符。"""
    return "一" <= ch <= "鿿" or "㐀" <= ch <= "䶿"


def _join_text(prev_text: str, next_text: str) -> str:
    """拼接两段文本：英文跨页补空格，中文直接相连；连字符断词去掉连字符。"""
    head = (prev_text or "").rstrip()
    tail = (next_text or "").lstrip()
    if not head:
        return tail
    if not tail:
        return head
    # 跨页处再次出现连字符断词
    if head.endswith(("-", "‐", "‑")):
        return head[:-1] + tail
    # 英文之间补空格，中文直接相连
    if head[-1].isascii() and tail[0].isascii():
        return head + " " + tail
    return head + tail
