package cn.greensmartplanet.gsc.common.enums;

import lombok.Getter;

/**
 * 是/否枚举
 * <p>
 * 替代代码中散落的 0/1 硬编码，语义更清晰。
 *
 * @author gsc
 */
@Getter
public enum YesNo {

    YES("1", "是"),
    NO("0", "否");

    private final String code;
    private final String desc;

    YesNo(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}