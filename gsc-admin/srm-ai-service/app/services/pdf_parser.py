"""
PDF 解析器

用 pypdf 逐页抽文本并保留页码；标题识别优先用书签（大纲），
其次字号启发式（pdfplumber，字号 ≥ 正文 ×1.15 且行长 ≤40），
再退中文条款正则。表格用 pdfplumber 受控提取。

刻意不用 pypdf 的 extraction_mode='layout'：它靠插空格对齐列，
会把单栏中文合同的缩进条款、双列签章块渲染出连续空格，
再被表格启发式误判切开句子——对最常见的 PDF 合同是主动降精准。

深解析（pdfplumber 字号/表格）由 PDF_DEEP_PARSE 开关控制，
默认关闭走纯 pypdf（快、稳）；开启后单文档页数上限 PDF_MAX_PAGES，
超限或解析失败自动降级纯文本。

输出与 chunker.Block 一致。
"""
import io
import logging
import os

from app.services.chunker import Block
from app.services.tokenizer import count_tokens

logger = logging.getLogger("srm-ai.pdf")

# 深解析开关（pdfplumber 字号/表格）：默认关闭，纯 pypdf 更快更稳
_DEEP_PARSE = os.environ.get("PDF_DEEP_PARSE", "false").lower() in ("1", "true", "yes")
# 单文档解析页数上限（防超大 PDF 拖垮同步链路）
_MAX_PAGES = int(os.environ.get("PDF_MAX_PAGES", "200"))
# 标题行最大字符数
_HEADING_MAX_CHARS = 40
# 字号判定：≥ 正文中位字号 × RATIO
_HEADING_SIZE_RATIO = 1.15


def extract_pdf_blocks(raw_bytes: bytes) -> list[Block]:
    """把 PDF 字节解析为结构块列表。

    Args:
        raw_bytes: PDF 文件字节

    Returns:
        Block 列表（heading/para/clause/table，带 page）

    Raises:
        RuntimeError: pypdf 未安装
        ValueError: 文档无法解析
    """
    try:
        from pypdf import PdfReader
    except ImportError:
        raise RuntimeError("pypdf 未安装，无法解析 PDF") from None

    try:
        reader = PdfReader(io.BytesIO(raw_bytes))
    except Exception as e:
        logger.error("PDF 打开失败: %s", e, exc_info=True)
        raise ValueError(f"PDF 文件无法解析: {e}") from e

    total = len(reader.pages)
    if total == 0:
        return []
    if total > _MAX_PAGES:
        logger.warning("PDF 页数 %d 超上限 %d，仅解析前 %d 页", total, _MAX_PAGES, _MAX_PAGES)

    # 1. 书签 → {页码: [标题,...]}（pdf 书签是 1-based 目标页，pypdf 返回 0-based）
    bookmarks = _extract_bookmarks(reader)

    # 2. 深解析（可选）：pdfplumber 拿字号找标题、提表格
    page_titles: dict[int, list[str]] = {}
    page_tables: dict[int, list[str]] = {}
    if _DEEP_PARSE:
        try:
            _deep_scan(raw_bytes, total, page_titles, page_tables)
        except Exception as e:
            logger.warning("PDF 深解析失败，降级纯文本: %s", e)

    blocks: list[Block] = []
    heading_stack: list[str] = []

    for pi in range(min(total, _MAX_PAGES)):
        page = reader.pages[pi]
        try:
            text = page.extract_text() or ""
        except Exception as e:
            logger.warning("PDF 第 %d 页抽取失败: %s", pi + 1, e)
            text = ""
        page_no = pi + 1

        # 书签标题优先，其次深解析字号标题
        titles = bookmarks.get(page_no) or page_titles.get(page_no) or []
        for t in titles:
            _push_heading(heading_stack, 2, t)

        # 表格（深解析）：独立成块
        for md in page_tables.get(page_no, []):
            blocks.append(Block(text=md, kind="table", heading_path=list(heading_stack), page=page_no))

        # 正文：按行识别条款/标题
        _text_to_blocks(text, heading_stack, blocks, page_no)

    logger.info("PDF 解析完成: %d 页 → %d 块 (deep=%s)", min(total, _MAX_PAGES), len(blocks), _DEEP_PARSE)
    return blocks


def _extract_bookmarks(reader) -> dict[int, list[str]]:
    """抽取书签大纲 → {页码: [标题]}（若存在）。"""
    out: dict[int, list[str]] = {}
    try:
        def walk(items, level):
            for it in items:
                if isinstance(it, list):
                    walk(it, level + 1)
                    continue
                title = getattr(it, "title", None)
                page_no = None
                try:
                    page_no = reader.get_destination_page_number(it) + 1
                except Exception:
                    pass
                if title and page_no:
                    out.setdefault(page_no, []).append(title.strip())
        outline = getattr(reader, "outline", None)
        if outline:
            walk(outline, 0)
    except Exception as e:
        logger.debug("PDF 书签解析跳过: %s", e)
    return out


def _deep_scan(raw_bytes: bytes, total: int, titles: dict, tables: dict) -> None:
    """pdfplumber 深扫描：字号启发式取标题 + 提取表格。"""
    import pdfplumber

    with pdfplumber.open(io.BytesIO(raw_bytes)) as pdf:
        for pi, page in enumerate(pdf.pages[:min(total, _MAX_PAGES)]):
            page_no = pi + 1
            # 字号：取所有字符尺寸中位数作为正文基准
            chars = page.chars
            if chars:
                sizes = sorted(c.get("size", 0) for c in chars)
                median = sizes[len(sizes) // 2] if sizes else 0
                # 逐行聚合：同 top(y) 归一行
                line_sizes: dict[int, list] = {}
                for c in chars:
                    key = round(c.get("top", 0))
                    line_sizes.setdefault(key, []).append(c)
                found = []
                for key, cs in line_sizes.items():
                    cs.sort(key=lambda c: c.get("x0", 0))
                    line = "".join(c.get("text", "") for c in cs).strip()
                    if not line or len(line) > _HEADING_MAX_CHARS:
                        continue
                    avg = sum(c.get("size", 0) for c in cs) / len(cs)
                    if median and avg >= median * _HEADING_SIZE_RATIO:
                        found.append(line)
                if found:
                    titles[page_no] = found
            # 表格
            try:
                for tbl in (page.extract_tables() or []):
                    md = _table_to_md(tbl)
                    if md:
                        tables.setdefault(page_no, []).append(md)
            except Exception as e:
                logger.debug("PDF 第 %d 页表格提取跳过: %s", page_no, e)


def _table_to_md(tbl: list[list]) -> str:
    """pdfplumber 表格 → Markdown 管道表。"""
    rows = [[(c or "").strip().replace("\n", " ") for c in row] for row in tbl if row]
    rows = [r for r in rows if any(r)]
    if len(rows) < 2:
        return ""  # 单行不足以成表
    header = rows[0]
    ncol = max(len(r) for r in rows)
    header = header + [""] * (ncol - len(header))
    lines = ["| " + " | ".join(header) + " |",
             "| " + " | ".join(["---"] * ncol) + " |"]
    for r in rows[1:]:
        r = r + [""] * (ncol - len(r))
        lines.append("| " + " | ".join(r) + " |")
    return "\n".join(lines)


def _text_to_blocks(text: str, heading_stack: list[str],
                    blocks: list[Block], page_no: int) -> None:
    """把一页的文本按行识别为条款/段落块。"""
    import re
    clause_re = re.compile(r"^\s*第\s*[一二三四五六七八九十百零〇\d]+\s*[条章节款项]")
    buffer: list[str] = []

    def flush():
        if buffer:
            content = "\n".join(buffer).strip()
            buffer.clear()
            if content:
                blocks.append(Block(text=content, kind="para",
                                    heading_path=list(heading_stack), page=page_no))

    for line in text.split("\n"):
        s = line.strip()
        if not s:
            flush()
            continue
        if clause_re.match(s) and len(s) <= _HEADING_MAX_CHARS:
            flush()
            blocks.append(Block(text=s, kind="clause", level=1,
                                heading_path=list(heading_stack), page=page_no))
            continue
        buffer.append(s)
    flush()


def _push_heading(stack: list[str], level: int, title: str) -> None:
    while len(stack) >= level:
        stack.pop()
    while len(stack) < level - 1:
        stack.append("")
    stack.append(title)
