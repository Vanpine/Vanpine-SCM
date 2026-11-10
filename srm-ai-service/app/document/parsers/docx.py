"""
docx 解析器

用 python-docx 抽取 Word 文档为结构块。docx 自带样式层级信息，
比正则猜标题可靠得多：
  - 段落 style 名含 'Heading N' / '标题 N' → 标题层级 N（直接拿 heading_path）
  - 正文段落、列表段落按样式归 para/list
  - 表格按行组抽为 table 块（保留表头）

输出与 chunker.Block 一致，交由 chunker.build_chunks 统一切分。
"""
import io
import logging
import re

from app.docparse.clean.toc import is_toc_line
from app.document.chunker import Block

logger = logging.getLogger("srm-ai.docx")

# Word 标题样式：'Heading 1'..'Heading 9' / '标题 1'..（中文 Word）
_HEADING_STYLE_RE = re.compile(r"^(?:Heading|标题)\s*(\d+)$", re.IGNORECASE)
# Word 自动目录样式：'TOC 1'..'TOC 9' / '目录 1'..（中文 Word）
_TOC_STYLE_RE = re.compile(r"^(?:TOC|目录)\s*\d*$", re.IGNORECASE)
# 目录标题样式（不参与切分，同样丢弃）
_TOC_STYLE_NAMES = {"TOC Heading", "目录标题"}
# 列表样式
_LIST_STYLE_HINT = ("List", "列表")
# 表格转 Markdown 的最大行数（超出切片，每片重复表头）
TABLE_MAX_ROWS = 40
# 无 TOC 样式时，连续多少行目录特征才判定为手工目录（防误删正文）
MIN_MANUAL_TOC_RUN = 3


def extract_docx_blocks(raw_bytes: bytes) -> list[Block]:
    """把 .docx 字节解析为结构块列表。

    Args:
        raw_bytes: docx 文件字节

    Returns:
        Block 列表（含 heading/para/list/table）

    Raises:
        RuntimeError: python-docx 未安装
        ValueError: 文档无法解析
    """
    try:
        import docx  # 惰性导入：仅解析 docx 时才需要
    except ImportError:
        raise RuntimeError("python-docx 未安装，无法解析 docx") from None

    try:
        document = docx.Document(io.BytesIO(raw_bytes))
    except Exception as e:
        logger.error("docx 解析失败: %s", e, exc_info=True)
        raise ValueError(f"docx 文件无法解析: {e}") from e

    blocks: list[Block] = []
    heading_stack: list[str] = []

    # docx 的 body 元素按出现顺序混排「段落」与「表格」，需按文档顺序遍历，
    # 否则表格会被挪到末尾，破坏结构。
    for child in document.element.body.iterchildren():
        tag = child.tag.split("}")[-1]
        if tag == "p":
            _handle_paragraph(document, child, blocks, heading_stack)
        elif tag == "tbl":
            _handle_table(document, child, blocks, heading_stack)

    # 兜底：手工录入的目录没有 TOC 样式，按「连续目录特征行」识别并标记
    _mark_manual_toc_runs(blocks)

    logger.info("docx 解析完成: %d 块", len(blocks))
    return blocks


def _handle_paragraph(document, p_element, blocks: list[Block],
                      heading_stack: list[str]) -> None:
    """处理单个段落元素：识别标题/列表/正文。"""
    from docx.text.paragraph import Paragraph

    para = Paragraph(p_element, document)
    text = (para.text or "").strip()
    if not text:
        return

    style_name = (para.style.name if para.style else "") or ""

    # 目录：标记为 toc，由 chunker 统一丢弃（目录含全部章节名，入库是强噪声）
    if _is_toc_style(style_name):
        blocks.append(Block(text=text, kind="toc",
                            heading_path=list(heading_stack)))
        return

    # 标题：style 名带 Heading N / 标题 N
    m = _HEADING_STYLE_RE.match(style_name.strip())
    if m:
        level = int(m.group(1))
        while len(heading_stack) >= level:
            heading_stack.pop()
        while len(heading_stack) < level - 1:
            heading_stack.append("")
        heading_stack.append(text)
        blocks.append(Block(text=text, kind="heading", level=level,
                            heading_path=list(heading_stack)))
        return

    # 列表段落
    if any(hint in style_name for hint in _LIST_STYLE_HINT):
        blocks.append(Block(text=text, kind="list",
                            heading_path=list(heading_stack)))
        return

    # 普通正文
    blocks.append(Block(text=text, kind="para",
                        heading_path=list(heading_stack)))


def _handle_table(document, tbl_element, blocks: list[Block],
                  heading_stack: list[str]) -> None:
    """处理表格元素：抽为 Markdown 管道表，超长按表头重复切片。"""
    from docx.table import Table

    try:
        table = Table(tbl_element, document)
    except Exception as e:
        logger.warning("docx 表格解析跳过: %s", e)
        return

    rows = []
    for row in table.rows:
        cells = [c.text.strip().replace("\n", " ") for c in row.cells]
        rows.append(cells)
    if not rows or not any(any(c for c in r) for r in rows):
        return

    header = rows[0]
    body = rows[1:]

    # 按 TABLE_MAX_ROWS 切片，每片重复表头
    if not body:
        md = _rows_to_md(header, [])
        blocks.append(Block(text=md, kind="table", heading_path=list(heading_stack)))
        return
    for i in range(0, len(body), TABLE_MAX_ROWS):
        md = _rows_to_md(header, body[i:i + TABLE_MAX_ROWS])
        blocks.append(Block(text=md, kind="table", heading_path=list(heading_stack)))


def _rows_to_md(header: list[str], body: list[list[str]]) -> str:
    """行组 → Markdown 管道表文本。"""
    ncol = max(len(header), *(len(r) for r in body)) if body else len(header)
    header = header + [""] * (ncol - len(header))
    lines = ["| " + " | ".join(header) + " |",
             "| " + " | ".join(["---"] * ncol) + " |"]
    for r in body:
        r = r + [""] * (ncol - len(r))
        lines.append("| " + " | ".join(r) + " |")
    return "\n".join(lines)


def _is_toc_style(style_name: str) -> bool:
    """判断段落样式是否为 Word 自动目录（TOC 1..9 / 目录 1..9 / 目录标题）。"""
    name = (style_name or "").strip()
    return name in _TOC_STYLE_NAMES or bool(_TOC_STYLE_RE.match(name))


def _looks_like_toc_entry(text: str) -> bool:
    """按目录行特征判断（复用 PDF 目录的同一套规则）。

    Word 目录用制表符分隔页码，先转成 4 个空格再交给共享识别器。
    """
    return is_toc_line((text or "").replace("\t", "    "))


def _mark_manual_toc_runs(blocks: list[Block]) -> None:
    """把连续 ≥3 行的手工目录段落标记为 toc（无 TOC 样式的兜底）。

    只处理普通段落；不足 3 行不触发，避免把正文里零星的「详见附录...页码」误删。
    """
    run: list[Block] = []

    def flush() -> None:
        if len(run) >= MIN_MANUAL_TOC_RUN:
            for block in run:
                block.kind = "toc"
        run.clear()

    for block in blocks:
        if block.kind == "para" and _looks_like_toc_entry(block.text):
            run.append(block)
        else:
            flush()
    flush()
