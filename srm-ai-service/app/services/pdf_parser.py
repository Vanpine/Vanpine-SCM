"""
PDF 解析器

用 pypdf 逐页抽文本并保留页码；标题识别优先用书签（大纲），
其次字号启发式（pdfplumber，字号 ≥ 正文 ×1.15 且行长 ≤40），
再退中文条款正则。表格用 pdfplumber 受控提取。

解析分两阶段（L3 清洗需要跨页视野）：
  ① 抽所有页文本，统计文本密度与扫描页；
  ② 清洗跨页重复的页眉/页脚/页码，再逐页建结构块。

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
import re
from dataclasses import dataclass, field

from app.docparse.clean.footnote import strip_footnotes
from app.docparse.clean.header_footer import strip_running_lines
from app.docparse.clean.text_repair import join_hyphenated_lines, merge_cross_page_blocks
from app.docparse.clean.toc import strip_toc_pages
from app.docparse.types import DocumentParseError, ParseErrorCode
from app.services.chunker import Block

logger = logging.getLogger("srm-ai.pdf")

# 深解析开关（pdfplumber 字号/表格）：默认关闭，纯 pypdf 更快更稳
_DEEP_PARSE = os.environ.get("PDF_DEEP_PARSE", "false").lower() in ("1", "true", "yes")
# 单文档解析页数上限（防超大 PDF 拖垮同步链路）
_MAX_PAGES = int(os.environ.get("PDF_MAX_PAGES", "200"))
# 标题行最大字符数
_HEADING_MAX_CHARS = 40
# 字号判定：≥ 正文中位字号 × RATIO
_HEADING_SIZE_RATIO = 1.15
# 单页文本层字符数低于此值且页面含图片 → 判为扫描页
_SCANNED_PAGE_CHARS = 50

_WHITESPACE_RE = re.compile(r"\s")


@dataclass
class PdfParseInfo:
    """PDF 解析统计（供 pipeline 质量门禁与日志使用）。"""

    page_count: int = 0
    parsed_pages: int = 0
    char_count: int = 0
    scanned_pages: int = 0
    ocr_pages: int = 0
    ocr_failed_pages: int = 0
    warnings: list[str] = field(default_factory=list)


def extract_pdf_blocks(raw_bytes: bytes) -> list[Block]:
    """兼容入口：只返回结构块（历史调用方与测试使用）。"""
    blocks, _ = parse_pdf_document(raw_bytes)
    return blocks


def parse_pdf_document(raw_bytes: bytes) -> tuple[list[Block], PdfParseInfo]:
    """解析 PDF → (结构块列表, 解析统计)。

    Args:
        raw_bytes: PDF 文件字节

    Returns:
        (Block 列表, PdfParseInfo)

    Raises:
        DocumentParseError: 文档加密无法解开
        RuntimeError: pypdf 未安装
        ValueError: 文档无法打开
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

    # 1. 加密 PDF：空密码能解就继续，解不开直接给明确原因码
    _ensure_decrypted(reader)

    info = PdfParseInfo(page_count=len(reader.pages))
    if info.page_count == 0:
        return [], info
    if info.page_count > _MAX_PAGES:
        logger.warning("PDF 页数 %d 超上限 %d，仅解析前 %d 页",
                       info.page_count, _MAX_PAGES, _MAX_PAGES)
        info.warnings.append(
            f"PDF 共 {info.page_count} 页，超过单次解析上限，仅解析前 {_MAX_PAGES} 页"
        )
    info.parsed_pages = min(info.page_count, _MAX_PAGES)

    # 2. 书签 → {页码: [标题]}（pdf 书签是 1-based 目标页，pypdf 返回 0-based）
    bookmarks = _extract_bookmarks(reader)

    # 3. 深解析（可选）：pdfplumber 拿字号找标题、提表格
    page_titles: dict[int, list[str]] = {}
    page_tables: dict[int, list[str]] = {}
    if _DEEP_PARSE:
        try:
            _deep_scan(raw_bytes, info.page_count, page_titles, page_tables)
        except Exception as e:
            logger.warning("PDF 深解析失败，降级纯文本: %s", e)
            info.warnings.append("PDF 深解析失败，已降级为纯文本解析")

    # 4. 抽全部页文本，同时统计有效字符与扫描页
    page_texts: list[str] = []
    scanned_indexes: list[int] = []  # 待 OCR 的页下标（0-based）
    for pi in range(info.parsed_pages):
        page = reader.pages[pi]
        try:
            text = page.extract_text() or ""
        except Exception as e:
            logger.warning("PDF 第 %d 页抽取失败: %s", pi + 1, e)
            text = ""
        page_texts.append(text)

        effective = len(_WHITESPACE_RE.sub("", text))
        info.char_count += effective
        if effective < _SCANNED_PAGE_CHARS and _page_has_image(page):
            info.scanned_pages += 1
            scanned_indexes.append(pi)

    # 4.1 扫描页 OCR：扫描件没有文本层，先用多模态模型把图片转回 Markdown
    if scanned_indexes:
        _ocr_scanned_pages(raw_bytes, page_texts, scanned_indexes, info)

    # 5. 跨页清洗页眉/页脚/页码（必须拿到全部页文本后才能判重复）
    cleaned_texts, stats = strip_running_lines(page_texts)
    if stats.removed_lines:
        info.warnings.append(f"已清理页眉页脚/页码 {stats.removed_lines} 行")

    # 5.1 整页丢弃目录页：目录含全部章节名，入库会造成强噪声
    cleaned_texts, toc_stats = strip_toc_pages(cleaned_texts)
    if toc_stats.removed_pages:
        info.warnings.append(f"已跳过目录页 {toc_stats.removed_pages} 页")

    # 5.2 剥离页底脚注（正文之外的注释/来源说明）
    cleaned_texts, footnote_stats = strip_footnotes(cleaned_texts)
    if footnote_stats.removed_lines:
        info.warnings.append(f"已剥离脚注 {footnote_stats.removed_lines} 行")

    # 5.3 修复行尾连字符断词（英文 manage- / ment → management）
    repaired_texts: list[str] = []
    hyphen_count = 0
    for text in cleaned_texts:
        fixed, count = join_hyphenated_lines(text)
        repaired_texts.append(fixed)
        hyphen_count += count
    cleaned_texts = repaired_texts
    if hyphen_count:
        info.warnings.append(f"已修复行尾连字符 {hyphen_count} 处")

    # 6. 用清洗后的文本逐页建结构块（标题栈跨页保持）
    blocks: list[Block] = []
    heading_stack: list[str] = []
    for pi, text in enumerate(cleaned_texts):
        page_no = pi + 1
        # 书签标题优先，其次深解析字号标题
        titles = bookmarks.get(page_no) or page_titles.get(page_no) or []
        for t in titles:
            _push_heading(heading_stack, 2, t)

        # 表格（深解析）：独立成块
        for md in page_tables.get(page_no, []):
            blocks.append(Block(text=md, kind="table",
                                heading_path=list(heading_stack), page=page_no))

        # 正文：按行识别条款/标题
        _text_to_blocks(text, heading_stack, blocks, page_no)

    # 6.1 拼接跨页被切断的正文段落（页边界造成的断句）
    blocks, merged_count = merge_cross_page_blocks(blocks)
    if merged_count:
        info.warnings.append(f"已拼接跨页段落 {merged_count} 处")

    logger.info("PDF 解析完成: %d/%d 页 → %d 块 (deep=%s, scanned=%d, chars=%d)",
                info.parsed_pages, info.page_count, len(blocks),
                _DEEP_PARSE, info.scanned_pages, info.char_count)
    return blocks, info


def _ensure_decrypted(reader) -> None:
    """空密码尝试解密；失败抛 ENCRYPTED 原因码。"""
    if not getattr(reader, "is_encrypted", False):
        return
    try:
        ok = reader.decrypt("")
    except Exception:
        ok = 0
    if not ok:
        raise DocumentParseError(
            ParseErrorCode.ENCRYPTED,
            "文档已加密，无法解析，请解除密码后重试",
        )


def _ocr_scanned_pages(raw_bytes: bytes, page_texts: list[str],
                       scanned_indexes: list[int], info: PdfParseInfo) -> None:
    """对扫描页调用 VLM 识别，把 Markdown 回填到页文本。

    Args:
        raw_bytes: PDF 原始字节（渲染用）
        page_texts: 页文本列表（原地回填）
        scanned_indexes: 扫描页下标（0-based）
        info: 解析统计（累计 OCR 成功/失败页数）
    """
    from app.services import vlm  # 惰性导入：避免未启用 OCR 时加载 LangChain 视觉客户端

    if not vlm.is_available():
        logger.info("VLM 未启用或无密钥，跳过扫描页 OCR: %d 页", len(scanned_indexes))
        info.warnings.append(
            f"检测到 {len(scanned_indexes)} 页扫描页，但 OCR 未启用，这些页将无内容"
        )
        return

    # 同步解析受 Java 侧 120s 读超时约束，页数超限直接给出可操作提示
    if len(scanned_indexes) > vlm.VLM_MAX_PAGES:
        raise DocumentParseError(
            ParseErrorCode.OCR_PAGE_LIMIT,
            f"扫描页共 {len(scanned_indexes)} 页，超过单次 OCR 上限 "
            f"{vlm.VLM_MAX_PAGES} 页，请分册上传或先做 OCR 后再上传",
        )

    ocr_texts = vlm.ocr_pages(raw_bytes, scanned_indexes)
    scores: list[float] = []
    low_quality = 0
    for idx in scanned_indexes:
        text, score = ocr_texts.get(idx, ("", 0.0))
        if text:
            page_texts[idx] = text
            info.ocr_pages += 1
            info.char_count += len(_WHITESPACE_RE.sub("", text))
            scores.append(score)
            # 质量分在 FAIL~WARN 之间：可用但建议人工核对
            if score < vlm.VLM_QUALITY_WARN:
                low_quality += 1
        else:
            info.ocr_failed_pages += 1

    if info.ocr_pages:
        avg_score = sum(scores) / len(scores)
        info.warnings.append(
            f"已 OCR 扫描页 {info.ocr_pages}/{len(scanned_indexes)} 页"
            f"（平均质量 {avg_score:.2f}）"
        )
    if low_quality:
        info.warnings.append(
            f"OCR 低质量页 {low_quality} 页（低于 {vlm.VLM_QUALITY_WARN:.2f}），建议人工核对"
        )
    if info.ocr_failed_pages:
        info.warnings.append(f"OCR 失败 {info.ocr_failed_pages} 页")


def _page_has_image(page) -> bool:
    """判断页面是否含图片 XObject（扫描页的关键特征）。

    不依赖 Pillow：直接检查 /Resources/XObject 里是否有 /Subtype /Image。
    """
    try:
        resources = page.get("/Resources")
        if resources is None:
            return False
        if hasattr(resources, "get_object"):
            resources = resources.get_object()
        xobjects = resources.get("/XObject")
        if xobjects is None:
            return False
        if hasattr(xobjects, "get_object"):
            xobjects = xobjects.get_object()
        for obj in xobjects.values():
            try:
                target = obj.get_object() if hasattr(obj, "get_object") else obj
                if target.get("/Subtype") == "/Image":
                    return True
            except Exception:
                continue
    except Exception:
        return False
    return False


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
    """把标题压入标题栈；跳级时不补空字符串占位（避免 heading_path 出现 ""）。"""
    while len(stack) >= level:
        stack.pop()
    stack.append(title)
