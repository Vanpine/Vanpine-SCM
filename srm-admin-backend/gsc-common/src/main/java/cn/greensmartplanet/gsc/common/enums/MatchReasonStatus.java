package cn.greensmartplanet.gsc.common.enums;

import lombok.Getter;

/**
 * 智能匹配理由状态 — 详情页名单先出、理由后补。
 */
@Getter
public enum MatchReasonStatus {

    /** 名单已出，DeepSeek 还在写理由 */
    GENERATING("generating", "生成中"),
    /** Redis 已有 AI 理由 */
    READY("ready", "已就绪"),
    /** 模型失败，页面用模板理由收场 */
    FALLBACK("fallback", "已回退");

    private final String code;
    private final String desc;

    MatchReasonStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
