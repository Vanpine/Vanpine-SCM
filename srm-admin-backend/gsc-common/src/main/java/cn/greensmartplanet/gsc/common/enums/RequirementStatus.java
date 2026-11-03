package cn.greensmartplanet.gsc.common.enums;

import lombok.Getter;

import java.util.List;

/**
 * 采购需求状态枚举
 * <p>
 * 状态机：open -> closed / expired / completed
 * open 过截止时间 -> expired；达标后 -> completed；手动关闭 -> closed
 * closed / expired 可重新发布 -> open；completed 已闭环不可回退。
 *
 * @author gsc
 */
@Getter
public enum RequirementStatus {

    OPEN("open", "进行中"),
    CLOSED("closed", "已关闭"),
    EXPIRED("expired", "已过期"),
    COMPLETED("completed", "已完成");

    private final String code;
    private final String desc;

    /** 可重新发布的状态 */
    public static final List<String> REPUBLISHABLE_CODES = List.of(CLOSED.code, EXPIRED.code);

    RequirementStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}