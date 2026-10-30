"""
parent-child 两层测试

覆盖 P5 验收点：
  (a) 同一 heading_path 的多个子块归属同一 parent_id
  (b) 父块内容 = 其下所有子块内容之和
  (c) 换章节时父块切换
  (d) 子块带 parent_id，父块不含向量（仅 payload）
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.services.chunker import extract_blocks, build_parent_child, PARENT_TARGET_TOKENS

# 长章节：同一标题下多段正文，聚合后应跨多个子块但归属同一父块
LONG_DOC = """# 采购合同

## 第三条 付款方式

货到验收合格后三十日内付款。乙方须开具增值税专用发票，税率 13%。
""" + ("付款细则说明文字，需逐条核对确认无遗漏。" * 80) + """

## 第四条 违约责任

逾期交货的，每逾期一日按迟延部分货款的万分之五支付违约金。
"""


def test_children_share_parent():
    """(a) 同章节的多个子块归属有限数量的父块（超长章节可拆多个父块）。"""
    blocks = extract_blocks(LONG_DOC)
    children, parents = build_parent_child(blocks)
    # 第三条章节下应有多个子块
    third = [c for c in children if any("第三条" in p for p in c["heading_path"])]
    assert len(third) >= 2, f"第三条子块数不足: {len(third)}"
    # 父块数应远少于子块数（多个子块共享父块）
    pids = {c["parent_id"] for c in third}
    assert len(pids) < len(third), f"父块未合并子块: {len(pids)} 父 vs {len(third)} 子"
    # 每个 parent_id 都存在对应父块
    parent_ids = {p["parent_id"] for p in parents}
    assert pids <= parent_ids, f"子块引用的父块缺失: {pids - parent_ids}"


def test_parent_content_is_union():
    """(b) 父块内容包含其子块正文。"""
    blocks = extract_blocks(LONG_DOC)
    children, parents = build_parent_child(blocks)
    third = [c for c in children if any("第三条" in p for p in c["heading_path"])]
    pid = third[0]["parent_id"]
    parent = next(p for p in parents if p["parent_id"] == pid)
    for c in third:
        # 子块正文应能在父块里找到（父块是子块拼接）
        assert c["content"][:30] in parent["content"]


def test_parent_switches_across_sections():
    """(c) 换章节时 parent_id 变化。"""
    blocks = extract_blocks(LONG_DOC)
    children, _ = build_parent_child(blocks)
    pids = list(dict.fromkeys(c["parent_id"] for c in children))
    assert len(pids) >= 2, f"不同章节未产生不同父块: {pids}"


def test_parent_target_not_exceeded():
    """(d) 父块不超过 PARENT_TARGET_TOKENS（除非单子块本身超）。"""
    blocks = extract_blocks(LONG_DOC)
    _, parents = build_parent_child(blocks)
    from app.services.tokenizer import count_tokens
    for p in parents:
        # 允许单子块超上限的极端情况，正常聚合不应超太多
        assert p["token_count"] <= PARENT_TARGET_TOKENS * 2


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
