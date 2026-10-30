"""
结构化切分器测试

覆盖 P1 验收点：
  (a) 不出现跨「第X条」的块
  (b) 目录行整段识别且未入库
  (c) 每块 token ≤ CHUNK_MAX_TOKENS
  (d) 规格书连续编号行未被切成单行标题
  (e) 递归字符兜底：超长无结构文本被切开且带重叠
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.services.tokenizer import count_tokens
from app.services.chunker import (
    extract_blocks, build_chunks,
    CHUNK_MAX_TOKENS, CHUNK_TARGET_TOKENS,
)

# ============ 样例：中文采购合同（含目录页） ============
CONTRACT_MD = """# 2026年度钢材采购合同

## 目录

第一条 标的物 ......... 1
第二条 数量与质量 ......... 2
第三条 付款方式 ......... 3
第四条 违约责任 ......... 4

## 第一条 标的物

本合同标的物为热轧卷板，规格 Q235B，产地不限。乙方须按甲方订单载明的
型号、数量、交期供货，不得擅自变更。

## 第二条 数量与质量

数量以甲方订单为准，允许 ±3% 溢短装。质量标准执行 GB/T 3274，随货提供
材质证明书与出厂检验报告。

## 第三条 付款方式

货到验收合格后三十日内付款。乙方须开具增值税专用发票，税率 13%。

## 第四条 违约责任

逾期交货的，每逾期一日按迟延部分货款的万分之五向甲方支付违约金。
质量不合格的，乙方应在七日内无偿更换并承担由此产生的运费。
"""

# ============ 样例：技术规格书（连续编号参数行） ============
SPEC_TXT = """技术规格书

1 输入电压 AC220V 50Hz
2 额定功率 50W
3 工作温度 -10~40℃
4 防护等级 IP54
5 通讯接口 RS485

安装说明

设备应安装在通风干燥处，避免阳光直射。安装前请核对铭牌参数与现场
供电是否匹配。
"""


def test_no_chunk_spans_clauses():
    """(a) 不存在同时含两条不同「第X条」标题的块。"""
    chunks = build_chunks(extract_blocks(CONTRACT_MD))
    clause_marker = "第"
    for c in chunks:
        hits = [ln for ln in c["content"].split("\n")
                if ln.strip().startswith("第") and "条" in ln[:6]]
        different = set()
        for h in hits:
            # 提取「第X条」
            for part in h.split():
                if part.startswith("第") and "条" in part:
                    different.add(part)
        # 条款块允许自身标题 + 正文，但不允许 >1 条"第X条"标题（目录已剔除）
        assert len({d[:4] for d in different}) <= 1, f"块跨条款: {c['content'][:80]}"


def test_toc_excluded():
    """(b) 目录行整段识别为 toc，且不出现在最终分块中。"""
    blocks = extract_blocks(CONTRACT_MD)
    toc = [b for b in blocks if b.kind == "toc"]
    assert toc, "目录未被识别为 toc"
    chunks = build_chunks(blocks)
    for c in chunks:
        assert c["chunk_type"] != "toc", "目录块混入结果"
        assert "......... 1" not in c["content"], "目录点线行混入结果"


def test_max_tokens_respected():
    """(c) 每块 token ≤ CHUNK_MAX_TOKENS。"""
    chunks = build_chunks(extract_blocks(CONTRACT_MD))
    for c in chunks:
        assert count_tokens(c["content"]) <= CHUNK_MAX_TOKENS, \
            f"超限 {count_tokens(c['content'])}: {c['content'][:60]}"


def test_spec_list_not_split():
    """(d) 规格书连续编号行未被切成单行标题，且不是逐行成块。"""
    blocks = extract_blocks(SPEC_TXT)
    lists = [b for b in blocks if b.kind == "list"]
    assert lists, "连续编号行未被识别为 list"
    # 5 行参数应聚成 ≤2 个 list 块，而非 5 个独立块
    spec_blocks = [b for b in blocks if "AC220V" in b.text or "IP54" in b.text]
    assert len(spec_blocks) <= 2, f"规格行被切碎成 {len(spec_blocks)} 块"


def test_long_text_recursive_fallback():
    """(e) 超长无结构文本被递归切分，每块不超上限。"""
    long_para = "这是一段没有任何标题的连续说明文字。" * 200  # 远超 512 token
    chunks = build_chunks(extract_blocks(long_para))
    assert len(chunks) > 1, "超长文本未被切分"
    for c in chunks:
        assert count_tokens(c["content"]) <= CHUNK_MAX_TOKENS


def test_heading_path_propagated():
    """章节路径正确传递到块上。"""
    chunks = build_chunks(extract_blocks(CONTRACT_MD))
    paths = [c["heading_path"] for c in chunks if c["heading_path"]]
    assert any("第三条 付款方式" in " ".join(p) for p in paths), \
        f"章节路径未传递: {paths}"


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
