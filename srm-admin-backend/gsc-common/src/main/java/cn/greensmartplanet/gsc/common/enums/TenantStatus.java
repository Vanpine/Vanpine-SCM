package cn.greensmartplanet.gsc.common.enums;

import lombok.Getter;

/**
 * 租户状态枚举 — 一个租户（企业）在系统中的生命周期
 * pending(待审核) → active(已开通) / rejected(已驳回)
 * active → suspended(被停用，比如欠费)
 *
 * @author gsc
 */
@Getter
public enum TenantStatus {

    PENDING("pending", "待审核"),       // 企业刚提交入驻申请，还没审核
    ACTIVE("active", "已开通"),         // 审核通过，可以正常使用系统
    SUSPENDED("suspended", "已停用"),   // 因欠费或违规被平台暂停使用
    REJECTED("rejected", "已驳回");     // 审核不通过，申请被驳回

    /** 存入数据库的值 */
    private final String code;
    /** 前端展示用的中文 */
    private final String desc;

    TenantStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
