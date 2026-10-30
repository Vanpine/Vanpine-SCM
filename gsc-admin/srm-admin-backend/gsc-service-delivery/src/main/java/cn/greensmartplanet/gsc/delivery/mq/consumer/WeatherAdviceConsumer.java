package cn.greensmartplanet.gsc.delivery.mq.consumer;

import cn.greensmartplanet.gsc.common.event.WeatherAdvicePayload;
import cn.greensmartplanet.gsc.delivery.entity.DeliveryRisk;
import cn.greensmartplanet.gsc.delivery.enums.RiskStatus;
import cn.greensmartplanet.gsc.delivery.enums.RiskType;
import cn.greensmartplanet.gsc.delivery.mapper.DeliveryRiskMapper;
import cn.greensmartplanet.gsc.delivery.service.WeatherAdviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 天气建议消费者：重查风险仍为未关闭的天气异常再调模型。
 * 建议是增强信息，模型失败写模板后 ack，不进 DLQ。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherAdviceConsumer {

    private final DeliveryRiskMapper riskMapper;
    private final WeatherAdviceService weatherAdviceService;

    @RabbitListener(queues = "gsc-weather-advice.delivery")
    public void onMessage(WeatherAdvicePayload payload) {
        if (payload == null || payload.getRiskId() == null) {
            return;
        }
        DeliveryRisk risk = riskMapper.selectById(payload.getRiskId());
        if (risk == null || !RiskType.WEATHER_DISRUPTION.getCode().equals(risk.getRiskType())) {
            log.info("天气建议跳过：风险不存在或已非天气类型, riskId={}", payload.getRiskId());
            return;
        }
        if (RiskStatus.CLOSED.getCode().equals(risk.getRiskStatus())
                || RiskStatus.MITIGATED.getCode().equals(risk.getRiskStatus())) {
            log.info("天气建议跳过：风险已关闭, riskId={}", payload.getRiskId());
            return;
        }
        weatherAdviceService.generate(payload);
    }
}
