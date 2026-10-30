package cn.greensmartplanet.gsc.delivery.support;

import cn.greensmartplanet.gsc.framework.weather.GeoLocation;
import cn.greensmartplanet.gsc.framework.weather.WeatherClient;
import cn.greensmartplanet.gsc.framework.weather.WeatherDaily;
import cn.greensmartplanet.gsc.framework.weather.WeatherForecast;
import cn.greensmartplanet.gsc.framework.weather.WeatherNow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 天气阈值：雷暴/大风要命中，小雨不能刷风险，取数失败当无异常。
 */
@ExtendWith(MockitoExtension.class)
class WeatherRiskEvaluatorTest {

    @Mock
    private WeatherClient weatherClient;
    @InjectMocks
    private WeatherRiskEvaluator evaluator;

    @Test
    void thunderstorm_isHighRisk() {
        stubGeo("上海");
        WeatherNow now = new WeatherNow();
        now.setWeatherCode(95);
        now.setWeatherText("雷暴");
        now.setWindGrade(4);
        now.setTemperature(22.0);
        now.setPrecipitation(1.0);
        when(weatherClient.now(anyDouble(), anyDouble())).thenReturn(now);
        when(weatherClient.forecast(anyDouble(), anyDouble(), anyInt())).thenReturn(null);

        Map<String, WeatherAnomaly> hits = evaluator.assessPlaces(List.of("上海"));

        assertThat(hits).containsKey("上海");
        assertThat(hits.get("上海").getLevel()).isEqualTo("high");
        assertThat(hits.get("上海").getSummary()).contains("雷暴");
    }

    @Test
    void lightRain_isIgnored() {
        stubGeo("孟买");
        WeatherNow now = new WeatherNow();
        now.setWeatherCode(61);
        now.setWeatherText("小雨");
        now.setWindGrade(3);
        now.setTemperature(28.0);
        now.setPrecipitation(1.0);
        when(weatherClient.now(anyDouble(), anyDouble())).thenReturn(now);
        WeatherForecast forecast = new WeatherForecast();
        WeatherDaily day = new WeatherDaily();
        day.setDate("2026-09-29");
        day.setWeatherCode(61);
        day.setWeatherText("小雨");
        day.setPrecipitationSum(5.0);
        day.setWindGradeMax(3);
        forecast.setDaily(List.of(day));
        when(weatherClient.forecast(anyDouble(), anyDouble(), anyInt())).thenReturn(forecast);

        assertThat(evaluator.assessPlaces(List.of("孟买"))).isEmpty();
    }

    @Test
    void weatherDown_isNotAnomaly() {
        when(weatherClient.geocode(eq("无法解析地"))).thenReturn(null);

        assertThat(evaluator.assessPlaces(List.of("无法解析地"))).isEmpty();
    }

    private void stubGeo(String place) {
        GeoLocation geo = new GeoLocation();
        geo.setLatitude(31.2);
        geo.setLongitude(121.5);
        when(weatherClient.geocode(eq(place))).thenReturn(geo);
    }
}
