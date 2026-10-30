package cn.greensmartplanet.gsc.framework.weather;

import lombok.Data;

import java.util.List;

/**
 * 未来天气预报（逐日）
 *
 * @author gsc
 */
@Data
public class WeatherForecast {

    /** 物流原文 */
    private String originalQuery;

    /** 实际查询城市 */
    private String resolvedPlace;

    /** 纬度 */
    private Double latitude;

    /** 经度 */
    private Double longitude;

    /** 时区（如 Asia/Shanghai） */
    private String timezone;

    /** 逐日预报，按日期升序 */
    private List<WeatherDaily> daily;
}