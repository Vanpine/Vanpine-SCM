"""
历史长回答压缩测试（阶段二：按段落切，不切出半截表格）

原实现 content[:120] 按字数硬切，会切出「| 数量 | 50」这种半截表格，
模型会把 50 当成完整数字。改为按段落累加 + 遇表格整块丢弃。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.services.chat import _history_message, _is_table_block, _trim_assistant_answer  # noqa: E402


def test_short_answer_untouched():
    """短回答原样保留。"""
    assert _trim_assistant_answer("共 3 份合同。") == "共 3 份合同。"


def test_table_dropped_after_conclusion():
    """长回答：保留表格之前的结论句，表格整块丢弃（用库里真实那条 198 字的消息）。"""
    content = (
        "上轮 3 条中第 2 条是 **pcb采集**：\n\n"
        "| 字段 | 内容 |\n|---|---|\n"
        "| 状态 | 已取消 |\n| 品类 | PCB线路板 |\n| 数量 | 500 件 |\n"
        "| 期望交付日期 | 2026-08-15 |\n| 报价截止 | 2026-08-07 00:00 |\n| 付款条件 | 款到发货 |\n\n"
        "该询盘已取消、报价截止也已过，不能延长期限；如需继续采购，需重新发布询盘。"
    )
    assert len(content) > 120, "用例本身要超限，否则走不到压缩分支"
    out = _trim_assistant_answer(content)
    assert "pcb采集" in out, f"结论句里的指代信息不能丢: {out}"
    assert "|" not in out, f"表格应整块丢弃: {out}"
    assert "500" not in out, f"不应残留半截表格行: {out}"


def test_never_cuts_mid_table_row():
    """表格行不会被切一半（原实现 content[:120] 的 bug）。"""
    content = (
        "共 15 条询盘（采购视角）。\n\n"
        "| 状态 | 标题 | 品类 | 数量 | 交付日期 | 报价截止 |\n|---|---|---|---|---|---|\n"
        + "| 已过期 | 线路板采集 | PCB线路板 | 3000吨 | 2026-09-26 | 2026-08-08 |\n" * 10
    )
    out = _trim_assistant_answer(content)
    assert "共 15 条询盘（采购视角）。" in out
    assert "|" not in out, f"表格应整块丢弃，不能切一半: {out}"


def test_table_first_falls_back_to_placeholder():
    """回答一上来就是表格、没有结论句时，用占位提示。"""
    content = "| 字段 | 内容 |\n|---|---|\n| 状态 | 已取消 |\n" * 20
    out = _trim_assistant_answer(content)
    assert out == "（上一轮回复了一个表格，内容已省略）", out


def test_long_prose_accumulates_paragraphs():
    """纯文字长回答：按段落累加，不超限就多带几段。"""
    paras = [f"第{i}段" + "字" * 30 for i in range(10)]
    out = _trim_assistant_answer("\n\n".join(paras))
    assert "第0段" in out and "第1段" in out
    assert out.endswith("…（历史答案已截断）")


def test_is_table_block():
    assert _is_table_block("| a | b |\n|---|---|")
    assert not _is_table_block("这是普通段落\n第二行")


def test_history_message_uses_paragraph_trim():
    """_history_message 对 assistant 走段落压缩，user 不受影响。"""
    long_table = "结论句。\n\n| 数量 |\n|---|\n| 500 件 |\n" * 10
    packed = _history_message({"role": "assistant", "content": long_table})
    assert packed[0] == "assistant"
    assert "|" not in packed[1], f"表格应被丢弃: {packed[1]}"

    user_text = "x" * 300
    assert _history_message({"role": "user", "content": user_text})[1] == user_text


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
