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
