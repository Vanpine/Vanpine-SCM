package cn.greensmartplanet.gsc.framework.weather;

import lombok.Data;

/**
 * 实时天气快照
 * <p>
 * 单位统一：气温 ℃、风速 m/s、降水 mm、风向 0-360（正北为 0）。
 * 风力等级按蒲福风级换算，方便交付预警直接用「几级风」表达。
 *
 * @author gsc
 */
@Data
public class WeatherNow {

    /** 物流原文 */
    private String originalQuery;

    /** 实际查询城市（详细地点被收成城市后回显，如 杭州） */
    private String resolvedPlace;

    /** 纬度（回显实际取数的坐标，便于核对地名解析是否正确） */
    private Double latitude;

    /** 经度 */
    private Double longitude;

    /** 时区（如 Asia/Shanghai） */
    private String timezone;

    /** 观测时间（该地点本地时间，yyyy-MM-dd'T'HH:mm） */
    private String time;

    /** 气温 ℃ */
    private Double temperature;

    /** 体感温度 ℃ */
    private Double apparentTemperature;

    /** 相对湿度 % */
    private Integer humidity;

    /** 降水量 mm（最近一小时） */
    private Double precipitation;

    /** WMO 天气码 */
    private Integer weatherCode;

    /** 天气码中文描述（如 阴 / 中雨 / 雷暴） */
    private String weatherText;

    /** 风速 m/s */
    private Double windSpeed;

    /** 风力等级（蒲福风级 0-17） */
    private Integer windGrade;

    /** 阵风 m/s */
    private Double windGusts;

    /** 风向 0-360 */
    private Integer windDirection;

    /** 风向中文（如 东南风） */
    private String windDirectionText;
}