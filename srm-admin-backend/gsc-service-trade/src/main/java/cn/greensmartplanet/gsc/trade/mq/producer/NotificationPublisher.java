package cn.greensmartplanet.gsc.trade.mq.producer;

import cn.greensmartplanet.gsc.common.event.NotificationPayload;
import cn.greensmartplanet.gsc.trade.entity.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 站内消息发布器 — RabbitTemplate 原生发送 gsc-notification，平台域消费落库
 * <p>
 * 阶段 8 切流后业务域通知的唯一通道（notification 表只由平台域写入）。
 * <p>
 * 幂等：messageId 贴 UUID（RabbitMQ 原生属性），同一条消息重试/重投 messageId 不变，
 * 平台消费端落库到 notification.event_id 唯一索引挡重复。
 * <p>
 * 发送失败仅记日志不抛异常：通知是旁路通道，失败不能回滚主业务（与 direct 直写模式语义一致）；
 * 消息落盘由 publisher confirm（yml 配置）确认。
 *
 * @author gsc
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPublisher {

    /** 通知交换机（platform 域 RabbitMqConfig 声明同名交换机/队列） */
    public static final String EXCHANGE = "gsc-notification";
    /** 路由键：固定通知路由，消费队列按此绑定 */
    public static final String ROUTING_KEY = "notify";

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发布站内消息（平台域消费落库）
     *
     * @param notification 通知实体（receiverTenantId 必填，调用方已校验）
     */
    public void publish(Notification notification) {
        // 实体 → 载荷：字段一一映射（五域 Notification 实体结构一致）
        NotificationPayload payload = new NotificationPayload();
        payload.setReceiverTenantId(notification.getReceiverTenantId());
        payload.setType(notification.getType());
        payload.setTitle(notification.getTitle());
        payload.setContent(notification.getContent());
        payload.setRelatedType(notification.getRelatedType());
        payload.setRelatedId(notification.getRelatedId());
        payload.setFromTenantId(notification.getFromTenantId());
        payload.setFromUserId(notification.getFromUserId());
        payload.setIsRead(notification.getIsRead() != null ? notification.getIsRead() : 0);
        payload.setReadTime(notification.getReadTime());
        payload.setCreateTime(notification.getCreateTime());

        // messageId 贴 UUID：消费端落库幂等键（event_id 唯一索引）
        String messageId = UUID.randomUUID().toString();
        MessagePostProcessor postProcessor = msg -> {
            msg.getMessageProperties().setMessageId(messageId);
            // 持久化消息：broker 重启不丢
            msg.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            return msg;
        };
        try {
            rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, payload, postProcessor);
            log.warn("发送消息: 通知, messageId={}, receiverTenantId={}", messageId, notification.getReceiverTenantId());
        } catch (Exception e) {
            // 通知失败不影响主业务，仅记录日志（与旁路"失败仅 log 不回滚"一致）
            log.error("站内消息发送失败: type={}, receiverTenantId={}",
                    notification.getType(), notification.getReceiverTenantId(), e);
        }
    }
}
