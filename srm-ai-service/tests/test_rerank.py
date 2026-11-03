"""
RAG 检索质量门禁测试（P0-1）

覆盖：
  (a) rerank 分数低（低于阈值）→ 过滤掉，retrieve 返回空
  (b) rerank 异常 → 返回空（不回退原序）
  (c) 高分 chunk → 正常返回
  (d) 未配置 key 时 → 保持旧行为（退回原序，不过滤）
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.services import rerank as rerank_mod


def _patch(monkey_score=None, raise_exc=False):
    """临时替换 _call_rerank + 保证 key/enabled 就绪，返回 restore 函数。"""
    orig_call = rerank_mod._call_rerank
    orig_key = rerank_mod._API_KEY
    orig_en = rerank_mod.RERANK_ENABLED
    rerank_mod._API_KEY = "test-key"
    rerank_mod.RERANK_ENABLED = True
    if raise_exc:
        def boom(q, docs):
            raise TimeoutError("simulated")
        rerank_mod._call_rerank = boom
    else:
        rerank_mod._call_rerank = lambda q, docs: monkey_score

    def restore():
        rerank_mod._call_rerank = orig_call
        rerank_mod._API_KEY = orig_key
        rerank_mod.RERANK_ENABLED = orig_en
    return restore


def test_low_score_filtered_returns_empty():
    """(a) 全部低于阈值 → 返回空（用 0.0 分，低于任何阈值的兜底不触发）。"""
    restore = _patch(monkey_score=[0.0, 0.0])   # 阈值默认 0.05
    try:
        cands = [{"content": "a"}, {"content": "b"}]
        out = rerank_mod.rerank("q", cands, 5)
        assert out == [], f"0 分应被过滤为空: {out}"
    finally:
        restore()


def test_all_below_threshold_falls_back_to_top1():
    """(a) 回归：全部低于阈值但分数 >0 → 保底返回最高分那条（避免误判"知识库没有"）。"""
    restore = _patch(monkey_score=[0.02, 0.03])   # 均 < 0.05 阈值，但 >0
    try:
        cands = [{"content": "a"}, {"content": "b"}]
        out = rerank_mod.rerank("q", cands, 5)
        assert len(out) == 1, f"保底应返回 1 条: {out}"
        assert out[0]["content"] == "b", f"应返回最高分 b: {out}"
    finally:
        restore()


def test_high_score_kept():
    """(c) 高于阈值 → 保留并按分数排序。"""
    restore = _patch(monkey_score=[0.9, 0.35, 0.8])
    try:
        cands = [{"content": "a"}, {"content": "b"}, {"content": "c"}]
        out = rerank_mod.rerank("q", cands, 5)
        scores = [c["rerank_score"] for c in out]
        assert scores == [0.9, 0.8, 0.35], f"应按分数降序且全达标: {scores}"
        assert [c["content"] for c in out] == ["a", "c", "b"]
    finally:
        restore()


def test_mixed_below_threshold_dropped():
    """(a) 混合：低于阈值(0.05)的丢弃，达标的保留。"""
    restore = _patch(monkey_score=[0.5, 0.01, 0.4])
    try:
        cands = [{"content": "a"}, {"content": "b"}, {"content": "c"}]
        out = rerank_mod.rerank("q", cands, 5)
        scores = sorted([c["rerank_score"] for c in out], reverse=True)
        assert scores == [0.5, 0.4], f"应丢弃低分 b(0.01): {out}"
        assert [c["content"] for c in out] == ["a", "c"], f"顺序按分降序: {out}"
    finally:
        restore()


def test_rerank_exception_returns_empty():
    """(b) rerank 异常 → 返回空，不回退原序。"""
    restore = _patch(raise_exc=True)
    try:
        cands = [{"content": "a"}, {"content": "b"}]
        out = rerank_mod.rerank("q", cands, 5)
        assert out == [], f"异常应返回空而非原序: {out}"
    finally:
        restore()


def test_no_key_keeps_old_behavior():
    """(d) 未配置 key → 退回原序前 top_n（无分数可判）。"""
    orig_key = rerank_mod._API_KEY
    rerank_mod._API_KEY = ""
    try:
        cands = [{"content": "a"}, {"content": "b"}, {"content": "c"}]
        out = rerank_mod.rerank("q", cands, 2)
        assert [c["content"] for c in out] == ["a", "b"]
    finally:
        rerank_mod._API_KEY = orig_key


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
