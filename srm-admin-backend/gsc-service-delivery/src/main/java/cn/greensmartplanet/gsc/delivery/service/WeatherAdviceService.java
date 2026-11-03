package cn.greensmartplanet.gsc.delivery.service;

import cn.greensmartplanet.gsc.common.event.WeatherAdvicePayload;
import cn.greensmartplanet.gsc.delivery.support.WeatherAdviceCache;

/**
 * 天气异常 AI 建议：读 Redis；生成走 Python DeepSeek，失败写模板。
 */
public interface WeatherAdviceService {

    WeatherAdviceCache read(Long riskId);

    /** 已有 ready 则跳过；否则调模型并写入 Redis */
    void generate(WeatherAdvicePayload payload);
}
