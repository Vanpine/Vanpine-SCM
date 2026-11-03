"""
system prompt 缓存前缀测试

上游按「请求前缀逐字节相同」做 prompt 缓存（命中后这部分计费约 1/10）。
本测试锁住两条不变量：
  (a) 不同问题、同路由的 system prompt 必须有共同的稳定前缀
      （动态部分只能加在末尾，不能在中间插）
  (b) 该稳定前缀就是 STATIC_SYSTEM_PROMPT 常量本身
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

from app.services.chat import (  # noqa: E402
    AGENT_STATIC_PROMPT, KNOWLEDGE_STATIC_PROMPT, STATIC_SYSTEM_PROMPT,
    build_system_prompt,
)


def _common_prefix(a: str, b: str) -> str:
    n = 0
    while n < min(len(a), len(b)) and a[n] == b[n]:
        n += 1
    return a[:n]


def test_agent_route_common_prefix_is_route_static():
    """(a) 同为 agent 路由，不同问题的公共前缀覆盖整个路由静态前缀。"""
    a = build_system_prompt("查一下今年的合同", "agent")
    b = build_system_prompt("广州天气怎么样", "agent")
    # 两个动态块都以 "\n[" 开头，公共前缀会多出 2 个巧合字符，断言「至少覆盖」静态前缀
    assert _common_prefix(a, b).startswith(AGENT_STATIC_PROMPT)


def test_static_prefix_is_cross_route_base():
    """跨路由至少有基础前缀可复用（knowledge 与 agent 共用 BASE + HISTORY）。"""
    a = build_system_prompt("违约金条款怎么规定", "knowledge")
    b = build_system_prompt("查一下今年的合同", "agent")
    assert _common_prefix(a, b).startswith(STATIC_SYSTEM_PROMPT)


def test_anchor_is_at_tail():
    """业务锚点加在末尾，不会破坏前面的稳定前缀。"""
    with_anchor = build_system_prompt("查一下今年的合同", "agent")
    without_anchor = build_system_prompt("帮我看看", "agent")
    assert with_anchor.startswith(without_anchor)


def test_knowledge_route_also_fixed_order():
    """(b) knowledge 路由同样以静态前缀开头，且两次拼接结果一致。"""
    a = build_system_prompt("违约金条款怎么规定", "knowledge")
    b = build_system_prompt("准入要求是什么", "knowledge")
    assert a.startswith(KNOWLEDGE_STATIC_PROMPT)
    assert a == b, "无天气词的 knowledge 问题应生成完全相同的 prompt（缓存可命中）"


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
