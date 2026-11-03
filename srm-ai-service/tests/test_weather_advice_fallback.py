from app.services.weather_advice import fallback_weather_advice


def test_fallback_has_actionable_lines():
    lines = fallback_weather_advice({"place": "杭州西湖", "evidence": "雷暴"})
    assert len(lines) == 4
    assert any("杭州西湖" in line for line in lines)
