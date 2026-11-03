from app.services.chat import parse_weather_query


def test_parse_beijing():
    assert parse_weather_query("北京天气") == {"city": "北京", "days": 3}


def test_parse_guangzhou_punct():
    assert parse_weather_query("广州天气？") == {"city": "广州", "days": 3}


def test_parse_days():
    assert parse_weather_query("杭州未来7天天气") == {"city": "杭州", "days": 7}


def test_parse_complaint_not_city():
    assert parse_weather_query("我tm叫你啥天气啊") is None


def test_parse_deixis_not_city():
    """指代/方位残留不是地名：「广州那边」「刚提到…现在」不该被当成城市强查。"""
    assert parse_weather_query("广州那边天气怎么样") is None
    assert parse_weather_query("刚提到的交付单现在天气怎么样") is None


def test_parse_business_object_weather_defers():
    """问的是某个业务对象所在地的天气：不强行抽地点，交给模型先查单据。

    返回 None 不等于"不问天气"——is_weather_question 仍为真，只是不短路主对话。
    """
    assert parse_weather_query("这批货到杭州的天气") is None
    assert parse_weather_query("合同约定的交付地现在天气如何") is None


def test_parse_still_works_with_plain_city():
    """业务词排除不能误伤正常城市问句。"""
    assert parse_weather_query("上海天气") == {"city": "上海", "days": 3}
    assert parse_weather_query("查一下深圳未来3天天气") == {"city": "深圳", "days": 3}


if __name__ == "__main__":
    import sys
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
