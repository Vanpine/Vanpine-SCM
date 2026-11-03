package cn.greensmartplanet.gsc.common.enums;

import lombok.Getter;

/**
 * 尽调事务所状态枚举
 * <p>
 * 状态机：unassigned(未分配) -> assigned(已分配) -> in_progress(进行中) -> submitted(已提交) -> completed(已完成)
 * submitted 可被退回 -> returned(已退回) -> 重新提交 -> submitted
 *
 * @author gsc
 */
@Getter
public enum DdFirmStatus {

    UNASSIGNED("unassigned", "未分配"),
    ASSIGNED("assigned", "已分配"),
    IN_PROGRESS("in_progress", "进行中"),
    SUBMITTED("submitted", "已提交"),
    COMPLETED("completed", "已完成"),
    RETURNED("returned", "已退回");

    private final String code;
    private final String desc;

    DdFirmStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}