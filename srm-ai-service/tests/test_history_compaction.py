"""
历史压缩测试：长回答（天气表/大表）不得整段进历史

上一轮若回答了一张天气表，Java 会把该 assistant 原文放进 history。
若不在 Python 侧压到「一句结论」，下一轮模型常把整张表复述一遍（跨业务串台）：
用户在「广州天气」后问「查看我的合同」，会先看到一遍广州天气表、再看到合同结论。

覆盖 _history_message 的压缩行为。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.services.chat import _history_message, MAX_HISTORY_MESSAGE_CHARS


WEATHER_TABLE = (
    "广州实时（10-16 17:30）：晴，气温 31.5℃，体感 33.3℃，湿度 46%，南风 2 级，无降水。\n\n"
    "日期    天气    气温    降水    风力\n"
    "10-16    阴    23.8 ~ 32.8℃    无    2 级\n"
    "10-17    小毛毛雨    24.6 ~ 33.1℃    0.1mm    3 级\n"
    "10-18    毛毛雨    24.8 ~ 32.7℃    1.7mm    2 级\n"
    "炎热为主，最高 33℃ 上下，17—18 日有零星小雨。"
)


def test_long_assistant_truncated_to_short_head():
    """长回答压到「一句结论」量级，表格主体被丢弃。"""
    packed = _history_message({"role": "assistant", "content": WEATHER_TABLE})
    assert packed is not None
    text = packed[1]
    assert len(text) <= MAX_HISTORY_MESSAGE_CHARS + 20, f"历史应被压短: {len(text)}"
    assert "10-17" not in text, "预报表主体不该进历史"
    assert "已截断" in text


def test_short_assistant_untouched():
    """短回答原样保留，不误伤。"""
    short = "华南钢铁：准入已通过，资质齐全。"
    packed = _history_message({"role": "assistant", "content": short})
    assert packed[1] == short


def test_user_message_never_truncated():
    """user 消息不截断（用户原话要完整保留）。"""
    long_user = "查" * 500
    packed = _history_message({"role": "user", "content": long_user})
    assert packed[1] == long_user


def test_threshold_is_small_enough_to_kill_tables():
    """阈值必须小到能砍掉一张典型天气表（回归：曾是 800，装得下整表形同虚设）。"""
    assert MAX_HISTORY_MESSAGE_CHARS <= 200, "阈值太大等于不截断"


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
