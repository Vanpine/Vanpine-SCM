package cn.greensmartplanet.gsc.delivery.support;

import cn.greensmartplanet.gsc.delivery.enums.RiskLevel;
import cn.greensmartplanet.gsc.framework.weather.GeoLocation;
import cn.greensmartplanet.gsc.framework.weather.WeatherClient;
import cn.greensmartplanet.gsc.framework.weather.WeatherDaily;
import cn.greensmartplanet.gsc.framework.weather.WeatherForecast;
import cn.greensmartplanet.gsc.framework.weather.WeatherNow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 天气异常规则引擎 — 只做取数 + 阈值，不写风险表。
 * <p>
 * 数据源走框架 {@link WeatherClient}（当前 Open-Meteo）。
 * 失败返回「无异常」，不把第三方故障当成天气风险。
 * 扫描侧必须先收集去重地点再调 {@link #assessPlaces}，禁止循环里逐单打外部 API。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherRiskEvaluator {

    /** 预报看未来几天：覆盖海运/汽运常见在途窗口，又不把半个月气候当异常 */
    private static final int FORECAST_DAYS = 3;
    /** 蒲福 8 级起算大风（17.2m/s），陆运/港口作业已受影响 */
    private static final int WIND_ALERT_GRADE = 8;
    /** 蒲福 10 级升级为高风险 */
    private static final int WIND_HIGH_GRADE = 10;
    /** 实时小时降水 mm，达到即告警 */
    private static final double NOW_PRECIP_MM = 8;
    /** 单日累计降水 mm */
    private static final double DAY_PRECIP_MM = 20;
    /** 单日累计降水达到此值升 high */
    private static final double DAY_PRECIP_HIGH_MM = 50;
    /** 极端低温 / 高温（冷链与普通货都危险） */
    private static final double TEMP_LOW = -15;
    private static final double TEMP_HIGH = 40;
    /**
     * WMO 强对流/强降水码：大雨、强冻雨、大雪、强阵雨、雷暴及冰雹。
     * 小雨小雪不进风险，避免扫描刷屏。
     */
    private static final Set<Integer> SEVERE_CODES = Set.of(65, 67, 75, 82, 95, 96, 99);
    private static final Set<Integer> HIGH_CODES = Set.of(95, 96, 99);

    private final WeatherClient weatherClient;

    /**
     * 批量评估地点：同一城市只打一次外部接口（客户端还有 10 分钟缓存）。
     *
     * @param places 交货地、物流节点等自由文本，空串忽略
     * @return 仅包含「判定为异常」的地点；无异常或取数失败不在 map 中
     */
    public Map<String, WeatherAnomaly> assessPlaces(Collection<String> places) {
        Map<String, WeatherAnomaly> hits = new HashMap<>();
        if (places == null || places.isEmpty()) {
            return hits;
        }
        // 1. 去重 trim，避免「上海 / 上海 」打两次
        Set<String> unique = new LinkedHashSet<>();
        for (String raw : places) {
            if (StringUtils.hasText(raw)) {
                unique.add(raw.trim());
            }
        }
        // 2. 逐个地点取数并套阈值（外部调用次数 = 去重后地点数）
        for (String place : unique) {
            WeatherAnomaly hit = assessOne(place);
            if (hit != null) {
                hits.put(place, hit);
            }
        }
        return hits;
    }

    /**
     * 从一单的两个监控点里取更严重的一条；都无异常返回 null。
     */
    public WeatherAnomaly pickWorse(WeatherAnomaly a, WeatherAnomaly b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return rank(b.getLevel()) > rank(a.getLevel()) ? b : a;
    }

    private WeatherAnomaly assessOne(String place) {
        // 1. 自由文本地名 → 坐标；解析失败不当异常
        GeoLocation geo = weatherClient.geocode(place);
        if (geo == null || geo.getLatitude() == null || geo.getLongitude() == null) {
            log.debug("天气监控跳过：地名无法解析, place={}", place);
            return null;
        }
        // 2. 实时 + 3 日预报；任一失败则只看拿到的那一半
        WeatherNow now = weatherClient.now(geo.getLatitude(), geo.getLongitude());
        WeatherForecast forecast = weatherClient.forecast(geo.getLatitude(), geo.getLongitude(), FORECAST_DAYS);
        WeatherAnomaly fromNow = fromNow(place, now);
        WeatherAnomaly fromForecast = fromForecast(place, forecast);
        return pickWorse(fromNow, fromForecast);
    }

    private WeatherAnomaly fromNow(String place, WeatherNow now) {
        if (now == null) {
            return null;
        }
        boolean severeCode = now.getWeatherCode() != null && SEVERE_CODES.contains(now.getWeatherCode());
        boolean highWind = now.getWindGrade() != null && now.getWindGrade() >= WIND_ALERT_GRADE;
        boolean heavyRain = now.getPrecipitation() != null && now.getPrecipitation() >= NOW_PRECIP_MM;
        boolean extremeTemp = (now.getTemperature() != null && now.getTemperature() <= TEMP_LOW)
                || (now.getTemperature() != null && now.getTemperature() >= TEMP_HIGH);
        if (!severeCode && !highWind && !heavyRain && !extremeTemp) {
            return null;
        }
        boolean high = (now.getWeatherCode() != null && HIGH_CODES.contains(now.getWeatherCode()))
                || (now.getWindGrade() != null && now.getWindGrade() >= WIND_HIGH_GRADE);
        String summary = place + " · 实时 "
                + nullToDash(now.getWeatherText())
                + (now.getWindGrade() != null ? " · " + now.getWindGrade() + "级风" : "");
        String evidence = "监控点=" + place
                + "; 时段=实时"
                + "; 天气=" + nullToDash(now.getWeatherText())
                + "; 气温=" + now.getTemperature()
                + "; 降水mm=" + now.getPrecipitation()
                + "; 风力=" + now.getWindGrade();
        return WeatherAnomaly.builder()
                .place(place)
                .level(high ? RiskLevel.HIGH.getCode() : RiskLevel.MEDIUM.getCode())
                .summary(summary)
                .evidence(evidence)
                .build();
    }

    private WeatherAnomaly fromForecast(String place, WeatherForecast forecast) {
        if (forecast == null || forecast.getDaily() == null || forecast.getDaily().isEmpty()) {
            return null;
        }
        WeatherAnomaly worst = null;
        for (WeatherDaily day : forecast.getDaily()) {
            if (day == null) {
                continue;
            }
            boolean severeCode = day.getWeatherCode() != null && SEVERE_CODES.contains(day.getWeatherCode());
            boolean highWind = day.getWindGradeMax() != null && day.getWindGradeMax() >= WIND_ALERT_GRADE;
            boolean heavyRain = day.getPrecipitationSum() != null && day.getPrecipitationSum() >= DAY_PRECIP_MM;
            boolean extremeTemp = (day.getTempMin() != null && day.getTempMin() <= TEMP_LOW)
                    || (day.getTempMax() != null && day.getTempMax() >= TEMP_HIGH);
            if (!severeCode && !highWind && !heavyRain && !extremeTemp) {
                continue;
            }
            boolean high = (day.getWeatherCode() != null && HIGH_CODES.contains(day.getWeatherCode()))
                    || (day.getWindGradeMax() != null && day.getWindGradeMax() >= WIND_HIGH_GRADE)
                    || (day.getPrecipitationSum() != null && day.getPrecipitationSum() >= DAY_PRECIP_HIGH_MM);
            String summary = place + " · " + nullToDash(day.getDate()) + " "
                    + nullToDash(day.getWeatherText())
                    + (day.getWindGradeMax() != null ? " · " + day.getWindGradeMax() + "级风" : "");
            String evidence = "监控点=" + place
                    + "; 时段=预报" + nullToDash(day.getDate())
                    + "; 天气=" + nullToDash(day.getWeatherText())
                    + "; 气温=" + day.getTempMin() + "~" + day.getTempMax()
                    + "; 日降水mm=" + day.getPrecipitationSum()
                    + "; 风力=" + day.getWindGradeMax();
            WeatherAnomaly hit = WeatherAnomaly.builder()
                    .place(place)
                    .level(high ? RiskLevel.HIGH.getCode() : RiskLevel.MEDIUM.getCode())
                    .summary(summary)
                    .evidence(evidence)
                    .build();
            worst = pickWorse(worst, hit);
        }
        return worst;
    }

    private static int rank(String level) {
        if (RiskLevel.HIGH.getCode().equals(level)) {
            return 3;
        }
        if (RiskLevel.MEDIUM.getCode().equals(level)) {
            return 2;
        }
        return 1;
    }

    private static String nullToDash(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }
}
