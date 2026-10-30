package cn.greensmartplanet.gsc.common.enums;

import lombok.Getter;

/**
 * 合同状态枚举
 * <p>
 * 状态机：
 * draft(草稿) -> pending_supplier(待供应商确认) -> pending_confirm(待确认盖章件) -> signed(已签署)
 * -> completed(已完成) / terminated(已终止)
 * <p>
 * 供应商拒绝时 pending_supplier -> draft，可多轮往复；终止从 signed 可触发。
 *
 * @author gsc
 */
@Getter
public enum ContractStatus {

    DRAFT("draft", "草稿"),
    PENDING_SUPPLIER("pending_supplier", "待供应商确认"),
    PENDING_CONFIRM("pending_confirm", "待确认盖章件"),
    SIGNED("signed", "已签署"),
    COMPLETED("completed", "已完成"),
    TERMINATED("terminated", "已终止");

    private final String code;
    private final String desc;

    ContractStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /** 按 code 取中文描述（AI 出参/展示用）；未知 code 原样返回 */
    public static String descOf(String code) {
        if (code == null) {
            return null;
        }
        for (ContractStatus s : values()) {
            if (s.code.equals(code)) {
                return s.desc;
            }
        }
        return code;
    }
}