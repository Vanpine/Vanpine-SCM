"""
总预算计入图片 + 工具循环内预算兜底测试

两处此前的漏洞：
  (a) _enforce_total_budget 用 _chunk_text 计量，只取 text 块——
      当前轮 4 张图 ≈ 6000 token 被当成 0，总闸形同虚设；
  (b) 工具循环每轮 append 一条 ToolMessage（单条上限 6000 字符），
      入口处的预算检查管不到循环内，4 轮累积可远超总预算。

本测试用真实 langchain（.venv 已装），不依赖 stub。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from langchain_core.messages import AIMessage, ToolMessage  # noqa: E402

import app.services.context_budget as context_budget  # noqa: E402  # 预算常量已拆到此模块，patch 必须打在读取方
from app.services.chat import (  # noqa: E402
    IMAGE_TOKEN_COST,
    TOOL_RESULT_ELIDED_SUFFIX,
    TOOL_RESULT_ELIDE_THRESHOLD,
    _content_tokens,
    _enforce_agent_budget,
    _messages_tokens,
    _shrink_old_tool_results,
    build_messages,
)

IMG_BLOCK = {"type": "image_url", "image_url": {"url": "https://oss.example.com/a.jpg"}}
TEXT_BLOCK = {"type": "text", "text": "这份合同的金额是多少"}


# ==================== (a) 图片计入预算 ====================

def test_image_block_counted():
    """图片块必须计入，不能贡献 0。"""
    assert _content_tokens([IMG_BLOCK]) >= IMAGE_TOKEN_COST


def test_text_plus_image():
    """文本 + 图片：图片部分按常量计。"""
    tokens = _content_tokens([TEXT_BLOCK, IMG_BLOCK])
    assert tokens >= IMAGE_TOKEN_COST
    assert tokens > _content_tokens([TEXT_BLOCK])


def test_plain_text_unchanged():
    """纯文本行为不变（不误伤）。"""
    assert _content_tokens("查我的合同") > 0
    assert _content_tokens("") == 0


# ==================== 本轮图编号 ====================

def test_current_round_images_get_numbering_hint():
    """本轮图以原图进多模态块，没有「第 N 张」标记——必须补一句编号说明。

    几张相似的合同扫描件，模型自己数第几张很容易数错。
    """
    msgs = build_messages("第2张的金额是多少", [], [], images=["u1", "u2", "u3", "u4"])
    last = msgs[-1]
    assert last[0] == "user"
    assert isinstance(last[1], list), "带图应走多模态块"
    text = last[1][0]["text"]
    assert "第2张的金额是多少" in text
    assert "共 4 张" in text and "1~4" in text


def test_single_image_numbering_hint():
    """单图也标注（统一口径，模型不必猜「几张」）。"""
    msgs = build_messages("这是什么", [], [], images=["u1"])
    assert "共 1 张" in msgs[-1][1][0]["text"]


def test_no_hint_without_images():
    """无图时不加编号说明。"""
    msgs = build_messages("查合同", [], [])
    assert "张图" not in str(msgs[-1][1])


def test_messages_tokens_handles_tuples_and_objects():
    """元组（build_messages 产出）与 LangChain 对象（循环内 append）都要能算。"""
    msgs = [
        ("system", "你是助手"),
        ("user", [TEXT_BLOCK, IMG_BLOCK]),
        AIMessage(content="我查一下"),
        ToolMessage(content="结果" * 500, tool_call_id="c1"),
    ]
    total = _messages_tokens(msgs)
    assert total >= IMAGE_TOKEN_COST, "元组里的图片块要算"
    assert total > 1000, "ToolMessage 的长内容要算"


def test_images_push_history_out_of_budget():
    """图片计入后，同样的历史会被裁掉——证明图片确实参与了预算判断。"""
    old = context_budget.TOTAL_CONTEXT_TOKEN_BUDGET
    # 阈值 = 14000 - 6000 = 8000：无图约 6066（不裁），有图约 12066（必裁）
    context_budget.TOTAL_CONTEXT_TOKEN_BUDGET = 14000
    try:
        history = [{"role": "user", "content": "字" * 400} for _ in range(10)]  # ≈4410 token
        with_images = build_messages("查合同", history, [], images=["u1", "u2", "u3", "u4"])
        without_images = build_messages("查合同", history, [], images=None)
    finally:
        context_budget.TOTAL_CONTEXT_TOKEN_BUDGET = old

    # 无图：system + 10 条历史 + 提问，全保留
    assert len(without_images) == 1 + 10 + 1
    # 有图：再加 6000 图片 token → 超阈值，历史被裁
    assert len(with_images) < len(without_images), "图片应把历史挤出预算"


# ==================== (b) 工具循环内兜底 ====================

def _tool_msg(text: str, idx: int = 0) -> ToolMessage:
    return ToolMessage(content=text, tool_call_id=f"c{idx}")


def test_shrink_keeps_recent_round():
    """只压较早轮次，最近 1 轮保留原文。"""
    msgs = [_tool_msg("A" * 1000, 0), _tool_msg("B" * 1000, 1), _tool_msg("C" * 1000, 2)]

    saved = _shrink_old_tool_results(msgs, keep_recent=1)

    assert saved > 0
    assert len(msgs[0].content) <= TOOL_RESULT_ELIDE_THRESHOLD + len(TOOL_RESULT_ELIDED_SUFFIX)
    assert len(msgs[1].content) <= TOOL_RESULT_ELIDE_THRESHOLD + len(TOOL_RESULT_ELIDED_SUFFIX)
    assert msgs[2].content == "C" * 1000, "最近一轮不动"


def test_shrink_ignores_short_results():
    """短工具结果不压（避免无谓改写）。"""
    msgs = [_tool_msg("短", 0), _tool_msg("B" * 1000, 1)]
    _shrink_old_tool_results(msgs, keep_recent=1)
    assert msgs[0].content == "短"


def test_enforce_agent_budget_noop_when_within():
    """没超预算时不改任何内容。"""
    msgs = [_tool_msg("A" * 1000, 0), _tool_msg("B" * 1000, 1)]
    before = [m.content for m in msgs]

    _enforce_agent_budget(msgs)

    assert [m.content for m in msgs] == before


def test_enforce_agent_budget_shrinks_when_over():
    """超预算时压早期工具结果。"""
    old = context_budget.TOTAL_CONTEXT_TOKEN_BUDGET
    context_budget.TOTAL_CONTEXT_TOKEN_BUDGET = 7000      # 阈值 = 1000
    try:
        msgs = [_tool_msg("字" * 2000, i) for i in range(4)]
        _enforce_agent_budget(msgs)
    finally:
        context_budget.TOTAL_CONTEXT_TOKEN_BUDGET = old

    assert msgs[0].content.endswith(TOOL_RESULT_ELIDED_SUFFIX)
    assert msgs[-1].content == "字" * 2000, "最近一轮保留"


def test_shrink_preserves_tool_call_pairing():
    """只改 content，不删消息——AIMessage(tool_calls) 必须紧跟其 ToolMessage。"""
    msgs = [
        AIMessage(content="", tool_calls=[{"name": "t", "args": {}, "id": "c0"}]),
        _tool_msg("A" * 1000, 0),
        AIMessage(content="", tool_calls=[{"name": "t", "args": {}, "id": "c1"}]),
        _tool_msg("B" * 1000, 1),
    ]
    _shrink_old_tool_results(msgs, keep_recent=1)
    assert len(msgs) == 4, "消息条数不变"


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
