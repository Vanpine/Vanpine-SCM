package cn.greensmartplanet.gsc.framework.weather;

import lombok.Data;

/**
 * 单日天气预报
 * <p>
 * 取的是「当日极值」而非瞬时值：风、降水按最大值/累计值给，
 * 正好是交付风险判定需要的口径（有台风/暴雨就是当天不能走货）。
 *
 * @author gsc
 */
@Data
public class WeatherDaily {

    /** 日期（该地点本地时间，yyyy-MM-dd） */
    private String date;

    /** WMO 天气码 */
    private Integer weatherCode;

    /** 天气码中文描述 */
    private String weatherText;

    /** 最高气温 ℃ */
    private Double tempMax;

    /** 最低气温 ℃ */
    private Double tempMin;

    /** 当日累计降水 mm */
    private Double precipitationSum;

    /** 当日最大降水概率 % */
    private Integer precipitationProbabilityMax;

    /** 当日最大风速 m/s */
    private Double windSpeedMax;

    /** 当日最大风力等级（蒲福风级） */
    private Integer windGradeMax;

    /** 当日最大阵风 m/s */
    private Double windGustsMax;
}