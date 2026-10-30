"""
工具循环防重复吐字测试

模型一轮生成里可能先吐「我来帮你查一下。」再决定调工具。这段过程话术不能吐给用户，
否则用户会先看到它、再看到工具结果后的真正答案（重复噪声）。

覆盖 _emit_round_text 的判定：
  (a) 本轮有工具调用 → 丢弃攒下的文本
  (b) 本轮无工具调用 → 全部吐出（这就是最终答案）
  (c) 空攒 → 都返回空，不崩
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.services.chat import _emit_round_text


def test_tool_call_round_drops_preamble():
    """(a) 有工具调用：过程话术丢弃。"""
    assert _emit_round_text(["我来帮你查一下。", "请稍等"], has_tool_calls=True) == []


def test_plain_round_emits_answer():
    """(b) 无工具调用：攒下的就是最终答案，原样吐出（顺序不变）。"""
    parts = ["钢材合同共 ", "3 份：", "A、B、C"]
    assert _emit_round_text(parts, has_tool_calls=False) == parts


def test_empty_pending_never_crashes():
    """(c) 空攒：两种分支都返回空。"""
    assert _emit_round_text([], has_tool_calls=True) == []
    assert _emit_round_text([], has_tool_calls=False) == []


def test_returns_copy_not_alias():
    """(b) 返回副本，避免调用方误改后台缓冲。"""
    parts = ["答案"]
    out = _emit_round_text(parts, has_tool_calls=False)
    out.append("注入")
    assert parts == ["答案"]


if __name__ == "__main__":
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
