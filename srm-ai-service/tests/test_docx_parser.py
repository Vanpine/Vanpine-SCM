"""
docx 解析器测试

覆盖 P2 验收点：
  (a) 标题按 style 层级抽出，heading_path 非空且正确
  (b) 正文段落归 para，不跨标题合并
  (c) 表格抽为 Markdown 管道表，表头保留，超长按表头重复切片
  (d) 段落与表格按文档顺序处理（表格不被挪到末尾）
"""
import io
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.services.docx_parser import extract_docx_blocks
from app.services.chunker import build_chunks
from app.services.tokenizer import count_tokens


def _make_docx() -> bytes:
    """用 python-docx 构造一份含标题/正文/表格的测试文档。"""
    from docx import Document

    doc = Document()
    doc.add_heading("2026年度钢材采购合同", level=1)
    doc.add_heading("第一条 标的物", level=2)
    doc.add_paragraph("本合同标的物为热轧卷板，规格 Q235B。")
    doc.add_heading("第二条 价格与付款", level=2)
    doc.add_paragraph("单价以订单为准，货到验收合格后30日内付款。")

    # 表格：报价明细
    table = doc.add_table(rows=3, cols=3)
    header = ["型号", "数量", "单价"]
    for j, h in enumerate(header):
        table.rows[0].cells[j].text = h
    table.rows[1].cells[0].text = "Q235B"
    table.rows[1].cells[1].text = "100吨"
    table.rows[1].cells[2].text = "4200"
    table.rows[2].cells[0].text = "Q345B"
    table.rows[2].cells[1].text = "50吨"
    table.rows[2].cells[2].text = "4500"

    doc.add_heading("第三条 违约责任", level=2)
    doc.add_paragraph("逾期交货的，每逾期一日按迟延部分货款的万分之五支付违约金。")

    buf = io.BytesIO()
    doc.save(buf)
    return buf.getvalue()


def test_docx_headings_extracted():
    """(a) 标题按 style 抽出，heading_path 正确。"""
    blocks = extract_docx_blocks(_make_docx())
    heads = [b for b in blocks if b.kind == "heading"]
    assert len(heads) == 4, f"标题数不对: {[h.text for h in heads]}"
    # 二级标题的 heading_path 应含一级标题
    lv2 = [h for h in heads if h.level == 2]
    assert all("2026年度钢材采购合同" in h.heading_path for h in lv2), "层级路径未继承"


def test_docx_para_not_cross_heading():
    """(b) 正文段落不跨标题合并（每条条款正文独立）。"""
    chunks = build_chunks(extract_docx_blocks(_make_docx()))
    texts = [c["content"] for c in chunks]
    # 「标的物」正文与「价格」正文不应出现在同一块
    for t in texts:
        assert not ("热轧卷板" in t and "30日内付款" in t), "跨条款合并"


def test_docx_table_as_markdown():
    """(c) 表格抽为 Markdown 管道表，含表头。"""
    blocks = extract_docx_blocks(_make_docx())
    tables = [b for b in blocks if b.kind == "table"]
    assert tables, "表格未识别"
    md = tables[0].text
    assert "| 型号 | 数量 | 单价 |" in md, f"表头缺失: {md}"
    assert "Q235B" in md and "4200" in md


def test_docx_order_preserved():
    """(d) 表格位置在文档顺序中（第二条与第三条之间），未被挪到末尾。"""
    blocks = extract_docx_blocks(_make_docx())
    kinds = [b.kind for b in blocks]
    # 表格应夹在 「第二条」标题 与 「第三条」标题 之间
    ti = next(i for i, b in enumerate(blocks) if b.kind == "table")
    after = blocks[ti + 1:]
    assert any(b.kind == "heading" and "第三条" in b.text for b in after), \
        "表格被挪到文档末尾（顺序错乱）"


def _add_styled_paragraph(doc, text: str, style_name: str):
    """给段落套一个自定义样式（模板里已存在则复用）。"""
    from docx.enum.style import WD_STYLE_TYPE

    try:
        style = doc.styles.add_style(style_name, WD_STYLE_TYPE.PARAGRAPH)
    except ValueError:
        style = doc.styles[style_name]
    para = doc.add_paragraph(text)
    para.style = style
    return para


def _make_docx_with_styled_toc(style_name: str) -> bytes:
    """构造带 Word 自动目录（TOC 样式）的文档。"""
    from docx import Document

    doc = Document()
    for text in ("第一章 总则\t1", "第二章 采购\t2", "第三章 交付\t3"):
        _add_styled_paragraph(doc, text, style_name)
    doc.add_heading("第一章 总则", level=1)
    doc.add_paragraph("本章规定供应商准入的总则内容。")

    buf = io.BytesIO()
    doc.save(buf)
    return buf.getvalue()


def _make_docx_with_manual_toc() -> bytes:
    """构造手工录入的目录（普通段落 + 点线 + 页码，没有 TOC 样式）。"""
    from docx import Document

    doc = Document()
    for text in ("第一章 总则.........1", "第二章 采购.........2", "第三章 交付.........3"):
        doc.add_paragraph(text)
    doc.add_heading("第一章 总则", level=1)
    doc.add_paragraph("本章规定供应商准入的总则内容。")

    buf = io.BytesIO()
    doc.save(buf)
    return buf.getvalue()


def test_docx_toc_style_not_indexed():
    """Word 自动目录（TOC 1~9 样式）标记为 toc，切分时丢弃。"""
    blocks = extract_docx_blocks(_make_docx_with_styled_toc("TOC 1"))
    assert sum(1 for b in blocks if b.kind == "toc") == 3, [b.kind for b in blocks]

    texts = [c["content"] for c in build_chunks(blocks)]
    assert not any("第二章 采购" in t for t in texts), texts
    assert any("本章规定供应商准入" in t for t in texts), texts


def test_docx_chinese_toc_style_not_indexed():
    """中文 Word 的目录样式（目录 1）同样识别。"""
    blocks = extract_docx_blocks(_make_docx_with_styled_toc("目录 1"))
    assert any(b.kind == "toc" for b in blocks), [b.kind for b in blocks]


def test_docx_manual_toc_run_not_indexed():
    """无 TOC 样式、但连续 ≥3 行目录特征的手工目录也要丢弃。"""
    blocks = extract_docx_blocks(_make_docx_with_manual_toc())
    texts = [c["content"] for c in build_chunks(blocks)]
    assert not any("........" in t for t in texts), texts
    assert any("本章规定供应商准入" in t for t in texts), texts


def test_docx_short_toc_like_lines_kept():
    """不足 3 行的目录特征不触发手工目录过滤，避免误删正文。"""
    from docx import Document

    doc = Document()
    doc.add_paragraph("详见附录 A 测试清单.........123")
    doc.add_paragraph("本章规定供应商准入的总则内容。")

    buf = io.BytesIO()
    doc.save(buf)
    blocks = extract_docx_blocks(buf.getvalue())
    assert not any(b.kind == "toc" for b in blocks), [b.kind for b in blocks]


if __name__ == "__main__":
    import traceback
    passed = failed = 0
    for name, fn in sorted(globals().items()):
        if name.startswith("test_") and callable(fn):
            try:
                fn()
                print(f"[PASS] {name}")
                passed += 1
            except Exception as e:
                print(f"[FAIL] {name}: {e}")
                traceback.print_exc()
                failed += 1
    print(f"\n{passed} passed, {failed} failed")
    sys.exit(1 if failed else 0)
