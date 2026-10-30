"""
上下文规模观测测试（阶段一：token 计量）

measure_context 只观测、不改行为，但必须按 build_messages 的真实口径算：
  - 历史按 _history_message 截断后的文本计（长回答已被砍到 120 字）
  - 工具消息（含 tool_calls / tool_call_id）不计入
  - 空内容消息不计入
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.services.chat import measure_context  # noqa: E402
from app.services.tokenizer import count_tokens  # noqa: E402


def test_empty_history():
    """无历史时只算 system + 提问。"""
    ctx = measure_context("查我的合同", [])
    assert ctx["historyCount"] == 0
    assert ctx["historyTokens"] == 0
    assert ctx["totalTokens"] == ctx["systemTokens"] + ctx["questionTokens"]
    assert ctx["systemTokens"] > 0, "system prompt 必然占 token"


def test_counts_valid_history_only():
    """只统计 user/assistant 的有效消息，工具消息与空内容不算。"""
    history = [
        {"role": "user", "content": "查我的合同"},
        {"role": "assistant", "content": "共 3 份合同。"},
        {"role": "assistant", "content": "", "tool_calls": [{"name": "list_contracts"}]},
        {"role": "tool", "content": "raw json", "tool_call_id": "c1"},
    ]
    ctx = measure_context("继续", history)
    assert ctx["historyCount"] == 2, f"只应算 2 条: {ctx}"


def test_long_answer_measured_after_truncation():
    """长回答按截断后的口径计，不等于原文 token 数。"""
    long_answer = "共 15 条询盘。\n\n" + "| 状态 | 标题 |\n|---|---|\n" * 60
    ctx = measure_context("继续", [{"role": "assistant", "content": long_answer}])
    full_tokens = count_tokens(long_answer)
    assert ctx["historyTokens"] < full_tokens, "长回答应被截断后计量"
    assert ctx["historyTokens"] < full_tokens * 0.5, f"截断应显著: {ctx['historyTokens']} vs {full_tokens}"


def test_total_is_sum_of_parts():
    """合计 = 系统 + 历史 + 提问。"""
    ctx = measure_context("第2个呢", [{"role": "user", "content": "我的寻盘列表"}])
    assert ctx["totalTokens"] == ctx["systemTokens"] + ctx["historyTokens"] + ctx["questionTokens"]
    assert ctx["questionTokens"] > 0


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
