"""
知识库工具测试（P0-2R：检索工具化后）

覆盖：
  (a) search_knowledge_base 命中 → success + 结果条目（含 source/section）
  (b) 空 query → success=False
  (c) 无 tenant_id → success=False
  (d) 检索异常 → success=False（不抛）
  (e) summarize 对知识库结果给专门摘要
"""
import sys
import types
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

# 打桩缺失依赖，让 tools 可 import
for _name, _attrs in {
    "langchain_openai": ("ChatOpenAI",),
    "langchain_core.messages": ("AIMessage", "ToolMessage", "HumanMessage"),
    "langchain_core.prompts": ("ChatPromptTemplate",),
    # rag.py 间接 import qdrant_client（本机未装），打桩让 _patch_retrieve 能替换 retrieve
    "qdrant_client": ("QdrantClient",),
    "qdrant_client.models": ("Distance", "VectorParams", "PointStruct", "Filter",
                             "FieldCondition", "MatchValue"),
}.items():
    if _name not in sys.modules:
        m = types.ModuleType(_name)
        for a in _attrs:
            setattr(m, a, object)
        sys.modules[_name] = m

from app.services import tools  # noqa: E402


def _patch_retrieve(hits=None, raise_exc=False):
    import app.services.rag as rag_mod
    orig = rag_mod.retrieve

    def fake(q, tenant_id, top_k=5, tool_token=""):
        if raise_exc:
            raise RuntimeError("qdrant down")
        return hits or []
    rag_mod.retrieve = fake
    return lambda: setattr(rag_mod, "retrieve", orig)


def test_hit_returns_items():
    """(a) 命中返回 source/section/content。"""
    hits = [{"file_name": "制度.pdf", "heading_path": ["准入", "资质"], "content": "需提供营业执照"}]
    restore = _patch_retrieve(hits=hits)
    try:
        r = tools._search_knowledge_base({"query": "准入要求"}, 1, "tok")
        assert r["success"] is True
        assert r["result"][0]["source"] == "制度.pdf"
        assert r["result"][0]["section"] == "准入 > 资质"
    finally:
        restore()


def test_empty_query_fails():
    """(b) 空 query → 失败。"""
    r = tools._search_knowledge_base({"query": "  "}, 1, "tok")
    assert r["success"] is False


def test_no_tenant_fails():
    """(c) 无 tenant_id → 失败。"""
    r = tools._search_knowledge_base({"query": "x"}, None, "tok")
    assert r["success"] is False


def test_exception_returns_failure():
    """(d) 检索异常 → 失败（不抛）。"""
    restore = _patch_retrieve(raise_exc=True)
    try:
        r = tools._search_knowledge_base({"query": "x"}, 1, "tok")
        assert r["success"] is False
    finally:
        restore()


def test_empty_hits_success_but_empty():
    """(a) 无命中 → success=True 但 result 空（模型据此说明未找到）。"""
    restore = _patch_retrieve(hits=[])
    try:
        r = tools._search_knowledge_base({"query": "火星矿"}, 1, "tok")
        assert r["success"] is True and r["result"] == []
    finally:
        restore()


def test_summarize_kb():
    """(e) 知识库结果摘要。"""
    r = {"success": True, "result": [{"source": "a", "section": "", "content": "x"}]}
    assert "知识库命中" in tools.summarize(r)


def test_summarize_weather():
    """(f) 天气结果（dict）摘要 —— 回归：dict 分支曾被 list 分支 return 挡住，永不触达。"""
    r = {"success": True, "result": {
        "originalQuery": "北京", "resolvedPlace": "北京市",
        "weatherText": "晴", "temperature": 25,
    }}
    s = tools.summarize(r)
    assert "北京市" in s and "晴" in s and "25" in s, f"天气摘要应含地点/天气/温度: {s}"


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
