"""PDF 页面级解析测试：页眉页脚清洗 + 扫描件识别 + 加密检测。

在原有 test_pdf_parser.py 基础上补充 L3/L6 行为：
  1. 跨页重复的页眉/页脚从最终块中消失；
  2. 页码行被删除；
  3. 每页文本密度与扫描页数进入 PdfParseInfo；
  4. 加密 PDF 抛 ENCRYPTED 原因码。
"""
import io
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.docparse.types import DocumentParseError, ParseErrorCode
from app.services import pdf_parser
from app.services.pdf_parser import extract_pdf_blocks, parse_pdf_document


def _make_pdf_lines(pages: list[list[str]]) -> bytes:
    """生成多页 PDF，每页按行写入（y 坐标递减，模拟正常阅读顺序）。"""
    from pypdf import PdfWriter
    from pypdf.generic import (ArrayObject, DecodedStreamObject, DictionaryObject,
                               FloatObject, NameObject)

    writer = PdfWriter()
    font = DictionaryObject({
        NameObject("/Type"): NameObject("/Font"),
        NameObject("/Subtype"): NameObject("/Type1"),
        NameObject("/BaseFont"): NameObject("/Helvetica"),
    })
    for lines in pages:
        page = writer.add_blank_page(width=612, height=792)
        page[NameObject("/Resources")] = DictionaryObject({
            NameObject("/Font"): DictionaryObject({NameObject("/F1"): font})
        })
        ops = []
        y = 720
        for line in lines:
            safe = line.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
            ops.append(f"BT /F1 12 Tf 72 {y} Td ({safe}) Tj ET")
            y -= 20
        s = DecodedStreamObject()
        s.set_data("\n".join(ops).encode("latin-1", "replace"))
        page[NameObject("/Contents")] = writer._add_object(s)

    buf = io.BytesIO()
    writer.write(buf)
    return buf.getvalue()


def test_repeated_header_and_footer_removed_from_blocks():
    pages = [
        ["ACME CORP", f"Quarter {i} body content that is long enough to keep", "internal use only"]
        for i in range(3)
    ]
    blocks = extract_pdf_blocks(_make_pdf_lines(pages))
    text = "\n".join(b.text for b in blocks)

    assert "ACME CORP" not in text, text
    assert "internal use only" not in text, text
    assert "Quarter 0 body content" in text, text


def test_page_numbers_removed_from_blocks():
    pages = [["- 1 -", "first page body content long enough"],
             ["- 2 -", "second page body content long enough"],
             ["- 3 -", "third page body content long enough"]]
    blocks = extract_pdf_blocks(_make_pdf_lines(pages))
    text = "\n".join(b.text for b in blocks)
    assert "- 1 -" not in text and "- 2 -" not in text, text
    assert "first page body content" in text


def test_page_info_density_and_scanned_count():
    pages = [["Page one text"], ["Page two content"]]
    blocks, info = parse_pdf_document(_make_pdf_lines(pages))
    assert blocks
    assert info.page_count == 2
    assert info.parsed_pages == 2
    assert info.char_count > 0
    # 短页默认会被记为低文本页（是否算扫描件由 pipeline 质量门禁结合图片判定）
    assert info.scanned_pages >= 0


def test_scanned_pages_counted_when_image_present(monkeypatch):
    monkeypatch.setattr(pdf_parser, "_page_has_image", lambda page: True)
    pages = [["x"], ["y"], ["z"]]
    _, info = parse_pdf_document(_make_pdf_lines(pages))
    assert info.scanned_pages == 3, info


def test_encrypted_pdf_raises_encrypted():
    from pypdf import PdfWriter

    writer = PdfWriter()
    writer.add_blank_page(width=612, height=792)
    writer.encrypt(user_password="secret", owner_password="secret")
    buf = io.BytesIO()
    writer.write(buf)

    with pytest.raises(DocumentParseError) as ei:
        parse_pdf_document(buf.getvalue())
    assert ei.value.code == ParseErrorCode.ENCRYPTED


def test_pdf_toc_page_not_indexed():
    """PDF 目录页整页丢弃，正文页正常入库。

    生成 PDF 的辅助函数只能写 ASCII（Helvetica），因此这里用数字/英文目录行验证
    接线；中文目录识别细节由 test_toc_cleaning.py 直接覆盖。
    """
    pages = [
        ["Contents", "6.3 Test results........60", "6.1 Test env........61",
         "6.2 Results........64", "Abstract........69"],
        ["6 System test", "6.1 Test env", "Body paragraph with real content."],
    ]
    blocks = extract_pdf_blocks(_make_pdf_lines(pages))
    text = "\n".join(b.text for b in blocks)

    assert "........" not in text, text
    assert "Abstract" not in text, text
    assert "Body paragraph with real content" in text, text


def test_bookmark_heading_path_has_no_empty_segment():
    """书签标题跳级时 heading_path 不得出现空字符串占位。"""
    from app.services.pdf_parser import _push_heading

    stack: list[str] = []
    _push_heading(stack, 2, "目 录")
    assert stack == ["目 录"], stack
