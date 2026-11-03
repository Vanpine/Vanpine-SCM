package cn.greensmartplanet.gsc.common.enums;

import lombok.Getter;

/**
 * 通用状态枚举（启用/禁用）
 * <p>
 * 适用于各种 is_active / status 字段，表示业务实体的通用启停状态。
 *
 * @author gsc
 */
@Getter
public enum CommonStatus {

    ACTIVE("active", "启用"),
    DISABLED("disabled", "禁用");

    private final String code;
    private final String desc;

    CommonStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}