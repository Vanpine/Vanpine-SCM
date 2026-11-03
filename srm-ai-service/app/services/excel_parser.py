"""
excel 解析器

用 openpyxl 抽取 .xlsx/.xls 为结构块。Excel 是表格型文档，
不能用文本段落逻辑：
  - 每个 sheet 视为一个章节（进 heading_path）
  - sheet 首行/首个非空行作表头，数据行按 TABLE_MAX_ROWS 成组
  - 每组重复表头 → 转 Markdown 管道表文本
  - 多 sheet 之间是硬边界

输出与 chunker.Block 一致。
"""
import io
import logging

from app.services.chunker import Block

logger = logging.getLogger("srm-ai.excel")

# 单块最大数据行数（超出切片，每片重复表头）
TABLE_MAX_ROWS = 40
# 单行最大列数（防超宽表把块撑爆）
MAX_COLS = 30


def extract_excel_blocks(raw_bytes: bytes) -> list[Block]:
    """把 Excel 字节解析为结构块列表。

    Args:
        raw_bytes: xlsx/xls 文件字节

    Returns:
        Block 列表（每 sheet 的每个行组一个 table 块）

    Raises:
        RuntimeError: openpyxl 未安装
        ValueError: 文档无法解析
    """
    try:
        import openpyxl  # 惰性导入
    except ImportError:
        raise RuntimeError("openpyxl 未安装，无法解析 Excel") from None

    try:
        wb = openpyxl.load_workbook(io.BytesIO(raw_bytes), read_only=True, data_only=True)
    except Exception as e:
        logger.error("Excel 解析失败: %s", e, exc_info=True)
        raise ValueError(f"Excel 文件无法解析: {e}") from e

    blocks: list[Block] = []
    for sheet in wb.worksheets:
        heading_path = [sheet.title]
        # 读为二维数组，去掉全空尾列/尾行
        rows = []
        for row in sheet.iter_rows(values_only=True):
            cells = ["" if v is None else str(v).strip() for v in row[:MAX_COLS]]
            if any(cells):
                rows.append(cells)
        if not rows:
            continue

        header = rows[0]
        body = rows[1:]
        if not body:
            blocks.append(Block(text=_rows_to_md(header, []), kind="table",
                                heading_path=list(heading_path)))
            continue
        for i in range(0, len(body), TABLE_MAX_ROWS):
            md = _rows_to_md(header, body[i:i + TABLE_MAX_ROWS])
            blocks.append(Block(text=md, kind="table", heading_path=list(heading_path)))

    wb.close()
    logger.info("Excel 解析完成: %d sheet, %d 块", len(wb.sheetnames), len(blocks))
    return blocks


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
