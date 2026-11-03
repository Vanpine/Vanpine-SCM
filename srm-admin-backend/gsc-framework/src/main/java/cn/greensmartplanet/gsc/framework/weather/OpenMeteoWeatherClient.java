package cn.greensmartplanet.gsc.framework.weather;

import cn.greensmartplanet.gsc.common.constant.SystemConstants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Open-Meteo 天气客户端实现（全球覆盖、免 API key）
 * <p>
 * 用到三个外部端点：
 * <ol>
 *   <li>{@code /v1/search}（geocoding 域名）— 地名 → 经纬度：交付地是自由文本，取天气前先解析坐标</li>
 *   <li>{@code /v1/forecast} + current — 实时天气</li>
 *   <li>{@code /v1/forecast} + daily — 逐日预报（风/降水/气温极值）</li>
 * </ol>
 * <p>
 * 稳定性处理：超时走框架全局 RestClient 配置（连接 5s / 读 120s）；
 * 网络异常、超时、限流、响应异常一律只记 warn 并返回 null，绝不向上抛
 * —— 天气是"增强信息"，不能拖垮交付主链路。结果进 Redis（多实例共享），
 * 避免每台 delivery 对同一批城市重复打第三方。Redis 故障只降级为当场回源。
 *
 * @author gsc
 */
@Slf4j
@RequiredArgsConstructor
public class OpenMeteoWeatherClient implements WeatherClient {

    /** 实时天气字段（风速显式用 m/s，与国内「几级风」口径一致） */
    private static final String CURRENT_FIELDS =
            "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,"
                    + "wind_speed_10m,wind_direction_10m,wind_gusts_10m";

    /** 逐日预报字段（取当日极值/累计值，正是交付风险判定需要的口径） */
    private static final String DAILY_FIELDS =
            "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,"
                    + "precipitation_probability_max,wind_speed_10m_max,wind_gusts_10m_max";

    /** 天气码 → 中文描述（WMO 4677 常用子集，未收录的码回显原值） */
    private static final Map<Integer, String> WEATHER_TEXT = Map.ofEntries(
            Map.entry(0, "晴"),
            Map.entry(1, "晴间多云"),
            Map.entry(2, "多云"),
            Map.entry(3, "阴"),
            Map.entry(45, "雾"),
            Map.entry(48, "冻雾"),
            Map.entry(51, "小毛毛雨"),
            Map.entry(53, "毛毛雨"),
            Map.entry(55, "大毛毛雨"),
            Map.entry(56, "冻毛毛雨"),
            Map.entry(57, "强冻毛毛雨"),
            Map.entry(61, "小雨"),
            Map.entry(63, "中雨"),
            Map.entry(65, "大雨"),
            Map.entry(66, "冻雨"),
            Map.entry(67, "强冻雨"),
            Map.entry(71, "小雪"),
            Map.entry(73, "中雪"),
            Map.entry(75, "大雪"),
            Map.entry(77, "米雪"),
            Map.entry(80, "小阵雨"),
            Map.entry(81, "阵雨"),
            Map.entry(82, "强阵雨"),
            Map.entry(85, "小阵雪"),
            Map.entry(86, "大阵雪"),
            Map.entry(95, "雷暴"),
            Map.entry(96, "雷暴伴小冰雹"),
            Map.entry(99, "雷暴伴大冰雹"));

    /** 蒲福风级上限（m/s），下标即风级；超出最后一档为 12 级及以上 */
    private static final double[] WIND_GRADE_MAX =
            {0.2, 1.5, 3.3, 5.4, 7.9, 10.7, 13.8, 17.1, 20.7, 24.4, 28.4, 32.6};

    /** 八方位风向中文（下标 0 = 北，顺时针每 45° 一档） */
    private static final String[] WIND_DIRECTION_TEXT =
            {"北风", "东北风", "东风", "东南风", "南风", "西南风", "西风", "西北风"};

    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;
    private final RedisTemplate<String, Object> redisTemplate;

    /** 数值接口基地址（实时天气 + 预报） */
    @Value("${gsc.weather.base-url:https://api.open-meteo.com}")
    private String baseUrl;

    /** 地名解析接口基地址 */
    @Value("${gsc.weather.geo-base-url:https://geocoding-api.open-meteo.com}")
    private String geoBaseUrl;

    /** 同一地点结果缓存时长（分钟），默认与 SystemConstants.WEATHER_CACHE_TTL 对齐 */
    @Value("${gsc.weather.cache-minutes:10}")
    private int cacheMinutes;

    // ==================== 对外方法 ====================

    @Override
    public GeoLocation geocode(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String keyword = name.trim();
        return cached("geo:" + keyword, () -> resolvePlace(keyword), GeoLocation.class);
    }

    @Override
    public WeatherNow now(double latitude, double longitude) {
        return cached("now:" + pointKey(latitude, longitude), () -> requestNow(latitude, longitude), WeatherNow.class);
    }

    @Override
    public WeatherForecast forecast(double latitude, double longitude, int days) {
        int safeDays = Math.min(Math.max(days, 1), 16);
        return cached("fc:" + safeDays + ":" + pointKey(latitude, longitude),
                () -> requestForecast(latitude, longitude, safeDays), WeatherForecast.class);
    }

    // ==================== 外部调用 ====================

    /**
     * 自由文本 → 坐标：词表抽城市，再查 Open-Meteo；通名单独出现直接放弃。
     */
    private GeoLocation resolvePlace(String original) {
        String extracted = PlaceNameResolver.extractCity(original);
        if (extracted != null) {
            return stamp(requestGeo(extracted, false), original, extracted);
        }
        if (PlaceNameResolver.isAmbiguousAtom(original)) {
            log.warn("地名是通名，拒绝猜测: name={}", original);
            return null;
        }
        GeoLocation exact = requestGeo(original, true);
        if (exact != null) {
            return stamp(exact, original, exact.getName());
        }
        for (String fallback : PlaceNameResolver.fallbackQueries(original)) {
            GeoLocation hit = requestGeo(fallback, true);
            if (hit != null) {
                return stamp(hit, original, fallback);
            }
        }
        return null;
    }

    /** 查 Open-Meteo：取人口最多的一条；cityLevelOnly 时丢掉村镇级同名 */
    private GeoLocation requestGeo(String name, boolean cityLevelOnly) {
        try {
            String body = restClientBuilder.baseUrl(geoBaseUrl).build()
                    .get()
                    .uri(builder -> builder.path("/v1/search")
                            .queryParam("name", name)
                            .queryParam("count", 5)
                            .queryParam("language", "zh")
                            .queryParam("format", "json")
                            .build())
                    .retrieve()
                    .body(String.class);

            JsonNode results = objectMapper.readTree(body).path("results");
            GeoLocation best = null;
            int bestPop = -1;
            for (JsonNode node : results) {
                GeoLocation location = parseGeo(node);
                if (cityLevelOnly && !GeoReliability.isCityLevel(location)) {
                    continue;
                }
                int population = location.getPopulation() == null ? 0 : location.getPopulation();
                if (best == null || population > bestPop) {
                    best = location;
                    bestPop = population;
                }
            }
            if (best == null) {
                log.warn("地名解析未命中: name={}, cityLevelOnly={}", name, cityLevelOnly);
            }
            return best;
        } catch (Exception e) {
            log.warn("地名解析失败: name={}, error={}", name, e.getMessage());
            return null;
        }
    }

    private GeoLocation parseGeo(JsonNode node) {
        GeoLocation location = new GeoLocation();
        location.setName(node.path("name").asText(null));
        location.setCountry(node.path("country").asText(null));
        location.setAdmin1(node.path("admin1").asText(null));
        location.setLatitude(readDouble(node, "latitude"));
        location.setLongitude(readDouble(node, "longitude"));
        location.setTimezone(node.path("timezone").asText(null));
        location.setFeatureCode(node.path("feature_code").asText(null));
        location.setPopulation(readInt(node, "population"));
        return location;
    }

    private GeoLocation stamp(GeoLocation location, String original, String resolved) {
        if (location == null) {
            return null;
        }
        location.setOriginalQuery(original);
        location.setResolvedPlace(resolved);
        return location;
    }

    /** 实时天气 */
    private WeatherNow requestNow(double latitude, double longitude) {
        try {
            String body = restClientBuilder.baseUrl(baseUrl).build()
                    .get()
                    .uri(builder -> builder.path("/v1/forecast")
                            .queryParam("latitude", coordinate(latitude))
                            .queryParam("longitude", coordinate(longitude))
                            .queryParam("current", CURRENT_FIELDS)
                            .queryParam("wind_speed_unit", "ms")
                            .queryParam("timezone", "auto")
                            .build())
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(body);
            if (isError(root, "实时天气")) {
                return null;
            }
            JsonNode current = root.path("current");
            WeatherNow now = new WeatherNow();
            now.setLatitude(readDouble(root, "latitude"));
            now.setLongitude(readDouble(root, "longitude"));
            now.setTimezone(root.path("timezone").asText(null));
            now.setTime(current.path("time").asText(null));
            now.setTemperature(readDouble(current, "temperature_2m"));
            now.setApparentTemperature(readDouble(current, "apparent_temperature"));
            now.setHumidity(readInt(current, "relative_humidity_2m"));
            now.setPrecipitation(readDouble(current, "precipitation"));
            now.setWindSpeed(readDouble(current, "wind_speed_10m"));
            now.setWindGusts(readDouble(current, "wind_gusts_10m"));
            now.setWindDirection(readInt(current, "wind_direction_10m"));
            Integer code = readInt(current, "weather_code");
            now.setWeatherCode(code);
            now.setWeatherText(weatherText(code));
            now.setWindGrade(windGrade(now.getWindSpeed()));
            now.setWindDirectionText(windDirectionText(now.getWindDirection()));
            return now;
        } catch (Exception e) {
            log.warn("实时天气查询失败: lat={}, lon={}, error={}", latitude, longitude, e.getMessage());
            return null;
        }
    }

    /** 逐日预报 */
    private WeatherForecast requestForecast(double latitude, double longitude, int days) {
        try {
            String body = restClientBuilder.baseUrl(baseUrl).build()
                    .get()
                    .uri(builder -> builder.path("/v1/forecast")
                            .queryParam("latitude", coordinate(latitude))
                            .queryParam("longitude", coordinate(longitude))
                            .queryParam("daily", DAILY_FIELDS)
                            .queryParam("forecast_days", days)
                            .queryParam("wind_speed_unit", "ms")
                            .queryParam("timezone", "auto")
                            .build())
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(body);
            if (isError(root, "天气预报")) {
                return null;
            }
            JsonNode daily = root.path("daily");
            JsonNode dates = daily.path("time");

            WeatherForecast forecast = new WeatherForecast();
            forecast.setLatitude(readDouble(root, "latitude"));
            forecast.setLongitude(readDouble(root, "longitude"));
            forecast.setTimezone(root.path("timezone").asText(null));

            List<WeatherDaily> rows = new ArrayList<>();
            for (int i = 0; i < dates.size(); i++) {
                WeatherDaily row = new WeatherDaily();
                row.setDate(dates.path(i).asText(null));
                Integer code = nestedInt(daily, "weather_code", i);
                row.setWeatherCode(code);
                row.setWeatherText(weatherText(code));
                row.setTempMax(nestedDouble(daily, "temperature_2m_max", i));
                row.setTempMin(nestedDouble(daily, "temperature_2m_min", i));
                row.setPrecipitationSum(nestedDouble(daily, "precipitation_sum", i));
                row.setPrecipitationProbabilityMax(nestedInt(daily, "precipitation_probability_max", i));
                row.setWindSpeedMax(nestedDouble(daily, "wind_speed_10m_max", i));
                row.setWindGustsMax(nestedDouble(daily, "wind_gusts_10m_max", i));
                row.setWindGradeMax(windGrade(row.getWindSpeedMax()));
                rows.add(row);
            }
            forecast.setDaily(rows);
            return forecast;
        } catch (Exception e) {
            log.warn("天气预报查询失败: lat={}, lon={}, days={}, error={}", latitude, longitude, days, e.getMessage());
            return null;
        }
    }

    // ==================== 解析与换算 ====================

    /** 第三方错误响应统一识别（Open-Meteo 用 error=true + reason 表达参数/限流错误） */
    private boolean isError(JsonNode root, String scene) {
        if (root.path("error").asBoolean(false)) {
            log.warn("{}接口返回错误: {}", scene, root.path("reason").asText(""));
            return true;
        }
        return false;
    }

    private Double readDouble(JsonNode parent, String field) {
        JsonNode node = parent.path(field);
        return node.isNumber() ? node.asDouble() : null;
    }

    private Integer readInt(JsonNode parent, String field) {
        JsonNode node = parent.path(field);
        return node.isNumber() ? node.asInt() : null;
    }

    private Double nestedDouble(JsonNode parent, String field, int index) {
        JsonNode node = parent.path(field).path(index);
        return node.isNumber() ? node.asDouble() : null;
    }

    private Integer nestedInt(JsonNode parent, String field, int index) {
        JsonNode node = parent.path(field).path(index);
        return node.isNumber() ? node.asInt() : null;
    }

    /** WMO 天气码 → 中文描述 */
    static String weatherText(Integer code) {
        if (code == null) {
            return null;
        }
        return WEATHER_TEXT.getOrDefault(code, "未知(" + code + ")");
    }

    /** 风速 m/s → 蒲福风级（0-17，12 级以上统一为 12） */
    static Integer windGrade(Double windSpeed) {
        if (windSpeed == null) {
            return null;
        }
        for (int grade = 0; grade < WIND_GRADE_MAX.length; grade++) {
            if (windSpeed <= WIND_GRADE_MAX[grade]) {
                return grade;
            }
        }
        return WIND_GRADE_MAX.length;
    }

    /** 风向角度 → 八方位中文 */
    static String windDirectionText(Integer degree) {
        if (degree == null) {
            return null;
        }
        return WIND_DIRECTION_TEXT[(int) Math.round(degree / 45.0) % 8];
    }

    /** 坐标入参格式：固定小数位，避免科学计数法被第三方接口拒收 */
    private String coordinate(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    /** 缓存键坐标：保留 2 位小数（约 1km 精度），避免同城尾数差异重复取数 */
    private String pointKey(double latitude, double longitude) {
        return String.format(Locale.ROOT, "%.2f,%.2f", latitude, longitude);
    }

    // ==================== 缓存（Redis，多实例共享） ====================

    /**
     * 读 Redis，未命中或 Redis 故障则回源；回源失败（null）不写，下次重试。
     */
    private <T> T cached(String suffix, Supplier<T> loader, Class<T> type) {
        String key = SystemConstants.WEATHER_CACHE_PREFIX + suffix;
        try {
            T hit = coerce(redisTemplate.opsForValue().get(key), type);
            if (hit != null) {
                return hit;
            }
        } catch (Exception e) {
            log.warn("天气缓存读取失败: key={}, error={}", key, e.getMessage());
        }
        T value = loader.get();
        if (value == null) {
            return null;
        }
        try {
            int ttl = Math.max(cacheMinutes, 1);
            redisTemplate.opsForValue().set(key, value, Duration.ofMinutes(ttl));
        } catch (Exception e) {
            log.warn("天气缓存写入失败: key={}, error={}", key, e.getMessage());
        }
        return value;
    }

    /** Jackson 带类型头时是实体；偶发只有 Map，转回目标类型才算命中 */
    private <T> T coerce(Object cached, Class<T> type) {
        if (cached == null) {
            return null;
        }
        if (type.isInstance(cached)) {
            return type.cast(cached);
        }
        if (cached instanceof Map<?, ?>) {
            try {
                return objectMapper.convertValue(cached, type);
            } catch (IllegalArgumentException e) {
                log.warn("天气缓存类型无法转换: type={}", type.getSimpleName());
            }
        }
        return null;
    }
}