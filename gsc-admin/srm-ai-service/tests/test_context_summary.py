"""
对话压缩与摘要注入测试（阶段三：摘要层）

覆盖：
  (a) 摘要在 build_messages 里的位置：system 之后、最近对话之前，且带标记
  (b) 无摘要时不注入
  (c) compact_history 增量压缩：旧摘要 + 新消息一起进提示词
  (d) compact_history 无消息时不调 LLM，原样返回旧摘要
"""
import asyncio
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import app.services.compaction as compaction  # noqa: E402  # compact_history 已拆到此模块，patch 必须打在读取方
from app.services.chat import build_messages, compact_history  # noqa: E402


class _FakeResp:
    def __init__(self, content):
        self.content = content


class _FakeLLM:
    """只实现 ainvoke，记录收到的 messages。"""

    def __init__(self, reply):
        self.reply = reply
        self.calls = []

    async def ainvoke(self, messages):
        self.calls.append(messages)
        return _FakeResp(self.reply)


def _with_fake_llm(reply, fn):
    """临时替换 _get_chat_llm 跑 fn，返回 (结果, fake)。"""
    fake = _FakeLLM(reply)
    original = compaction._get_chat_llm
    compaction._get_chat_llm = lambda: fake
    try:
        return fn(), fake
    finally:
        compaction._get_chat_llm = original


# ==================== 摘要注入 ====================

def test_summary_injected_between_system_and_history():
    """(a) 摘要在 system 之后、最近对话之前，且带「[早前对话摘要]」标记。"""
    msgs = build_messages(
        "第2个呢",
        [{"role": "user", "content": "我的寻盘列表"}],
        [],
        history_summary="用户关注已过期的 3 条询盘，pcb采集 已取消。",
    )
    assert msgs[0][0] == "system", f"第一条应是 system: {msgs[0][0]}"
    assert msgs[1][0] == "user"
    assert "早前对话摘要" in msgs[1][1], f"摘要应带标记: {msgs[1][1]}"
    assert "pcb采集 已取消" in msgs[1][1]
    assert msgs[2][1] == "我的寻盘列表", f"历史应在摘要之后: {msgs}"


def test_no_summary_no_injection():
    """(b) 无摘要时不多塞一条消息。"""
    msgs = build_messages("你好", [], [])
    assert all("早前对话摘要" not in str(m[1]) for m in msgs)


def test_blank_summary_no_injection():
    """(b2) 摘要为空白字符串时也不注入。"""
    msgs = build_messages("你好", [], [], history_summary="   ")
    assert all("早前对话摘要" not in str(m[1]) for m in msgs)


# ==================== 压缩 ====================

def test_compact_merges_old_summary_and_new_messages():
    """(c) 旧摘要与新消息一起进提示词，返回模型输出。"""
    out, fake = _with_fake_llm(
        "用户查了 15 条询盘，确认 pcb采集 已取消。",
        lambda: asyncio.run(compact_history("旧摘要内容", [
            {"role": "user", "content": "我的寻盘列表"},
            {"role": "assistant", "content": "共 15 条询盘。"},
        ])),
    )
    assert out == "用户查了 15 条询盘，确认 pcb采集 已取消。"
    assert fake.calls, "应调用一次 LLM"
    prompt = fake.calls[0][1][1]
    assert "旧摘要内容" in prompt, "旧摘要应进提示词"
    assert "我的寻盘列表" in prompt and "共 15 条询盘" in prompt, "新消息应进提示词"
    assert "已有摘要" in prompt and "新增对话" in prompt


def test_compact_without_messages_skips_llm():
    """(d) 没有可压内容时不调 LLM，原样返回旧摘要。"""
    out, fake = _with_fake_llm("不该被调用", lambda: asyncio.run(compact_history("旧摘要", [])))
    assert out == "旧摘要"
    assert not fake.calls, "无消息不应调 LLM"


def test_compact_ignores_blank_messages():
    """(d2) 全是空内容的消息等同没有内容。"""
    out, fake = _with_fake_llm("不该被调用", lambda: asyncio.run(
        compact_history("旧", [{"role": "assistant", "content": "   "}])))
    assert out == "旧"
    assert not fake.calls


def test_compact_truncates_overlong_summary():
    """(e) 模型超字数输出被硬切到 COMPACT_HARD_CAP（提示词约束不可靠）。"""
    from app.services.chat import COMPACT_HARD_CAP
    out, _ = _with_fake_llm(
        "字" * (COMPACT_HARD_CAP + 500),
        lambda: asyncio.run(compact_history("", [
            {"role": "user", "content": "查一下合同"},
        ])),
    )
    # 没有句读可收口时硬切到上限 + 省略号
    assert len(out) == COMPACT_HARD_CAP + 1, f"应硬切到上限: {len(out)}"


def test_compact_cuts_at_sentence_boundary():
    """(e3) 超长时在句末收口，不切出半句话。"""
    from app.services.chat import COMPACT_HARD_CAP
    sentence = "用户目标是查看已过期的询盘。"
    out, _ = _with_fake_llm(
        sentence * 100,   # 1300 字，远超上限，但每 13 字一个句号
        lambda: asyncio.run(compact_history("", [{"role": "user", "content": "查询盘"}])),
    )
    assert len(out) <= COMPACT_HARD_CAP + 1
    assert out.endswith("。…"), f"应在句末收口: {out[-20:]!r}"


def test_compact_prompt_forbids_listing_details():
    """(e4) 提示词禁止罗列查询明细——只留不可重算的记忆信息。"""
    _, fake = _with_fake_llm(
        "备忘",
        lambda: asyncio.run(compact_history("", [{"role": "user", "content": "查合同"}])),
    )
    prompt = fake.calls[0][1][1]
    assert "禁止罗列查询结果" in prompt
    assert "最多 3 个" in prompt
    assert "重新查询" in prompt


def test_compact_keeps_summary_within_limit():
    """(e2) 未超限的摘要原样返回，不误伤。"""
    out, _ = _with_fake_llm(
        "用户查了 3 份合同。",
        lambda: asyncio.run(compact_history("", [{"role": "user", "content": "查合同"}])),
    )
    assert out == "用户查了 3 份合同。"


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
