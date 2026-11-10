"""页眉页脚 / 页码清洗。

判定逻辑（组合证据，避免误删正文）：
  1. 只有页面首/尾 ``zone_lines`` 行是候选（正文中间的行不参与）；
  2. 候选行在 ≥3 页且 ≥30% 页面重复出现 → 判为页眉/页脚；
  3. 比较前把数字归一成 ``#``，这样「第 1 页」与「第 2 页」算同一行；
  4. 显式页码格式（第 X 页 / X-Y / - X - / Page X of Y）无论重复与否都删，
     纯数字行只在页面边际区域删除，避免误删正文年份等。
"""
import math
import re
from collections import Counter
from dataclasses import dataclass, field

# 页面首尾各取几行作为页眉/页脚候选
ZONE_LINES = 3
# 至少出现在几页才判为重复（两页文档不触发，防止巧合误删）
MIN_PAGES = 3
# 重复页数占比阈值
MIN_RATIO = 0.3
# 候选行最大长度：超过这个长度是正文段落，不参与页眉页脚判定
MAX_CANDIDATE_CHARS = 120

# 显式页码格式（正文任何位置命中都可删）
_PAGE_NO_STRICT_RE = re.compile(
    r"^\s*(?:"
    r"[-—–]\s*\d{1,4}\s*[-—–]"
    r"|第\s*\d{1,4}\s*页(?:\s*[，,/]?\s*共\s*\d{1,4}\s*页)?"
    r"|\d{1,4}\s*/\s*\d{1,4}"
    r"|page\s*\d{1,4}(?:\s*(?:of|/)\s*\d{1,4})?"
    r")\s*$",
    re.IGNORECASE,
)
# 纯数字行（只在页面边际区域删除）
_PURE_NUM_RE = re.compile(r"^\s*\d{1,4}\s*$")
# 罗马数字页码（目录/前言常用 I / V / XII），只在页面边际区域删除
_ROMAN_NUM_RE = re.compile(r"^\s*[IVXLCDM]{1,6}\s*$")
# 行内的页码片段：删掉后剩余部分才能跨页比较（如「华东精密 第1页」→「华东精密」）
# 只识别真正的页码形态，避免把「第0季度营收」这类含数字正文误归一
_PAGE_FRAGMENT_RE = re.compile(
    r"(?:"
    r"第\s*\d{1,4}\s*页(?:\s*[，,/]?\s*共\s*\d{1,4}\s*页)?"
    r"|page\s*\d{1,4}(?:\s*(?:of|/)\s*\d{1,4})?"
    r"|\d{1,4}\s*/\s*\d{1,4}"
    r"|[-—–]\s*\d{1,4}\s*[-—–]"
    r")",
    re.IGNORECASE,
)
# 句末标点：命中说明更像正文，不参与页眉页脚判定
_SENTENCE_END = ("。", "！", "？", "；")
_SPACES_RE = re.compile(r"\s+")


@dataclass
class HeaderFooterStats:
    """清洗统计（写解析日志 / 告警用）。"""

    removed_lines: int = 0
    samples: list[str] = field(default_factory=list)


def strip_running_lines(page_texts: list[str], zone_lines: int = ZONE_LINES,
                        min_pages: int = MIN_PAGES,
                        min_ratio: float = MIN_RATIO) -> tuple[list[str], HeaderFooterStats]:
    """删除跨页重复的页眉页脚与页码行。

    Args:
        page_texts: 每页文本（顺序与页码一致）
        zone_lines: 页面首/尾各取几行作为候选
        min_pages: 触发重复判定的最少页数
        min_ratio: 触发重复判定的最低页面占比

    Returns:
        (清洗后的每页文本, 统计信息)
    """
    stats = HeaderFooterStats()
    if not page_texts:
        return page_texts, stats

    pages = [text.splitlines() for text in page_texts]
    repeated = _detect_repeated_keys(pages, zone_lines, min_pages, min_ratio)

    cleaned_pages: list[str] = []
    for lines in pages:
        zone = _zone_indices(lines, zone_lines)
        kept: list[str] = []
        for idx, line in enumerate(lines):
            stripped = line.strip()
            if stripped and _is_droppable(stripped, idx in zone, repeated, len(pages)):
                stats.removed_lines += 1
                if len(stats.samples) < 5:
                    stats.samples.append(stripped[:40])
                continue
            kept.append(line)
        cleaned_pages.append("\n".join(kept))

    return cleaned_pages, stats


def _is_droppable(line: str, in_zone: bool, repeated: set[str], page_count: int) -> bool:
    """判断某一行是否应删除。"""
    # 1. 显式页码格式：任何位置都删
    if _PAGE_NO_STRICT_RE.match(line):
        return True
    # 2. 纯数字行：只在页面边际区域删，避免误删正文
    if in_zone and page_count >= 2 and _PURE_NUM_RE.match(line):
        return True
    # 2.1 罗马数字页码：同样只在边际区域删
    if in_zone and page_count >= 2 and _ROMAN_NUM_RE.match(line):
        return True
    # 3. 跨页重复的页眉/页脚（只在边际区域删）
    if in_zone and _normalize(line) in repeated:
        return True
    return False


def _detect_repeated_keys(pages: list[list[str]], zone_lines: int,
                          min_pages: int, min_ratio: float) -> set[str]:
    """统计候选行在多少页的边际区域出现过，返回达到阈值的归一化文本集合。"""
    if len(pages) < min_pages:
        return set()

    threshold = max(min_pages, math.ceil(len(pages) * min_ratio))
    counts: Counter[str] = Counter()
    for lines in pages:
        seen: set[str] = set()
        for idx in _zone_indices(lines, zone_lines):
            raw = lines[idx].strip()
            # 句末带标点的行更像正文，不参与页眉页脚判定
            if raw.endswith(_SENTENCE_END):
                continue
            key = _normalize(raw)
            # 同页内同文本只计一次；过长文本不参与（更像是正文）
            if not key or len(key) > MAX_CANDIDATE_CHARS or key in seen:
                continue
            seen.add(key)
            counts[key] += 1

    # len(key) >= 2 防单字符噪声；纯页码已由 _is_droppable 兜底
    return {key for key, count in counts.items() if count >= threshold and len(key) >= 2}


def _zone_indices(lines: list[str], zone_lines: int) -> set[int]:
    """返回页面首/尾非空行的下标集合。"""
    non_empty = [i for i, line in enumerate(lines) if line.strip()]
    if not non_empty:
        return set()
    return set(non_empty[:zone_lines] + non_empty[-zone_lines:])


def _normalize(line: str) -> str:
    """归一化：去掉页码片段、压缩空白、转小写。

    只剥离真正的页码形态（第X页 / X/Y / - X - / Page X），
    不做「数字全变 #」，否则「第0季度」「第1季度」会被误判成同一行。
    """
    text = _PAGE_FRAGMENT_RE.sub(" ", line.strip())
    return _SPACES_RE.sub(" ", text).strip().lower()
