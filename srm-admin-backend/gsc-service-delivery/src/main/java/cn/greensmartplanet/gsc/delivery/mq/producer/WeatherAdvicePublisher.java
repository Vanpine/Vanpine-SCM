package cn.greensmartplanet.gsc.delivery.mq.producer;

import cn.greensmartplanet.gsc.common.event.WeatherAdvicePayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 天气 AI 建议任务发布 — delivery 自产自销，失败不回滚风险主表。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherAdvicePublisher {

    public static final String EXCHANGE = "gsc-weather-advice";
    public static final String ROUTING_KEY = "weather.advice";

    private final RabbitTemplate rabbitTemplate;

    public void publish(WeatherAdvicePayload payload) {
        if (payload == null || payload.getRiskId() == null) {
            return;
        }
        String messageId = UUID.randomUUID().toString();
        try {
            rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, payload, msg -> {
                msg.getMessageProperties().setMessageId(messageId);
                msg.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                return msg;
            });
            log.info("天气建议任务已发送: riskId={}, messageId={}", payload.getRiskId(), messageId);
        } catch (Exception e) {
            log.warn("天气建议任务发送失败: riskId={}", payload.getRiskId(), e);
        }
    }
}
