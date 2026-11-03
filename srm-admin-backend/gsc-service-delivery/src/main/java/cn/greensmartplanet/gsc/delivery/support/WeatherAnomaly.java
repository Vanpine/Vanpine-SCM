package cn.greensmartplanet.gsc.delivery.support;

import lombok.Builder;
import lombok.Value;

/**
 * 天气异常判定结果 — 只表示「这个地点当前/未来几天有运输风险」。
 * 规则判定放 {@link WeatherRiskEvaluator}，本对象只给风险主表当证据。
 */
@Value
@Builder
public class WeatherAnomaly {

    /** 监控地点原文（交货地或最新物流节点） */
    String place;
    /** 风险等级：medium / high */
    String level;
    /** 详情页 evidence 文本 */
    String evidence;
    /** 通知用短描述，如「上海 · 雷暴 · 8级风」 */
    String summary;
}
