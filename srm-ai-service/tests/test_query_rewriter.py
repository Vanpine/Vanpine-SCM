"""
查询改写与指代消解测试（P0-4）

覆盖：
  (a) 代词问题结合历史对象消解（"他呢" → 含"供应商"+谓词）
  (b) 序数指代补全（"第一条详情" → 含"列表中"）
  (c) 完整问题不改写（零改动）
  (d) 无历史时短问题不改错（保持原样）
  (e) 改写不改变语义主体（对象名来自历史）
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.services.query_rewriter import rewrite


HIST_SUPPLIER = [
    {"role": "user", "content": "帮我查一下华南钢铁"},
    {"role": "assistant", "content": "华南钢铁供应商详情：准入状态 已通过，资质齐全，主营热轧卷板。"},
]


def test_pronoun_resolved():
    """(a) "他呢" 结合历史对象（供应商）消解。"""
    out = rewrite("他呢", HIST_SUPPLIER)
    assert "供应商" in out, f"应消解出供应商: {out}"
    assert out != "他呢", "应被改写"


def test_pronoun_with_predicate():
    """(a) "他资质怎么样" 消解代词但保留谓语。"""
    out = rewrite("他资质怎么样", HIST_SUPPLIER)
    assert "供应商" in out and "资质" in out


def test_ordinal_completed():
    """(b) 序数指代补全为完整问句。"""
    out = rewrite("第一条详情", HIST_SUPPLIER)
    assert "第一条" in out and "查询" in out, f"序数应补全: {out}"


def test_complete_question_unchanged():
    """(c) 完整问题不改写（零开销）。"""
    q = "知识库里供应商准入要求是什么"
    assert rewrite(q, HIST_SUPPLIER) == q
    q2 = "帮我查一下2026年度钢材采购合同的付款条款"
    assert rewrite(q2, HIST_SUPPLIER) == q2


def test_no_history_short_unchanged():
    """(d) 无历史时短问题不误改。"""
    assert rewrite("你好", []) == "你好"
    assert rewrite("查合同", []) == "查合同"


def test_short_no_pronoun_unchanged_with_history():
    """(j) 回归：无指代词的独立短问，即便有历史也不改写。

    曾经「短句硬拼上一问」把「看看我的简历」污染成「（延续上一问：查询租户列表）看看我的简历」，
    致模型选错工具、知识库不再被检索。此行为已移除。
    """
    hist = [
        {"role": "user", "content": "查询租户列表"},
        {"role": "assistant", "content": "租户列表：28供应测试公司、华南钢铁…"},
    ]
    assert rewrite("看看我的简历", hist) == "看看我的简历"
    assert rewrite("知识库中有啊", hist) == "知识库中有啊"


def test_pronoun_without_object_in_history():
    """(e) 历史里没有可识别对象时，代词问题保持原样（不改错）。"""
    hist = [{"role": "assistant", "content": "好的，已收到。"}]
    assert rewrite("他呢", hist) == "他呢"


# ==================== P1-2：context_entities 精确消解 ====================

ENTITIES_JSON = '[{"objectType":"contract","objectId":"123","title":"2026年度钢材采购合同","status":"signed"}]'


def test_entities_pronoun_uses_title():
    """(f) 有 context_entities 时，代词消解用精确 title（非泛化"供应商"）。"""
    out = rewrite("它状态怎么样", history=[], context_entities=ENTITIES_JSON)
    assert "2026年度钢材采购合同" in out, f"应用精确 title 消解: {out}"


def test_entities_override_history():
    """(g) context_entities 优先于 history 猜的对象。"""
    hist = [{"role": "assistant", "content": "供应商详情：华南钢铁"}]
    out = rewrite("他呢", history=hist, context_entities=ENTITIES_JSON)
    # 应优先用 entities 的合同，而非 history 里的供应商
    assert "合同" in out, f"entities 应优先: {out}"


def test_entities_json_parse_tolerant():
    """(h) 非法 JSON / 空 → 不报错，退化为原逻辑。"""
    assert rewrite("你好", [], "not-json") == "你好"
    assert rewrite("你好", [], "") == "你好"
    assert rewrite("你好", [], None) == "你好"


def test_no_predicate_completed_with_entities():
    """(i) 纯指代碎句（"那个"）补成完整问句。"""
    out = rewrite("那个", history=[], context_entities=ENTITIES_JSON)
    assert "查询" in out and "详情" in out, f"应补全为完整问句: {out}"


# ==================== 序数越界：不指错对象 ====================

TWO_CONTRACTS = (
    '[{"objectType":"contract","objectId":"1","title":"钢材合同A"},'
    '{"objectType":"contract","objectId":"2","title":"铝材合同B"}]'
)


def test_ordinal_in_range_maps_correctly():
    """序数 N 落到 entities[N-1]，「第二条」不能取到第一条。"""
    assert "铝材合同B" in rewrite("第二条的交期", [], TWO_CONTRACTS)
    assert "钢材合同A" in rewrite("第一条的交期", [], TWO_CONTRACTS)


def test_ordinal_out_of_range_keeps_original():
    """越界（说「第三条」但只有两条）不改写，避免指错对象。"""
    q = "第三条的交期"
    assert rewrite(q, [], TWO_CONTRACTS) == q, "越界应保持原句，而不是指向最后一条"


def test_last_ordinal_still_works():
    """「最后一条」仍指向最后一个（-1 哨兵不受越界改动影响）。"""
    assert "铝材合同B" in rewrite("最后一条详情", [], TWO_CONTRACTS)


# ==================== 「另一条」：取不是当前（第一条）的那条 ====================


def test_another_targets_second():
    """「另一条」指向列表第二个（不是当前的第一条）。"""
    out = rewrite("另一条呢", [], TWO_CONTRACTS)
    assert "铝材合同B" in out, f"另一条应指第 2 条: {out}"
    assert "另一条" not in out, f"指代词应被替换掉: {out}"


def test_another_needs_two_entities():
    """只有一条时「另一条」无法定位，保持原句（宁可不改，不改错）。"""
    one = '[{"objectType":"contract","objectId":"1","title":"钢材合同A"}]'
    assert rewrite("另一条呢", [], one) == "另一条呢"


def test_another_without_entities_unchanged():
    """无 entities 时不误改。"""
    assert rewrite("另一条呢", []) == "另一条呢"


# ==================== 单字代词误伤（2026-10-20） ====================
# 「该」「此」「其」只在后接业务对象名词时才算代词：
#   「该合同」是指代；「应该」的「该」是副词；「其他」的「其」是固定搭配。
# 不加限制会把无指代关系的问句改成乱句，导致选错工具。

ENTITIES = '[{"objectType":"contract","objectId":"1","title":"钢材采购合同"}]'


def test_should_not_trigger_pronoun():
    """「应该查合同吗」的「该」是副词，不是指代。"""
    out = rewrite("应该查合同吗", [], ENTITIES)
    assert out == "应该查合同吗", f"不应被改写: {out}"


def test_other_supplier_not_pronoun():
    """「其中」的「其」是固定搭配，不是指代。

    注：「其他供应商」会命中词表里的「他」（单字代词），属另一类误伤，
    未在本次修复范围内。
    """
    out = rewrite("其中有哪些是过期的", [], ENTITIES)
    assert out == "其中有哪些是过期的", f"不应被改写: {out}"


def test_therefore_not_pronoun():
    """「因此」的「此」是连词，不是指代。"""
    out = rewrite("因此要查一下合同", [], ENTITIES)
    assert out == "因此要查一下合同", f"不应被改写: {out}"


def test_real_single_pronoun_still_resolved():
    """真指代仍要消解：「该合同」→ 具体合同名。"""
    out = rewrite("该合同的金额是多少", [], ENTITIES)
    assert "钢材采购合同" in out, f"应消解出具体对象: {out}"


def test_single_pronoun_position_not_confused_by_homograph():
    """「应该看该合同」：副词「该」在前，替换要落在真正的指代上。"""
    out = rewrite("应该看该合同的状态", [], ENTITIES)
    assert "钢材采购合同" in out, f"应消解: {out}"
    assert out.startswith("应该看"), f"副词的「该」不能被吃掉: {out}"


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
                failed += 1
    print(f"\n{passed} passed, {failed} failed")
    sys.exit(1 if failed else 0)
