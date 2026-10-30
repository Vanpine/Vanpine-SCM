"""
图片描述进上下文测试

背景：图片 token 成本是同体积文字的 10 倍，历史轮次一律不带原图；
Java 侧首次把图纳入上下文时生成描述落库，历史只带描述。
本测试锁住三条：
  (a) 历史消息（_history_message）按序号注入描述
  (b) 压缩输入（compact_history）同样带上
  (c) 多图按序号标注，用户问「第 2 张」能对上
"""
import asyncio
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import app.services.compaction as compaction  # noqa: E402  # compact_history 已拆到此模块，patch 必须打在读取方
from app.services.chat import _history_message, _image_desc_text, compact_history  # noqa: E402

DESC1 = "采购合同扫描件，合同号 HT20261001，甲方宝钢股份，金额 320 万元"
DESC2 = "增值税发票，发票号 FP2026001，金额 320 万元，开票日 2026-09-20"


def _descs(*items):
    """构造 Java 下发的 imageDescriptions 结构。"""
    return [{"index": i + 1, "imageId": str(9000 + i), "description": d} for i, d in enumerate(items)]


class _FakeResp:
    def __init__(self, content):
        self.content = content


class _FakeLLM:
    def __init__(self, reply):
        self.reply = reply
        self.calls = []

    async def ainvoke(self, messages):
        self.calls.append(messages)
        return _FakeResp(self.reply)


def _with_fake_llm(reply, fn):
    fake = _FakeLLM(reply)
    original = compaction._get_chat_llm
    compaction._get_chat_llm = lambda: fake
    try:
        return fn(), fake
    finally:
        compaction._get_chat_llm = original


# ==================== (c) 渲染 ====================

def test_render_numbers_each_image():
    """多图按序号标注——「第 2 张」要能对上。"""
    text = _image_desc_text({"imageDescriptions": _descs(DESC1, DESC2)})
    assert "附图1：" in text and "附图2：" in text
    assert DESC1 in text and DESC2 in text


def test_render_uses_java_index_not_list_position():
    """序号取 Java 给的 index，不按列表下标——某张图缺描述时不会错位。"""
    text = _image_desc_text({"imageDescriptions": [
        {"index": 2, "description": "第二张的图"},
    ]})
    assert "附图2：" in text, f"应保留原始序号: {text}"
    assert "附图1" not in text


def test_render_keeps_placeholder_for_blank_description():
    """空描述（生成失败/未生成）渲染成占位，保留序号——跳过会让「第 N 张」错位。"""
    text = _image_desc_text({"imageDescriptions": [
        {"index": 1, "description": "  "},
        {"index": 2, "description": "发票，金额 320 万"},
    ]})
    assert "附图1：" in text and "附图2：" in text
    assert "未识别出内容" in text
    assert "发票" in text


def test_render_all_blank_still_numbered():
    """全空也要按序渲染，让模型知道有几张、分别是什么序号。"""
    text = _image_desc_text({"imageDescriptions": [
        {"index": 1, "description": ""},
        {"index": 2, "description": ""},
    ]})
    assert "附图1：" in text and "附图2：" in text


def test_render_empty_when_absent():
    """没有描述字段时返回空串，不影响普通消息。"""
    assert _image_desc_text({}) == ""
    assert _image_desc_text({"imageDescriptions": []}) == ""


# ==================== (a) 历史消息 ====================

def test_history_appends_image_desc():
    """带描述的历史消息把描述拼进 content，模型才能指代「刚才那张图」。"""
    packed = _history_message({
        "role": "user",
        "content": "这份合同的金额是多少",
        "imageDescriptions": _descs(DESC1),
    })
    assert packed is not None
    assert DESC1 in packed[1]
    assert "附图1" in packed[1]


def test_history_without_desc_unchanged():
    """没有描述的历史消息不受影响（绝大多数消息走这条）。"""
    packed = _history_message({"role": "user", "content": "查我的合同"})
    assert packed[1] == "查我的合同"


def test_history_desc_only_message_kept():
    """纯图无文案但有描述时，消息仍要保留（否则图片信息全丢）。"""
    packed = _history_message({"role": "user", "content": "", "imageDescriptions": _descs(DESC1)})
    assert packed is not None
    assert DESC1 in packed[1]


def test_history_ignores_original_images():
    """历史项里即使带了 images，也不再编成多模态块（图片 token 太贵，会撑爆窗口）。"""
    packed = _history_message({
        "role": "user",
        "content": "查合同",
        "images": [{"url": "https://oss.example.com/a.jpg"}],
    })
    assert packed[1] == "查合同", f"历史不应带原图: {packed[1]}"


# ==================== (b) 压缩输入 ====================

def test_compact_includes_image_desc():
    """图片描述随消息进压缩提示词，否则图的信息掉出窗口后永久丢失。"""
    _, fake = _with_fake_llm("备忘", lambda: asyncio.run(compact_history("", [
        {"role": "user", "content": "这份合同金额多少", "imageDescriptions": _descs(DESC1)},
    ])))
    prompt = fake.calls[0][1][1]
    assert DESC1 in prompt, "描述应进压缩输入"
    assert "附图1" in prompt


def test_compact_desc_only_message_not_dropped():
    """纯图消息（content 为空但有描述）不能被当成空消息跳过。"""
    _, fake = _with_fake_llm("备忘", lambda: asyncio.run(compact_history("", [
        {"role": "user", "content": "", "imageDescriptions": _descs(DESC1)},
    ])))
    assert fake.calls, "有描述就不算空消息，应调用 LLM"
    assert DESC1 in fake.calls[0][1][1]


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
