"""
总上下文预算兜底测试

各部分（system / 摘要 / 历史 / 工具结果）各有上限，但总和仍需一条总闸。
规则：
  - 超预算从最老历史开始丢；system 与当前提问永远保留
  - 历史丢完仍超 → 丢摘要
  - 丢完窗口开头若是 assistant（悬空回答），一并剥掉
"""
import sys
import types
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

# 打桩非标准库依赖（本机可能缺 langchain）
for _name, _attrs in {
    "langchain_openai": ("ChatOpenAI",),
    "langchain_core.messages": ("AIMessage", "ToolMessage", "HumanMessage"),
    "langchain_core.prompts": ("ChatPromptTemplate",),
}.items():
    if _name not in sys.modules:
        m = types.ModuleType(_name)
        for a in _attrs:
            setattr(m, a, type(a, (), {"bind_tools": lambda self, *a, **k: self}))
        sys.modules[_name] = m
sys.modules["langchain_core.prompts"].ChatPromptTemplate = type(
    "ChatPromptTemplate", (), {"from_messages": staticmethod(lambda *a, **k: object())}
)
import app.services.tools as _tools  # noqa: E402

import app.services.context_budget as context_budget  # noqa: E402  # 预算常量已拆到此模块，patch 必须打在读取方
from app.services.chat import build_messages  # noqa: E402

# 每条历史给足 token（400 字 ≈ 400 token）
_LONG = "字" * 400


def _history(n):
    """n 轮（user + assistant）历史。"""
    rows = []
    for i in range(n):
        rows.append({"role": "user", "content": f"{i}号提问{_LONG}"})
        rows.append({"role": "assistant", "content": f"{i}号回答{_LONG}"})
    return rows


def test_within_budget_keeps_everything():
    """预算充足时一条不丢。"""
    msgs = build_messages("当前提问", _history(2), [])
    assert len(msgs) == 1 + 4 + 1, f"system + 4 条历史 + 提问: {len(msgs)}"


def test_over_budget_drops_oldest_first():
    """超预算：保留最近的，丢最老的。"""
    old_limit = context_budget.TOTAL_CONTEXT_TOKEN_BUDGET
    # 预算 10000 - tools 预留 6000 = messages 额度 4000；system 约 1650，剩约 2350 给历史
    context_budget.TOTAL_CONTEXT_TOKEN_BUDGET = 10000
    try:
        msgs = build_messages("当前提问", _history(10), [])
    finally:
        context_budget.TOTAL_CONTEXT_TOKEN_BUDGET = old_limit

    assert msgs[0][0] == "system"
    assert msgs[-1][1] == "当前提问"
    # 已明显收缩
    assert len(msgs) < 21
    # 保留的是最近的几条历史（0 号最早，应已丢弃）
    contents = "".join(str(m[1]) for m in msgs)
    assert "0号提问" not in contents, "最老的历史应先被丢弃"
    assert "9号提问" in contents, "最近的历史应保留"


def test_question_never_dropped():
    """当前提问永远保留，哪怕历史全丢。"""
    old_limit = context_budget.TOTAL_CONTEXT_TOKEN_BUDGET
    # 额度刚好只剩 system（1650）+ 一点余量，历史必须全清
    context_budget.TOTAL_CONTEXT_TOKEN_BUDGET = 8000
    try:
        msgs = build_messages("这是必须保留的提问", _history(5), [])
    finally:
        context_budget.TOTAL_CONTEXT_TOKEN_BUDGET = old_limit

    assert msgs[-1][1] == "这是必须保留的提问"
    assert msgs[0][0] == "system"


def test_summary_dropped_after_history():
    """历史丢完仍超 → 丢摘要。"""
    old_limit = context_budget.TOTAL_CONTEXT_TOKEN_BUDGET
    context_budget.TOTAL_CONTEXT_TOKEN_BUDGET = 8000
    try:
        msgs = build_messages(
            "提问", _history(5), [],
            history_summary="这是一段很长的摘要" + "字" * 500,
        )
    finally:
        context_budget.TOTAL_CONTEXT_TOKEN_BUDGET = old_limit

    assert not any("早前对话摘要" in str(m[1]) for m in msgs), "历史清空后应丢弃摘要"
    assert msgs[-1][1] == "提问"


def test_dangling_assistant_stripped():
    """丢历史后窗口开头若是 assistant，一并剥掉。"""
    old_limit = context_budget.TOTAL_CONTEXT_TOKEN_BUDGET
    context_budget.TOTAL_CONTEXT_TOKEN_BUDGET = 9500
    try:
        msgs = build_messages("提问", _history(6), [])
    finally:
        context_budget.TOTAL_CONTEXT_TOKEN_BUDGET = old_limit

    body = msgs[1:-1]
    if body:
        assert body[0][0] == "user", f"窗口应以 user 开头: {[m[0] for m in body]}"


if __name__ == "__main__":
    passed = failed = 0
    for name, fn in sorted(globals().items()):
        if name.startswith("test_") and callable(fn):
            try:
                fn()
                passed += 1
                print(f"  PASS {name}")
            except AssertionError as e:
                failed += 1
                print(f"  FAIL {name}: {e}")
    print(f"\n{passed} passed, {failed} failed")
    sys.exit(1 if failed else 0)
