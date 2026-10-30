"""
工具业务对象回传测试（context_entities 的数据来源）

链路：Java 工具响应带 entities → Python execute_tool 透传 → chat 层放进
tool_result / confirm_required 事件回传 → Java updateContextEntities 落库。

覆盖：
  (a) 读工具成功 → tool_result 事件带 entities
  (b) 写工具二次确认短路 → confirm_required 事件带 entities（供下一轮「再给它延长一次」定位）
  (c) 工具无 entities（如知识库/天气）→ 事件里为空列表，不报错
"""
import asyncio
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import app.services.chat as chat  # noqa: E402


CONTRACT = {"objectType": "contract", "objectId": "11", "title": "钢材采购合同"}


class _FakeChunk:
    """最小 LLM chunk：支持 + 合并，带 tool_calls / tool_call_chunks。"""

    def __init__(self, content="", tool_calls=None, tool_call_chunks=None):
        self.content = content
        self.tool_calls = tool_calls or []
        self.tool_call_chunks = tool_call_chunks or []

    def __add__(self, other):
        return _FakeChunk(
            content=(self.content or "") + (other.content or ""),
            tool_calls=list(self.tool_calls) + list(other.tool_calls),
            tool_call_chunks=list(self.tool_call_chunks) + list(other.tool_call_chunks),
        )


class _FakeLLM:
    """按调用轮次吐出预置 chunk 序列（忽略 messages）。"""

    def __init__(self, rounds):
        self._rounds = list(rounds)
        self.calls = 0

    async def astream(self, messages):
        chunks = self._rounds[min(self.calls, len(self._rounds) - 1)]
        self.calls += 1
        for c in chunks:
            yield c


def _tool_round(name):
    """第一轮：模型发起一次工具调用（无正文）。"""
    call = {"name": name, "args": {}, "id": "call-1"}
    return [_FakeChunk(tool_calls=[call], tool_call_chunks=[{"name": name}])]


def _answer_round():
    """第二轮：模型直接给正文，不再调工具。"""
    return [_FakeChunk(content="查到了。")]


def _run_agent(tool_result, rounds):
    """跑一遍 stream_chat_agent，返回事件列表（临时替换 LLM 与工具执行）。"""
    original = (chat.execute_tool, chat._get_tool_llm, chat._get_chat_llm)

    async def fake_execute_tool(name, args, tool_token, tenant_id=None):
        return tool_result

    chat.execute_tool = fake_execute_tool
    chat._get_tool_llm = lambda question: _FakeLLM(rounds)
    chat._get_chat_llm = lambda: _FakeLLM([_answer_round()])
    try:
        events = []
        gen = chat.stream_chat_agent("查我的合同", [], [], "tok", 1)
        while True:
            try:
                events.append(asyncio.run(gen.__anext__()))
            except StopAsyncIteration:
                break
        return events
    finally:
        chat.execute_tool, chat._get_tool_llm, chat._get_chat_llm = original


def _ok(**extra):
    base = {"success": True, "result": [], "error": None}
    base.update(extra)
    return base


def test_tool_result_carries_entities():
    """(a) 读工具成功 → tool_result 事件带 entities。"""
    events = _run_agent(_ok(entities=[CONTRACT]), [_tool_round("list_contracts"), _answer_round()])
    results = [e for e in events if e["type"] == "tool_result"]
    assert results, f"应有 tool_result 事件: {[e['type'] for e in events]}"
    assert results[0]["entities"] == [CONTRACT], f"实体未透传: {results[0]}"


def test_confirm_required_carries_entities():
    """(b) 写工具二次确认 → confirm_required 事件带 entities。"""
    events = _run_agent(
        _ok(requireConfirm=True, proposalId="p1", actionSummary="记录供应商备注：华南钢铁",
            entities=[{"objectType": "supplier", "objectId": "9", "title": "华南钢铁"}]),
        [_tool_round("create_supplier_note")])
    confirms = [e for e in events if e["type"] == "confirm_required"]
    assert confirms, f"应有 confirm_required 事件: {[e['type'] for e in events]}"
    assert confirms[0]["entities"][0]["title"] == "华南钢铁"


def test_tool_result_without_entities_is_empty_list():
    """(c) 工具无实体（知识库/天气）→ 事件里为空列表，不报错。"""
    events = _run_agent(_ok(), [_tool_round("search_knowledge_base"), _answer_round()])
    results = [e for e in events if e["type"] == "tool_result"]
    assert results and results[0]["entities"] == []


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
