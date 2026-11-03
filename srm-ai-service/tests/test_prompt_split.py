"""
Prompt 拆分测试（P1-1）

覆盖：
  (a) knowledge 路由的 system prompt 含知识规则、不含业务工具规则（标识展示/手动执行）
  (b) agent 路由的 system prompt 含业务规则、不含知识问答规则
  (c) 检索资料放 user 消息，不进 system
  (d) 温度已降低（≤0.2）
  (e) 「优先基于资料」措辞已改为「仅当直接相关」
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
# reasoning.py 用 ChatPromptTemplate.from_messages(...) 构建模块级常量，需要能调用
sys.modules["langchain_core.prompts"].ChatPromptTemplate = type(
    "ChatPromptTemplate", (), {"from_messages": staticmethod(lambda *a, **k: object())}
)
import app.services.tools as _tools  # noqa: E402

from app.services.chat import (  # noqa: E402
    build_system_prompt, build_messages, KNOWLEDGE_RULE, DISPLAY_RULE, MANUAL_ACTION_RULE,
)


def test_knowledge_prompt_isolated():
    """(a) knowledge system 含知识规则，不含业务规则。"""
    p = build_system_prompt("知识库里准入要求是什么", route="knowledge")
    assert "知识问答" in p
    assert "标识展示规则" not in p, "knowledge 不应含标识展示规则"
    assert "重要操作手动执行" not in p, "knowledge 不应含手动执行规则"


def test_agent_prompt_isolated():
    """(b) agent system 含业务规则，不含知识问答规则。"""
    p = build_system_prompt("查我的合同列表", route="agent")
    assert "标识展示规则" in p
    assert "重要操作手动执行" in p
    assert "知识问答" not in p, "agent 不应含知识问答规则"


def test_chunks_in_user_message_not_system():
    """(c) 检索资料在 user 消息，不在 system。"""
    chunks = [{"file_name": "合同.pdf", "content": "违约金按日万分之五", "heading_path": ["第四条"]}]
    msgs = build_messages("违约金怎么算", history=[], chunks=chunks, route="knowledge")
    system_text = next(m[1] for m in msgs if m[0] == "system")
    user_text = next(m[1] for m in msgs if m[0] == "user")
    assert "违约金按日万分之五" not in system_text, "资料不应在 system"
    assert "违约金按日万分之五" in user_text, "资料应在 user 消息"
    assert "仅当与问题直接相关" in user_text


def test_no_prefer_based_wording():
    """(e) 旧的『优先基于这些资料』措辞已删除。"""
    chunk = [{"file_name": "f", "content": "c", "heading_path": []}]
    msgs = build_messages("q", history=[], chunks=chunk, route="knowledge")
    blob = " ".join(str(m[1]) for m in msgs)
    assert "请优先基于这些资料" not in blob
    assert KNOWLEDGE_RULE.strip()  # 新规则存在


def test_temperature_lowered():
    """(d) 对话温度 ≤ 0.2。

    _get_chat_llm 已从 chat.py 拆到 llm.py 并改为「延迟创建的单例」——
    不在 import 时构造（无 API Key 的环境也要能 import），所以不能读实例属性校验，
    改在定义它的模块源码里找字面量。
    """
    import inspect
    from app.services import llm as llm_mod
    src = inspect.getsource(llm_mod._get_chat_llm)
    assert "temperature=0.2" in src, "对话温度应降到 0.2"


# ==================== 图片问题不得调业务工具（2026-10-20） ====================
# 实测：用户问「第一张图是什么」时，模型自主调了 list_inquiries 把询盘列表又列了一遍——
# 它不知道历史里的「附图N」是什么，又觉得「第一张」有歧义，就顺手查了业务数据。
# 修法是在 system prompt 里说清「附图N」的语义 + 明令图片问题不调工具。

def test_prompt_explains_image_description_marker():
    """system 必须解释「（附图N：…）」是什么，否则模型看不懂历史里的标注。"""
    p = build_system_prompt("第一张图是什么", route="agent")
    assert "附图1" in p, "应说明附图编号的含义"


def test_prompt_forbids_tools_for_image_questions():
    """明令：图片内容不在业务库，为图片问题调业务工具查不到东西。"""
    p = build_system_prompt("第一张图是什么", route="agent")
    assert "图片内容不在业务库" in p
    assert "list_*" in p or "不要调" in p


def test_prompt_forbids_unsolicited_business_listing():
    """用户没问业务数据时，不得顺带罗列业务列表。"""
    p = build_system_prompt("这是什么", route="agent")
    assert "不要顺带查询或罗列业务列表" in p


def test_prompt_mentions_current_round_numbering():
    """本轮附图编号说明要与 build_messages 实际注入的文案一致。"""
    p = build_system_prompt("第2张的金额", route="agent")
    assert "本次共 N 张图" in p, "提示词要指向实际注入的编号说明"

    msgs = build_messages("第2张的金额", [], [], images=["u1", "u2"])
    assert "本次共 2 张图" in msgs[-1][1][0]["text"], "实际注入文案应能对上提示词描述"


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
