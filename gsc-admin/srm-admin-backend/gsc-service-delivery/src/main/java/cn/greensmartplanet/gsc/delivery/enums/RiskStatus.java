package cn.greensmartplanet.gsc.delivery.enums;

import lombok.Getter;

/**
 * 交付风险状态枚举
 * <p>
 * 状态机：active(观察中) -> warned(已预警)/high(高风险) -> disposing(处置中)
 * -> mitigated(已缓解)/closed(已关闭)。与交付单状态机完全分离，不修改 delivery.status。
 *
 * @author gsc
 */
@Getter
public enum RiskStatus {

    ACTIVE("active", "观察中"),
    WARNED("warned", "已预警"),
    HIGH("high", "高风险"),
    DISPOSING("disposing", "处置中"),
    MITIGATED("mitigated", "已缓解"),
    CLOSED("closed", "已关闭");

    private final String code;
    private final String desc;

    RiskStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}