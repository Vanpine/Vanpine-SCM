package cn.greensmartplanet.gsc.common.enums;

import lombok.Getter;

/**
 * 用户状态枚举 — 一个用户账号当前能不能用
 *
 * @author gsc
 */
@Getter
public enum UserStatus {

    ACTIVE("active", "启用"),       // 正常使用中
    DISABLED("disabled", "禁用"),   // 管理员手动禁用
    LOCKED("locked", "锁定");       // 输错密码太多次，系统自动锁定

    private final String code;
    private final String desc;

    UserStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
