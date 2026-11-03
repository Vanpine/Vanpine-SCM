"""
端到端链路回归评测（P0-2R：知识库工具化后）

验证「意图路由 → 工具化知识库」的分流走向：
  (a) 业务查询类      → business（工具循环，模型选业务工具）
  (b) 知识库问答类    → business（工具循环，模型选 search_knowledge_base）
  (c) 元问题类        → 直答，不进工具循环
  (d) 追问指代类      → 改写后进工具循环
  (e) 天气类          → business（chat 层强制 get_weather）
"""
import sys
import types
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

for _name, _attrs in {
    "fastapi": ("APIRouter", "HTTPException", "Request"),
    "fastapi.responses": ("StreamingResponse",),
    "langchain_openai": ("ChatOpenAI",),
    "langchain_core.messages": ("AIMessage", "ToolMessage", "HumanMessage"),
    "langchain_core.prompts": ("ChatPromptTemplate",),
}.items():
    if _name not in sys.modules:
        m = types.ModuleType(_name)
        for a in _attrs:
            setattr(m, a, object)
        sys.modules[_name] = m
sys.modules["fastapi"].responses = sys.modules["fastapi.responses"]

from app.services.intent import classify  # noqa: E402
from app.services.query_rewriter import rewrite  # noqa: E402
from app.services.tools import TOOL_SCHEMAS  # noqa: E402


def _route(question, history=None, entities=None):
    internal_q = rewrite(question, history or [], entities)
    intent = classify(internal_q or question, False)
    return intent.intent, internal_q


# ==================== (a) 业务查询类 ====================

def test_business_routes_to_agent():
    for q in ["查我的合同列表", "我的询盘有哪些", "有几家供应商报价"]:
        route, _ = _route(q)
        assert route == "business", f"{q} → business"


# ==================== (b) 知识库问答类 ====================

def test_knowledge_routes_to_agent_with_kb_tool():
    """知识问题也走 business，且工具清单里有 search_knowledge_base。"""
    for q in ["知识库里准入要求是什么", "交货期的规定是什么", "质量要求有哪些"]:
        route, _ = _route(q)
        assert route == "business", f"{q} → business"
    names = [t["function"]["name"] for t in TOOL_SCHEMAS]
    assert "search_knowledge_base" in names, "工具清单必须含知识库检索工具"


def test_kb_tool_description_not_misleading():
    """回归：KB 工具描述不得把「某供应商」劝退（曾致模型不查某公司的上传文档）。

    描述应引导模型：查某公司的上传文档/合同/资质 也用本工具；且不得出现
    「某供应商…不要用本工具」这类误导表述。
    """
    desc = next(t["function"]["description"] for t in TOOL_SCHEMAS
                if t["function"]["name"] == "search_knowledge_base")
    assert "文档" in desc, f"描述应点明查文档内容: {desc}"
    assert "上传" in desc, f"描述应强调上传文件: {desc}"
    assert "某供应商" not in desc, f"描述不应劝退供应商相关查询: {desc}"


# ==================== (c) 元问题类 ====================

def test_meta_no_tool_loop():
    for q, expect in [("我是谁", "identity_self"), ("你能做什么", "capability"), ("你好", "smalltalk")]:
        route, _ = _route(q)
        assert route == expect, f"{q} → {expect}"


# ==================== (d) 追问指代类 ====================

def test_followup_rewrite():
    hist = [{"role": "assistant", "content": "华南钢铁供应商详情：准入已通过。"}]
    route, internal = _route("他呢", history=hist)
    assert "供应商" in internal
    assert route == "business"


def test_followup_with_entities():
    entities = '[{"objectType":"inquiry","objectId":"9","title":"钢材询盘2026"}]'
    _, internal = _route("它状态怎么样", entities=entities)
    assert "钢材询盘2026" in internal


# ==================== (e) 天气类 ====================

def test_weather_routes_to_business():
    route, _ = _route("北京天气")
    assert route == "business"


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
