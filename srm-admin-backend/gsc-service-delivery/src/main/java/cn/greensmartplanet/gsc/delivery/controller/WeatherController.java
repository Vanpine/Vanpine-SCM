package cn.greensmartplanet.gsc.delivery.controller;

import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.framework.weather.GeoLocation;
import cn.greensmartplanet.gsc.framework.weather.WeatherClient;
import cn.greensmartplanet.gsc.framework.weather.WeatherForecast;
import cn.greensmartplanet.gsc.framework.weather.WeatherNow;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 天气查询 Controller（交付域）
 * <p>
 * 第一阶段：打通外部天气数据源 —— 地名解析 / 实时天气 / 逐日预报，
 * 供交付页面展示与联调验证使用。
 * <p>
 * 风险扫描已接入：本控制器仍只提供查询。异常阈值在
 * {@code WeatherRiskEvaluator}，落库走 {@code DeliveryRiskLifecycleService}。
 * <p>
 * 只读端点，按规范不记 {@code @AuditLog}；鉴权沿用网关 + JWT 的 /api/admin/** 默认策略。
 *
 * @author gsc
 */
@Tag(name = "交付天气")
@RestController
@RequestMapping("/api/admin/delivery/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherClient weatherClient;

    @Operation(summary = "地名解析（城市名 → 经纬度）")
    @GetMapping("/geo")
    public R<GeoLocation> geo(
            @Parameter(description = "地名，支持详细地点如 杭州西湖") @RequestParam String name) {
        GeoLocation location = weatherClient.geocode(name);
        if (location == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "未解析到该地名，请改用更规范的城市名或直接传经纬度");
        }
        return R.ok(location);
    }

    @Operation(summary = "实时天气（传城市名或经纬度，二选一）")
    @GetMapping("/now")
    public R<WeatherNow> now(
            @Parameter(description = "地点，支持详细地点如 杭州西湖") @RequestParam(required = false) String city,
            @Parameter(description = "纬度（与经度同时传）") @RequestParam(required = false) Double latitude,
            @Parameter(description = "经度（与纬度同时传）") @RequestParam(required = false) Double longitude) {
        GeoLocation location = resolve(city, latitude, longitude);
        WeatherNow weather = weatherClient.now(location.getLatitude(), location.getLongitude());
        if (weather == null) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "天气数据源暂时不可用，请稍后重试");
        }
        weather.setOriginalQuery(location.getOriginalQuery());
        weather.setResolvedPlace(location.getResolvedPlace() != null ? location.getResolvedPlace() : location.getName());
        return R.ok(weather);
    }

    @Operation(summary = "未来天气预报（逐日，1-16 天，默认 3 天）")
    @GetMapping("/forecast")
    public R<WeatherForecast> forecast(
            @Parameter(description = "地点，支持详细地点如 杭州西湖") @RequestParam(required = false) String city,
            @Parameter(description = "纬度（与经度同时传）") @RequestParam(required = false) Double latitude,
            @Parameter(description = "经度（与纬度同时传）") @RequestParam(required = false) Double longitude,
            @Parameter(description = "预报天数 1-16") @RequestParam(defaultValue = "3") int days) {
        if (days < 1 || days > 16) {
            throw new BizException(ErrorCode.BAD_REQUEST, "预报天数需在 1-16 之间");
        }
        GeoLocation location = resolve(city, latitude, longitude);
        WeatherForecast forecast = weatherClient.forecast(location.getLatitude(), location.getLongitude(), days);
        if (forecast == null) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "天气数据源暂时不可用，请稍后重试");
        }
        forecast.setOriginalQuery(location.getOriginalQuery());
        forecast.setResolvedPlace(location.getResolvedPlace() != null ? location.getResolvedPlace() : location.getName());
        return R.ok(forecast);
    }

    /**
     * 定位：优先用经纬度，其次按自由文本解析（详细地点会先抽城市）。
     */
    private GeoLocation resolve(String city, Double latitude, Double longitude) {
        if (latitude != null && longitude != null) {
            GeoLocation location = new GeoLocation();
            location.setLatitude(latitude);
            location.setLongitude(longitude);
            return location;
        }
        if (city == null || city.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请传 city，或同时传 latitude + longitude");
        }
        GeoLocation location = weatherClient.geocode(city);
        if (location == null || location.getLatitude() == null || location.getLongitude() == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "未解析到该地名: " + city);
        }
        return location;
    }
}