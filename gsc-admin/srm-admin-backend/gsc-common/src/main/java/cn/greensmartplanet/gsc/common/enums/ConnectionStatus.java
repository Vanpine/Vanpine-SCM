package cn.greensmartplanet.gsc.common.enums;

import lombok.Getter;

/**
 * 对接记录状态枚举 — 对接意向从发起到关闭的过程
 *
 * @author gsc
 */
@Getter
public enum ConnectionStatus {

    PENDING("pending", "待处理"),
    ACCEPTED("accepted", "已接受"),
    REJECTED("rejected", "已拒绝"),
    CLOSED("closed", "已关闭");

    private final String code;
    private final String desc;

    ConnectionStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
