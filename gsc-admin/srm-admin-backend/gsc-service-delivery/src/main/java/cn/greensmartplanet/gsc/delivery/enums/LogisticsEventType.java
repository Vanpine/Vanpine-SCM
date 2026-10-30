package cn.greensmartplanet.gsc.delivery.enums;

import lombok.Getter;

/**
 * 物流事件类型枚举（标准化节点）
 * <p>
 * 外部 TMS 状态名称各不相同，平台内部统一成此事件类型。
 * planned_ship(计划发运) -> actual_ship(实际发运) -> in_transit(运输中)
 * -> arrived_transit(到达中转站) -> customs_start(清关开始) -> customs_hold(清关/查验滞留)
 * -> customs_clear(清关完成) -> arrived_delivered(预计到达/实际签收)，另有 delay(运输延迟)/exception(其他异常)。
 *
 * @author gsc
 */
@Getter
public enum LogisticsEventType {

    PLANNED_SHIP("planned_ship", "计划发运"),
    ACTUAL_SHIP("actual_ship", "实际发运"),
    IN_TRANSIT("in_transit", "运输中"),
    ARRIVED_TRANSIT("arrived_transit", "到达中转站"),
    CUSTOMS_START("customs_start", "清关开始"),
    CUSTOMS_HOLD("customs_hold", "清关/查验滞留"),
    CUSTOMS_CLEAR("customs_clear", "清关完成"),
    DELAY("delay", "运输延迟"),
    ARRIVED_DELIVERED("arrived_delivered", "到达/签收"),
    EXCEPTION("exception", "其他异常");

    private final String code;
    private final String desc;

    LogisticsEventType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /** 按 code 取枚举（录入事件校验用），未知 code 返回 null */
    public static LogisticsEventType fromCode(String code) {
        for (LogisticsEventType t : values()) {
            if (t.code.equals(code)) {
                return t;
            }
        }
        return null;
    }
}