"""统一解析管线：真实类型识别 → 选解析器 → 质量门禁。

对上层（rag.parse_and_index）只暴露一个入口 ``parse_document``，
以后换 PDF 引擎（pdfplumber / Docling / OCR）只改这里的分派，切分与索引不动。
"""
import logging

from app.docparse.quality import evaluate
from app.docparse.sniff import detect_real_type
from app.docparse.types import (DocMeta, DocumentParseError, ParseErrorCode,
                                ParseResult)

logger = logging.getLogger("srm-ai.docparse")


def parse_document(raw_bytes: bytes, file_type: str) -> ParseResult:
    """解析任意受支持文档为结构块，并执行质量门禁。

    Args:
        raw_bytes: 文件字节
        file_type: 上传时的扩展名（只作兜底，真实类型以内容为准）

    Returns:
        ParseResult（结构块 + 元数据）

    Raises:
        DocumentParseError: 不支持类型 / 空文档 / 扫描件 / 加密 / 解析异常
    """
    real_type = detect_real_type(raw_bytes, file_type)
    meta = DocMeta(real_type=real_type)

    try:
        blocks = _dispatch(raw_bytes, real_type, meta)
    except DocumentParseError:
        # 已带原因码的异常直接上抛，不覆盖成泛化 PARSE_ERROR
        raise
    except Exception as e:
        logger.error("文档解析异常: real_type=%s, error=%s", real_type, e, exc_info=True)
        raise DocumentParseError(
            ParseErrorCode.PARSE_ERROR, f"文档解析失败: {e}"
        ) from e

    # 非 PDF 解析器不返回字符数，这里按块文本兜底统计
    if not meta.char_count:
        meta.char_count = sum(len((b.text or "").strip()) for b in blocks)

    evaluate(blocks, meta)
    logger.info(
        "文档解析完成: real_type=%s parser=%s pages=%d chars=%d density=%.0f "
        "scanned=%d quality=%.2f warnings=%s",
        real_type, meta.parser, meta.parsed_pages, meta.char_count,
        meta.text_density, meta.scanned_pages, meta.quality_score, meta.warnings,
    )
    return ParseResult(blocks=blocks, meta=meta)


def _dispatch(raw_bytes: bytes, real_type: str, meta: DocMeta) -> list:
    """按真实类型分派到对应解析器，并回填解析器信息。"""
    if real_type == "pdf":
        from app.document.parsers.pdf import parse_pdf_document

        blocks, info = parse_pdf_document(raw_bytes)
        meta.parser = "pypdf+pdfplumber"
        meta.page_count = info.page_count
        meta.parsed_pages = info.parsed_pages
        meta.char_count = info.char_count
        meta.scanned_pages = info.scanned_pages
        meta.ocr_pages = info.ocr_pages
        meta.ocr_failed_pages = info.ocr_failed_pages
        meta.warnings.extend(info.warnings)
        return blocks

    if real_type == "docx":
        from app.document.parsers.docx import extract_docx_blocks

        meta.parser = "python-docx"
        return extract_docx_blocks(raw_bytes)

    if real_type == "xlsx":
        from app.document.parsers.excel import extract_excel_blocks

        meta.parser = "openpyxl"
        return extract_excel_blocks(raw_bytes)

    if real_type in ("txt", "md"):
        from app.document.chunker import extract_blocks

        meta.parser = "regex"
        return extract_blocks(_decode_text(raw_bytes))

    if real_type == "xls":
        raise DocumentParseError(
            ParseErrorCode.UNSUPPORTED_TYPE,
            "暂不支持旧版 .xls，请用 Excel 另存为 .xlsx 后重新上传",
        )

    raise DocumentParseError(
        ParseErrorCode.UNSUPPORTED_TYPE,
        f"不支持的文档类型: {real_type}（支持 txt / md / pdf / docx / xlsx）",
    )


def _decode_text(raw_bytes: bytes) -> str:
    """按 UTF-8 → GBK → Latin-1 依次尝试解码文本文件。"""
    for encoding in ("utf-8", "gbk", "latin-1"):
        try:
            return raw_bytes.decode(encoding)
        except (UnicodeDecodeError, LookupError):
            continue
    raise DocumentParseError(ParseErrorCode.PARSE_ERROR, "文档编码无法识别")
