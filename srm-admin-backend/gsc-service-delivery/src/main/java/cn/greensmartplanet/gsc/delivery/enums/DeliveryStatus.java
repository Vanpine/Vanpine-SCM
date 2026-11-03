package cn.greensmartplanet.gsc.delivery.enums;

import lombok.Getter;

/**
 * 交付单状态枚举
 * <p>
 * 状态机：
 * pending_shipment(待发货) -> shipped(待收货) -> received(待验收) -> accepted(已验收)
 * received 部分不合格 -> return_pending(待供应商确认) -> return_confirmed(待退回货物) -> return_shipping(退货中) -> returned(已退回)
 * received 部分残次 -> 原批 accepted + 补发批 reship_pending(待补单) -> shipped
 *
 * @author gsc
 */
@Getter
public enum DeliveryStatus {

    PENDING_SHIPMENT("pending_shipment", "待发货"),
    SHIPPED("shipped", "待收货"),
    RECEIVED("received", "待验收"),
    ACCEPTED("accepted", "已验收"),
    RETURN_PENDING("return_pending", "待供应商确认退货"),
    RETURN_CONFIRMED("return_confirmed", "待退回货物"),
    RETURN_SHIPPING("return_shipping", "退货中"),
    RETURNED("returned", "已退回"),
    RESHIP_PENDING("reship_pending", "待补单");

    private final String code;
    private final String desc;

    DeliveryStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /** 按 code 取中文描述（AI 出参/展示用）；未知 code 原样返回 */
    public static String descOf(String code) {
        if (code == null) {
            return null;
        }
        for (DeliveryStatus s : values()) {
            if (s.code.equals(code)) {
                return s.desc;
            }
        }
        return code;
    }
}