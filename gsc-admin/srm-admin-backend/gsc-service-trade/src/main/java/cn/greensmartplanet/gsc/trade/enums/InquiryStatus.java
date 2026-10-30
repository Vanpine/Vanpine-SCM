package cn.greensmartplanet.gsc.trade.enums;

import lombok.Getter;

import java.util.List;

/**
 * 询盘状态枚举
 * <p>
 * 状态机：open -> quoting -> awarded / closed / expired / cancelled
 * open / quoting 过截止时间 -> expired；延时后按有无报价恢复 open / quoting
 * 授标规则：分配量凑满需求量 -> awarded；未满保持 quoting；采购方可主动完成授标。
 *
 * @author gsc
 */
@Getter
public enum InquiryStatus {

    OPEN("open", "待报价"),
    QUOTING("quoting", "报价中"),
    AWARDED("awarded", "已授标"),
    CLOSED("closed", "已关闭"),
    EXPIRED("expired", "已过期"),
    CANCELLED("cancelled", "已取消");

    private final String code;
    private final String desc;

    /** 活跃状态（统计查询用） */
    public static final List<String> ACTIVE_CODES = List.of(OPEN.code, QUOTING.code);

    InquiryStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /** 按 code 取中文描述（AI 出参/展示用）；未知 code 原样返回 */
    public static String descOf(String code) {
        if (code == null) {
            return null;
        }
        for (InquiryStatus s : values()) {
            if (s.code.equals(code)) {
                return s.desc;
            }
        }
        return code;
    }
}