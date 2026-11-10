"""文档类型识别测试（L1 接入层）。

锁定：不能只信扩展名——魔数优先，其次打开 OOXML 压缩包看内部目录，
最后才回退到扩展名/文本启发式。真实类型决定用哪个解析器。
"""
import io
import sys
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.docparse.sniff import detect_real_type


def _zip_with(entry: str) -> bytes:
    """构造只含一个条目的 zip（模拟 OOXML 容器的内部结构）。"""
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        z.writestr(entry, b"x")
    return buf.getvalue()


def test_pdf_magic_wins_over_extension():
    """扩展名写 .docx 但内容是 PDF，必须按 PDF 处理。"""
    assert detect_real_type(b"%PDF-1.7\n%\xe2\xe3\n", "报告.docx") == "pdf"


def test_docx_detected_by_internal_entry():
    assert detect_real_type(_zip_with("word/document.xml"), "a.bin") == "docx"


def test_xlsx_detected_by_internal_entry():
    assert detect_real_type(_zip_with("xl/workbook.xml"), "a.bin") == "xlsx"


def test_pptx_detected_by_internal_entry():
    assert detect_real_type(_zip_with("ppt/presentation.xml"), "a.bin") == "pptx"


def test_legacy_ole_uses_extension():
    """旧版 Office 是 OLE 复合文档，魔术相同，只能靠扩展名区分 doc/xls/ppt。"""
    ole = b"\xd0\xcf\x11\xe0\xa1\xb1\x1a\xe1" + b"\x00" * 16
    assert detect_real_type(ole, "合同.doc") == "doc"
    assert detect_real_type(ole, "清单.xls") == "xls"
    assert detect_real_type(ole, "unknown.bin") == "ole"


def test_markdown_falls_back_to_extension():
    assert detect_real_type("第一段\n\n第二段".encode("utf-8"), "制度.md") == "md"


def test_html_detected_by_prefix():
    assert detect_real_type(b"<!DOCTYPE html><html><body>x</body></html>", "a.bin") == "html"


def test_unknown_binary_rejected():
    assert detect_real_type(b"\x00\x01\x02\x03\xff", "a.bin") == "binary"
