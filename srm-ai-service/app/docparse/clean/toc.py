"""目录页清洗（PDF 路径）。

目录页的特征：绝大多数行都是「章节名 + 点线/连续空格 + 页码」。
这类页面信息密度低，但章节名全都在里面，进入向量库后会成为强噪声
（用户问章节内容，检索却命中目录）。

判定采用页面级规则：
  - 单行：章节前缀 + 点线/多空格 + 页码（阿拉伯或罗马数字）；
  - 整页：目录行 ≥3 且占非空行 ≥50% → 整页丢弃。
正文页偶有一行「详见附录 A .... 123」不会触发整页丢弃。
"""
import re
from dataclasses import dataclass

# 单行目录条目：章节前缀 +（点线 或 连续空格）+ 页码
_TOC_ENTRY_RE = re.compile(
    r"^\s*(?:"
    r"第\s*[一二三四五六七八九十百零〇\d]+\s*[章节篇部]"   # 第 6 章 / 第一章
    r"|\d+(?:\.\d+){0,4}[.、．]?"                          # 6.3 / 1.2.1
    r"|[一二三四五六七八九十]+[、.．]"                      # 一、
    r"|附录[\sA-Za-z一二三四五六七八九十\d]*"               # 附录 A
    r"|参考文献|致谢|结论|摘要|abstract"                    # 固定章节名
    r")"
    r".*?"
    r"(?:[.·…]{2,}|\s{2,})\s*(?:\d{1,4}|[IVXLCDM]{1,6})\s*$",
    re.IGNORECASE,
)
# 目录行最大长度（超长更像正文段落）
MAX_TOC_LINE_CHARS = 200
# 整页判定：至少几条目录行
MIN_TOC_LINES = 3
# 整页判定：目录行占非空行的比例
TOC_PAGE_RATIO = 0.5


@dataclass
class TocStats:
    """目录清洗统计（写解析日志 / 告警用）。"""

    removed_pages: int = 0
    removed_lines: int = 0


def is_toc_line(line: str) -> bool:
    """判断单行是否像目录条目。"""
    text = (line or "").strip()
    if not text or len(text) > MAX_TOC_LINE_CHARS:
        return False
    return bool(_TOC_ENTRY_RE.match(text))


def strip_toc_pages(page_texts: list[str]) -> tuple[list[str], TocStats]:
    """整页丢弃目录页；正文页原样保留。

    Args:
        page_texts: 每页文本（顺序与页码一致）

    Returns:
        (清洗后的每页文本, 统计信息)；被丢弃的页返回空字符串
    """
    stats = TocStats()
    cleaned: list[str] = []
    for text in page_texts:
        non_empty = [line for line in text.splitlines() if line.strip()]
        toc_lines = [line for line in non_empty if is_toc_line(line)]

        # 页面级判定：目录行数量足够且占多数，才整页丢弃
        if (len(toc_lines) >= MIN_TOC_LINES
                and len(toc_lines) >= len(non_empty) * TOC_PAGE_RATIO):
            stats.removed_pages += 1
            stats.removed_lines += len(non_empty)
            cleaned.append("")
            continue
        cleaned.append(text)

    return cleaned, stats
