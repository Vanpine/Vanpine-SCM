package cn.greensmartplanet.gsc.framework.weather;

import lombok.Data;

/**
 * 地名解析结果（经纬度 + 行政区信息）
 *
 * @author gsc
 */
@Data
public class GeoLocation {

    /** 数据源返回的规范地名（如 Shanghai） */
    private String name;

    /** 物流原文（如 杭州西湖） */
    private String originalQuery;

    /** 实际用来查天气的城市（如 杭州） */
    private String resolvedPlace;

    /** GeoNames 特征码（PPLA=省会级，PPLA4=村镇） */
    private String featureCode;

    /** 人口（选最大城市，避免同名乡镇） */
    private Integer population;

    /** 国家/地区（如 中国） */
    private String country;

    /** 一级行政区（如 上海市） */
    private String admin1;

    /** 纬度 */
    private Double latitude;

    /** 经度 */
    private Double longitude;

    /** 时区（如 Asia/Shanghai） */
    private String timezone;
}