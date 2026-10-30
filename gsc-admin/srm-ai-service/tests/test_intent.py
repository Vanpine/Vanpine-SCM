"""
意图路由测试（P0-2R：知识库工具化后）

覆盖：
  (a) 元问题直答：identity / capability / smalltalk / personal_fact
  (b) 其余（业务数据 + 知识问答）统一 business → 走 Agent 工具循环，
      由模型自选业务工具还是 search_knowledge_base（不再前置关键词区分）
  (c) 图片 → image
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.services.intent import classify


def test_meta_intents():
    """(a) 元问题分类不变。"""
    assert classify("我是谁").intent == "identity_self"
    assert classify("你能做什么").intent == "capability"
    assert classify("你好").intent == "smalltalk"
    assert classify("我对花生过敏").intent == "personal_fact"


def test_identity_question_forms():
    """「你是谁」这类问助手身份的问句也是元问题，不该走业务工具。"""
    assert classify("你是谁").intent == "identity_assistant"
    assert classify("你是谁？").intent == "identity_assistant"
    assert classify("你叫什么名字").intent == "identity_assistant"


def test_ask_self_and_ask_assistant_are_different_intents():
    """回归：问「我自己」和问「助手」必须分属两个意图。

    曾经两者共用一个 identity 意图、只有一套答案，导致「你是谁」被答成
    「你是当前登录用户，可在页面右上角查看你的账号与角色」——答非所问。
    """
    assert classify("我是谁").intent != classify("你是谁").intent
    assert classify("我的身份").intent == "identity_self"
    assert classify("你的身份").intent == "identity_assistant"


def test_smalltalk_with_trailing_punct():
    """闲聊句末带标点（问号/句号/感叹号）仍应识别成闲聊。"""
    assert classify("在吗？").intent == "smalltalk"
    assert classify("在吗").intent == "smalltalk"
    assert classify("你好。").intent == "smalltalk"
    assert classify("谢谢！").intent == "smalltalk"
    # 不能矫枉过正：「在吗」开头的业务问句不能被吞成闲聊
    assert classify("在吗，帮我查合同").intent == "business"


def test_personal_fact_needs_first_person_and_no_biz_words():
    """个人陈述须「第一人称 + 无业务词」，否则落回 business。

    裸关键词包含会把业务提问误判成个人偏好：
      「合同里有过敏责任条款吗」→ 曾被「过敏」带偏
      「记住我刚才查的合同」→ 曾被「记住我」带偏
    """
    # 真个人陈述
    assert classify("我对花生过敏").intent == "personal_fact"
    assert classify("我偏好顺丰发货").intent == "personal_fact"
    assert classify("记住我，我不吃辣").intent == "personal_fact"
    # 业务词一票否决
    assert classify("合同里有过敏责任条款吗").intent == "business"
    assert classify("记住我刚才查的合同").intent == "business"
    assert classify("这条询盘的过敏条款怎么写的").intent == "business"
    # 无人称的「过敏」不是个人陈述
    assert classify("过敏体质怎么写进合同").intent == "business"


def test_business_and_knowledge_both_business():
    """(b) 业务数据与知识问答都归 business（交工具循环，模型自选）。"""
    for q in ["查我的合同列表", "我的询盘有哪些",
              "知识库里供应商准入要求是什么", "交货期的规定是什么",
              "质量要求有哪些", "什么是准入"]:
        assert classify(q).intent == "business", f"应归 business: {q}"


def test_image():
    """(c) 带图 → image。"""
    assert classify("这图里写了什么", has_images=True).intent == "image"


def test_empty_and_unknown():
    """兜底 business（不崩）。"""
    assert classify("").intent == "business"
    assert classify("嗯").intent == "business"


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
                failed += 1
    print(f"\n{passed} passed, {failed} failed")
    sys.exit(1 if failed else 0)
