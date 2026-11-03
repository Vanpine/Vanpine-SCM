package cn.greensmartplanet.gsc.common.enums;

import lombok.Getter;

/**
 * 物料关键等级枚举（合同字段，交付域风险评估用）
 *
 * @author gsc
 */
@Getter
public enum MaterialCriticalLevel {

    HIGH("high", "高"),
    MEDIUM("medium", "中"),
    LOW("low", "低");

    private final String code;
    private final String desc;

    MaterialCriticalLevel(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}