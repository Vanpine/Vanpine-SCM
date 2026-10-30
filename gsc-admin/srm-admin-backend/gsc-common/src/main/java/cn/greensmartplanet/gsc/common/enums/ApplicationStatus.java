package cn.greensmartplanet.gsc.common.enums;

import lombok.Getter;

/**
 * 入驻申请状态枚举 — 一份入驻申请从提交到出结果的过程
 *
 * @author gsc
 */
@Getter
public enum ApplicationStatus {

    PENDING_REVIEW("pending_review", "待审核"),  // 刚提交，等超管审核 pending 待处理 review 审查
    APPROVED("approved", "已通过"),              // 审核通过，会自动创建租户账号
    REJECTED("rejected", "已驳回");              // 审核不通过，会邮件通知原因

    private final String code;
    private final String desc;

    ApplicationStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
