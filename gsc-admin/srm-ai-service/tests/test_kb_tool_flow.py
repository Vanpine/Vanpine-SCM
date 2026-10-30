"""
链路冒烟测试（P0-2R）：模拟 LLM 发起 search_knowledge_base 工具调用，
验证 stream_chat_agent 能本地执行知识库检索、回填结果、并推 citations 事件。

不依赖真实 LLM / Qdrant：mock 掉 tool_llm.astream 与 rag.retrieve。
"""
import asyncio
import sys
import types
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

# ---- 打桩依赖 ----
for _name, _attrs in {
    "langchain_openai": ("ChatOpenAI",),
    "langchain_core.messages": ("AIMessage", "ToolMessage", "HumanMessage"),
    "langchain_core.prompts": ("ChatPromptTemplate",),
    "qdrant_client": ("QdrantClient",),
    "qdrant_client.models": ("Distance", "VectorParams", "PointStruct", "Filter",
                             "FieldCondition", "MatchValue"),
}.items():
    if _name not in sys.modules:
        m = types.ModuleType(_name)
        for a in _attrs:
            setattr(m, a, object)
        sys.modules[_name] = m
# reasoning.py 模块级调用 ChatPromptTemplate.from_messages(...)，需可调用
sys.modules["langchain_core.prompts"].ChatPromptTemplate = type(
    "ChatPromptTemplate", (), {"from_messages": staticmethod(lambda *a, **k: object())}
)
# 消息类需能接受任意构造参数（AIMessage(content=..., tool_calls=...)）
for _cname in ("AIMessage", "ToolMessage", "HumanMessage"):
    sys.modules["langchain_core.messages"].__dict__[_cname] = type(
        _cname, (), {"__init__": lambda self, *a, **k: None}
    )


class _FakeChunk:
    """模拟 langchain 流式 chunk。"""
    def __init__(self, content="", tool_calls=None):
        self.content = content
        self.tool_calls = tool_calls or []
        self.tool_call_chunks = tool_calls or []

    def __add__(self, other):
        return self


class _FakeLLM:
    """第一轮返回一个 search_knowledge_base 工具调用；第二轮返回文本。"""
    def __init__(self):
        self.round = 0

    async def astream(self, messages):
        self.round += 1
        if self.round == 1:
            yield _FakeChunk(tool_calls=[{
                "name": "search_knowledge_base",
                "args": {"query": "供应商准入要求"},
                "id": "call_1",
            }])
        else:
            yield _FakeChunk(content="根据制度，准入需提供营业执照与资质证明。")


def test_kb_tool_flow():
    import app.services.chat as chat
    import app.services.rag as rag

    # 改的都是模块级全局，必须还原：不还原会污染后跑的测试
    # （曾导致 test_prompt_split 的 temperature 断言 inspect 到本文件里的 lambda 源码）
    orig_tool_llm, orig_chat_llm = chat._get_tool_llm, chat._get_chat_llm
    orig_retrieve = rag.retrieve
    try:
        # mock LLM（_get_tool_llm 现已按本轮问题裁剪工具清单，签名带 question）
        fake = _FakeLLM()
        chat._get_tool_llm = lambda question="": fake
        chat._get_chat_llm = lambda: fake
        # mock 知识库检索。tools._search_knowledge_base 内部是
        # `from app.services.rag import retrieve`（调用时实时取），patch 这里即可生效
        rag.retrieve = lambda q, tenant_id, top_k=5, tool_token="": [
            {"file_name": "准入制度.pdf", "heading_path": ["准入", "资质"],
             "content": "需提供营业执照、ISO 认证", "document_id": 7, "chunk_index": 3}
        ]

        events = []

        async def run():
            async for ev in chat.stream_chat_agent("准入要求是什么", [], [], "tok", 1):
                events.append(ev)

        asyncio.run(run())

        kinds = [e["type"] for e in events]
        assert "tool_call" in kinds, f"应发起工具调用: {kinds}"
        assert "citations" in kinds, f"知识库命中应推 citations: {kinds}"
        assert any(e["type"] == "chunk" for e in events), "应有最终回答"
        # citations 内容正确
        cit = next(e for e in events if e["type"] == "citations")
        assert cit["sources"][0]["fileName"] == "准入制度.pdf"
        assert cit["sources"][0]["sectionPath"] == "准入 > 资质"
        # S4：引用须透出 documentId/chunkIndex，前端才能点引用跳转打开原文档
        assert cit["sources"][0]["documentId"] == 7, f"引用应带 documentId: {cit['sources'][0]}"
        assert cit["sources"][0]["chunkIndex"] == 3, f"引用应带 chunkIndex: {cit['sources'][0]}"
        # 审计字段：tool_call 带 arguments（供 ai_tool_calls 落库），tool_result 带 success/costMs
        tc = next(e for e in events if e["type"] == "tool_call")
        assert tc["arguments"] == {"query": "供应商准入要求"}, f"tool_call 应带参数: {tc}"
        tr = next(e for e in events if e["type"] == "tool_result")
        assert tr["success"] is True, f"tool_result 应带 success: {tr}"
        assert isinstance(tr["costMs"], int) and tr["costMs"] >= 0, f"tool_result 应带 costMs: {tr}"
    finally:
        chat._get_tool_llm, chat._get_chat_llm = orig_tool_llm, orig_chat_llm
        rag.retrieve = orig_retrieve


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
