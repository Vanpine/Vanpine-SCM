package cn.greensmartplanet.gsc.delivery.enums;

import lombok.Getter;

/**
 * 交付风险类型枚举
 *
 * @author gsc
 */
@Getter
public enum RiskType {

    LATE_SHIPMENT("late_shipment", "延迟发货"),
    LOGISTICS_STAGNATION("logistics_stagnation", "物流停滞"),
    ETA_DELAY("eta_delay", "预计到货延迟"),
    CLEARANCE_HOLD("clearance_hold", "清关/查验滞留"),
    QUANTITY_SHORTAGE("quantity_shortage", "数量短缺"),
    WEATHER_DISRUPTION("weather_disruption", "天气异常"),
    OTHER("other", "其他");

    private final String code;
    private final String desc;

    RiskType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}