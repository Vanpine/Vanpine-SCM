package cn.greensmartplanet.gsc.delivery.enums;

import lombok.Getter;

/**
 * 交付风险等级枚举
 *
 * @author gsc
 */
@Getter
public enum RiskLevel {

    LOW("low", "低风险"),
    MEDIUM("medium", "中风险"),
    HIGH("high", "高风险");

    private final String code;
    private final String desc;

    RiskLevel(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}