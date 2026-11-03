"""
工具清单按问题裁剪测试

历史里残留天气话题时，模型会在无关问题（「查看合同」）上自己再调一次 get_weather，
把上一轮的城市又查一遍并复述（实测串台）。治法：本轮没提天气，就不把 get_weather
绑给模型——它手上没这把工具，就不会顺手去敲。

覆盖 _tools_for 的裁剪规则。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.services.chat import _tools_for, _WEATHER_TOOL_NAME
from app.services.tools import TOOL_SCHEMAS


def _names(schemas):
    return [t["function"]["name"] for t in schemas]


def test_weather_tool_dropped_for_non_weather_question():
    """非天气问题：get_weather 不在清单里。"""
    names = _names(_tools_for("查看合同"))
    assert _WEATHER_TOOL_NAME not in names
    # 业务工具照旧可用
    assert "list_contracts" in names


def test_weather_tool_kept_for_weather_question():
    """天气问题：get_weather 保留。"""
    for q in ["广州天气", "明天有台风吗", "北京会下暴雨吗", "气象怎么样"]:
        assert _WEATHER_TOOL_NAME in _names(_tools_for(q)), f"应保留天气工具: {q}"


def test_dropping_weather_keeps_everything_else():
    """裁掉的只有 get_weather，其余工具一个不少。"""
    full = _names(TOOL_SCHEMAS)
    trimmed = _names(_tools_for("查看合同"))
    assert set(full) - set(trimmed) == {_WEATHER_TOOL_NAME}


def test_does_not_mutate_source_schema():
    """裁剪返回新列表，不改动 TOOL_SCHEMAS 本体。"""
    before = len(TOOL_SCHEMAS)
    _tools_for("查看合同")
    assert len(TOOL_SCHEMAS) == before


def test_weather_question_returns_full_list():
    """天气问题返回的是完整清单（同一份对象即可）。"""
    assert _names(_tools_for("广州天气")) == _names(TOOL_SCHEMAS)


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
