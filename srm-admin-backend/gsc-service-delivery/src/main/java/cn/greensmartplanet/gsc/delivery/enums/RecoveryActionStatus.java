package cn.greensmartplanet.gsc.delivery.enums;

import lombok.Getter;

/**
 * 补救工单状态枚举
 *
 * @author gsc
 */
@Getter
public enum RecoveryActionStatus {

    PENDING("pending", "待执行"),
    EXECUTING("executing", "执行中"),
    COMPLETED("completed", "已完成"),
    FAILED("failed", "已失败");

    private final String code;
    private final String desc;

    RecoveryActionStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}