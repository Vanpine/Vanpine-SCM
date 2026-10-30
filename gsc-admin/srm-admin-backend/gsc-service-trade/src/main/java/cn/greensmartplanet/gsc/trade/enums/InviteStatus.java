package cn.greensmartplanet.gsc.trade.enums;

import lombok.Getter;

/**
 * 询盘邀请状态枚举
 * <p>
 * pending -> accepted / declined / expired
 *
 * @author gsc
 */
@Getter
public enum InviteStatus {

    PENDING("pending", "待响应"),
    ACCEPTED("accepted", "已接受"),
    DECLINED("declined", "已拒绝"),
    EXPIRED("expired", "已过期");

    private final String code;
    private final String desc;

    InviteStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}