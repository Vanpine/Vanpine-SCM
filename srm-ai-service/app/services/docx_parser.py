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

from app.services.chunker import Block

logger = logging.getLogger("srm-ai.docx")

# Word 标题样式：'Heading 1'..'Heading 9' / '标题 1'..（中文 Word）
_HEADING_STYLE_RE = re.compile(r"^(?:Heading|标题)\s*(\d+)$", re.IGNORECASE)
# 列表样式
_LIST_STYLE_HINT = ("List", "列表")
# 表格转 Markdown 的最大行数（超出切片，每片重复表头）
TABLE_MAX_ROWS = 40


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
