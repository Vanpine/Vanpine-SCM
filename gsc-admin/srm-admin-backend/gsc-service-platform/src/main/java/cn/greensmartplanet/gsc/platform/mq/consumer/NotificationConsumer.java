package cn.greensmartplanet.gsc.platform.mq.consumer;

import cn.greensmartplanet.gsc.common.event.NotificationPayload;
import cn.greensmartplanet.gsc.platform.entity.Notification;
import cn.greensmartplanet.gsc.platform.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * 站内消息消费者 — 监听 gsc-notification.platform 队列，落库 notification 表
 * <p>
 * 阶段 8 切流后业务域（trade/delivery/supplier）通知的唯一落库方。
 * 幂等：messageId 落库到 notification.event_id（唯一索引兜底），
 * 重复消息（重试窗口已落库）撞唯一索引抛 DuplicateKeyException 时静默忽略。
 * 消费失败抛异常 → 队列重试（yml listener.retry 3 次）→ 仍失败进死信队列 .dlq。
 *
 * @author gsc
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationMapper notificationMapper;

    /**
     * 消费一条通知消息：载荷校验 → 转实体落库（messageId 幂等）
     *
     * @param payload 通知载荷（JSON 反序列化，由 RabbitMQ 转换器按类型路由）
     * @param message 原始消息（取 messageId 幂等键）
     */
    @RabbitListener(queues = "gsc-notification.platform")
    public void onMessage(NotificationPayload payload, Message message) {
        log.warn("收到消息: queue=gsc-notification.platform, messageId={}", message.getMessageProperties().getMessageId());
        // 防御性校验：载荷为空视为异常消息，跳过不落库（无业务数据可写）
        if (payload == null || payload.getReceiverTenantId() == null) {
            log.warn("通知消息载荷缺失，跳过: messageId={}", message.getMessageProperties().getMessageId());
            return;
        }
        // 载荷 → 实体：event_id 取消息 messageId（幂等键），is_read 缺省补 0
        Notification notification = Notification.builder()
                .eventId(message.getMessageProperties().getMessageId())
                .receiverTenantId(payload.getReceiverTenantId())
                .type(payload.getType())
                .title(payload.getTitle())
                .content(payload.getContent())
                .relatedType(payload.getRelatedType())
                .relatedId(payload.getRelatedId())
                .fromTenantId(payload.getFromTenantId())
                .fromUserId(payload.getFromUserId())
                .isRead(payload.getIsRead() != null ? payload.getIsRead() : 0)
                .readTime(payload.getReadTime())
                .build();
        try {
            notificationMapper.insert(notification);
        } catch (DuplicateKeyException e) {
            // 重复消息（重试/重投）：已落库，幂等忽略
            log.info("通知消息重复投递，忽略: messageId={}", message.getMessageProperties().getMessageId());
        }
    }
}
