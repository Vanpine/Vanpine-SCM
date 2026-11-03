package cn.greensmartplanet.gsc.trade.enums;

import lombok.Getter;

/**
 * 报价状态枚举
 *
 * @author gsc
 */
@Getter
public enum QuoteStatus {

    PENDING("pending", "待处理"),
    WITHDRAWN("withdrawn", "已撤回");

    private final String code;
    private final String desc;

    QuoteStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}