"""脚注剥离（P1）。

PDF 抽取的文本里，页底脚注会和正文混在一起。判定采用保守规则：
  - 只在页面最后 3 个非空行里找脚注起始标记；
  - 标记必须足够明确（[1]、①、注：、来源：、作者简介、数字+参见/来源等），
    不把「1 输入电压 AC220V」这类规格编号误判为脚注；
  - 命中标记后，该行到页末的内容全部视为脚注剥离。
"""
import re
from dataclasses import dataclass

# 只在页面尾部这么多行里找脚注
FOOTNOTE_ZONE_LINES = 3

# 脚注起始标记（保守集合，避免误伤正文编号列表）
_FOOTNOTE_MARK_RE = re.compile(
    r"^\s*(?:"
    r"\[[0-9]{1,2}\]"                      # [1]
    r"|[①-⑳]"                              # ①
    r"|[¹²³⁴⁵⁶⁷⁸⁹⁰]+"                       # 上标数字
    r"|[*＊]\s*\S"                          # * 注：...
    r"|(?:注|注释|资料来源|数据来源|来源|作者简介)\s*[:：]"  # 注：/ 来源：
    r"|\d{1,2}\s+(?:参见|见|来源|数据来源|作者|注)"          # 1 参见...
    r")"
)


@dataclass
class FootnoteStats:
    """脚注剥离统计。"""

    removed_lines: int = 0
    removed_pages: int = 0


def strip_footnotes(page_texts: list[str],
                    zone_lines: int = FOOTNOTE_ZONE_LINES) -> tuple[list[str], FootnoteStats]:
    """剥离每页尾部的脚注。

    Args:
        page_texts: 每页文本
        zone_lines: 只在页面最后 N 个非空行中查找脚注标记

    Returns:
        (清洗后的每页文本, 统计信息)
    """
    stats = FootnoteStats()
    cleaned: list[str] = []

    for text in page_texts:
        lines = text.splitlines()
        non_empty = [i for i, line in enumerate(lines) if line.strip()]
        zone = set(non_empty[-zone_lines:]) if non_empty else set()

        cut_index = None
        for idx in non_empty:
            if idx in zone and _FOOTNOTE_MARK_RE.match(lines[idx]):
                cut_index = idx
                break

        if cut_index is None:
            cleaned.append(text)
            continue

        kept = lines[:cut_index]
        removed = sum(1 for line in lines[cut_index:] if line.strip())
        stats.removed_lines += removed
        stats.removed_pages += 1
        cleaned.append("\n".join(kept))

    return cleaned, stats
