"""统一解析管线与质量门禁测试（L1 + L6）。

锁定：
  1. 分派以真实类型为准，扩展名说谎时按内容解析；
  2. 空文本 / 扫描件 / 不支持类型必须抛带原因码的 DocumentParseError；
  3. 质量分与告警进入 DocMeta，供日志与前端展示。
"""
import io
import sys
import zipfile
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.docparse.pipeline import parse_document
from app.docparse.quality import evaluate
from app.docparse.types import DocumentParseError, DocMeta, ParseErrorCode


def _zip_with(entry: str) -> bytes:
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        z.writestr(entry, b"x")
    return buf.getvalue()


# ==================== 分派 ====================

def test_txt_parsed_and_meta_filled():
    res = parse_document("第一章 总则\n\n供应商应具备相应资质。".encode("utf-8"), "制度.txt")
    assert res.meta.real_type == "txt"
    assert res.blocks, "应产出结构块"
    assert 0.0 <= res.meta.quality_score <= 1.0


def test_markdown_heading_tree_kept():
    res = parse_document("# 标题\n\n正文".encode("utf-8"), "a.md")
    assert any(b.kind == "heading" for b in res.blocks), res.blocks


def test_magic_wins_over_lying_extension():
    """内容其实是纯文本，扩展名写 .pdf：按真实类型走文本解析，不报错。"""
    res = parse_document("这是正文内容，不是 PDF。".encode("utf-8"), "fake.pdf")
    assert res.meta.real_type == "txt"
    assert res.blocks


def test_unsupported_type_raises_code():
    with pytest.raises(DocumentParseError) as ei:
        parse_document(_zip_with("ppt/presentation.xml"), "slides.pptx")
    assert ei.value.code == ParseErrorCode.UNSUPPORTED_TYPE


def test_empty_document_raises_empty_text():
    with pytest.raises(DocumentParseError) as ei:
        parse_document(b"   \n\n\t  ", "empty.txt")
    assert ei.value.code == ParseErrorCode.EMPTY_TEXT


# ==================== 质量门禁 ====================

def test_scanned_pdf_rejected_with_code():
    meta = DocMeta(real_type="pdf", page_count=3, parsed_pages=3, scanned_pages=3)
    with pytest.raises(DocumentParseError) as ei:
        evaluate([], meta)
    assert ei.value.code == ParseErrorCode.SCANNED_NEEDS_OCR
    assert "扫描" in ei.value.message


def test_partial_scan_passes_with_warning():
    meta = DocMeta(real_type="pdf", page_count=10, parsed_pages=10,
                   scanned_pages=1, char_count=5000)
    evaluate([object()], meta)          # 不抛异常
    assert any("扫描" in w for w in meta.warnings), meta.warnings
    assert meta.quality_score > 0


def test_normal_pdf_quality_score_high():
    meta = DocMeta(real_type="pdf", page_count=2, parsed_pages=2,
                   scanned_pages=0, char_count=1200)
    evaluate([object()], meta)
    assert meta.quality_score >= 0.8, meta.quality_score
    assert meta.warnings == []


def test_empty_blocks_raise_empty_text_for_non_pdf():
    meta = DocMeta(real_type="docx", page_count=0)
    with pytest.raises(DocumentParseError) as ei:
        evaluate([], meta)
    assert ei.value.code == ParseErrorCode.EMPTY_TEXT
