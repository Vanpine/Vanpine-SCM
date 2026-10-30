"""
跨语言契约测试（Java → Python）

背景：Java 与 Python 之间是 HTTP + JSON，**没有编译期检查**。
Pydantic 默认忽略未声明字段且不报错，`dict.get()` 取不到 key 也不报错——
Java 加了字段忘了在 Python 声明，功能会 100% 静默失效。

2026-10-20 实际踩过：Java 下发的 imageDesc 因 ChatMessage 未声明该字段被整条丢弃，
图片描述功能从未生效，日志全绿。

本测试锁住三件事：
  (a) Java 实际下发的完整 payload，每个字段都能被 Pydantic 接住
  (b) 请求体的字段集是显式列举的——任一侧增删字段都会红
  (c) 嵌套结构（history / imageDescriptions）逐层可达
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.models.schemas import ChatMessage, ChatRequest, CompactRequest  # noqa: E402

# ==================== Java 实际下发的 payload ====================
# 逐字段对照 AiChatServiceImpl.buildChatRequestBody：
#   question / tenant_id / history / images? / context_entities? / history_summary? / tool_token
# history 项对照 buildHistory：role / content / imageDescriptions?
JAVA_CHAT_PAYLOAD = {
    "question": "这份合同的金额是多少",
    "tenant_id": 123456,
    "history": [
        {
            "role": "user",
            "content": "看看这两张",
            "imageDescriptions": [
                {"index": 1, "imageId": "9001", "description": "采购合同扫描件，编号 HT001"},
                {"index": 2, "imageId": "9002", "description": "发票，金额 320 万元"},
            ],
        },
        {"role": "assistant", "content": "合同金额 320 万元。"},
    ],
    "images": [{"url": "https://oss.example.com/a.jpg"}],
    "context_entities": '[{"objectType":"contract","objectId":"1","title":"钢材合同"}]',
    "history_summary": "用户在看钢材采购合同。",
    "tool_token": "eyJhbGciOiJIUzI1NiJ9.fake.token",
}

# 逐字段对照 AiContextCompactService.callCompact：old_summary / messages
JAVA_COMPACT_PAYLOAD = {
    "old_summary": "用户为采购方。",
    "messages": [
        {"role": "user", "content": "看看这两张",
         "imageDescriptions": [{"index": 1, "imageId": "9001", "description": "合同扫描件"}]},
        {"role": "assistant", "content": "已查到。"},
    ],
}


# ==================== (a) 字段不丢 ====================

def test_chat_request_accepts_every_java_field():
    """Java 下发的每个顶层字段，Python 都要接住（丢一个就静默失效）。"""
    dumped = ChatRequest(**JAVA_CHAT_PAYLOAD).model_dump()
    missing = set(JAVA_CHAT_PAYLOAD) - set(dumped)
    assert not missing, f"这些顶层字段被 Pydantic 丢弃了: {missing}"


def test_chat_request_history_item_keeps_image_descriptions():
    """history 项的 imageDescriptions 必须逐层可达（2026-10-20 就是这里丢的）。"""
    req = ChatRequest(**JAVA_CHAT_PAYLOAD)
    descs = req.history[0].imageDescriptions
    assert len(descs) == 2, f"应接住 2 条描述: {descs}"
    assert descs[0].index == 1
    assert descs[0].imageId == "9001"
    assert "HT001" in descs[0].description
    assert descs[1].index == 2


def test_chat_request_optional_fields_present():
    """可选字段确实被解析，不是默认值凑数。"""
    req = ChatRequest(**JAVA_CHAT_PAYLOAD)
    assert req.images[0].url == "https://oss.example.com/a.jpg"
    assert "钢材合同" in req.context_entities
    assert req.history_summary == "用户在看钢材采购合同。"
    assert req.tool_token.startswith("eyJ")


def test_compact_request_accepts_every_java_field():
    """压缩请求体同样逐字段可达（图片描述也要进摘要）。"""
    req = CompactRequest(**JAVA_COMPACT_PAYLOAD)
    dumped = req.model_dump()
    missing = set(JAVA_COMPACT_PAYLOAD) - set(dumped)
    assert not missing, f"这些字段被 Pydantic 丢弃了: {missing}"
    assert req.messages[0].imageDescriptions[0].description == "合同扫描件"


def test_minimal_payload_still_parses():
    """Java 省略所有可选字段时也要能解析（imageUrls/summary/entities 为空不下发）。"""
    req = ChatRequest(question="你好", tenant_id=1, history=[], tool_token="t")
    assert req.images == []
    assert req.context_entities == ""
    assert req.history_summary == ""


# ==================== (b) 字段集显式列举 ====================

def test_chat_request_field_set_is_explicit():
    """字段集写死：Java 侧增删字段时必须同步改这里 + schemas.py。

    对应 Java 侧 AiChatSeamTest.requestBody_usesSnakeCaseKeys 的 containsKeys 断言——
    两边各自红，逼开发者想到另一边。
    """
    expected = {
        "question", "tenant_id", "history", "images",
        "tool_token", "context_entities", "history_summary",
    }
    assert set(ChatRequest.model_fields) == expected, (
        "ChatRequest 字段集变了：Java 侧 buildChatRequestBody 要同步检查")


def test_chat_message_field_set_is_explicit():
    """history 项的字段集同样写死。"""
    expected = {"role", "content", "images", "imageDescriptions"}
    assert set(ChatMessage.model_fields) == expected, (
        "ChatMessage 字段集变了：Java 侧 buildHistory 要同步检查")


def test_compact_request_field_set_is_explicit():
    """压缩请求体的字段集同样写死。"""
    expected = {"old_summary", "messages"}
    assert set(CompactRequest.model_fields) == expected, (
        "CompactRequest 字段集变了：Java 侧 callCompact 要同步检查")


# ==================== (c) 嵌套字段不丢 ====================

def test_nested_image_description_fields_explicit():
    """imageDescriptions 的每个子字段都要能取到（漏一个就少一半信息）。"""
    req = ChatRequest(**JAVA_CHAT_PAYLOAD)
    dumped = req.history[0].imageDescriptions[0].model_dump()
    assert set(dumped) == {"index", "imageId", "description"}, (
        f"ImageDescription 字段集变了: {set(dumped)}")


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
