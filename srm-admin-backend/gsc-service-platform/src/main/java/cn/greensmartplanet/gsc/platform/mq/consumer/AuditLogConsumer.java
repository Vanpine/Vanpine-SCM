package cn.greensmartplanet.gsc.platform.mq.consumer;

import cn.greensmartplanet.gsc.common.event.AuditLogPayload;
import cn.greensmartplanet.gsc.platform.entity.AuditLogEntry;
import cn.greensmartplanet.gsc.platform.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * 审计日志消费者 — 监听 gsc-audit-log.platform 队列，落库 audit_log 表
 * <p>
 * 阶段 8 切流后业务域（trade/delivery/supplier）审计日志的唯一落库方，
 * platform 自身审计继续走 DbAuditLogSink 直写。
 * 幂等：messageId 落库到 audit_log.event_id（唯一索引兜底），
 * 重复消息（重试窗口已落库）撞唯一索引抛 DuplicateKeyException 时静默忽略。
 * 消费失败抛异常 → 队列重试（yml listener.retry 3 次）→ 仍失败进死信队列 .dlq。
 *
 * @author gsc
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogConsumer {

    private final AuditLogMapper auditLogMapper;

    /**
     * 消费一条审计消息：载荷校验 → 转实体落库（messageId 幂等）
     *
     * @param payload 审计载荷（JSON 反序列化，由 RabbitMQ 转换器按类型路由）
     * @param message 原始消息（取 messageId 幂等键）
     */
    @RabbitListener(queues = "gsc-audit-log.platform")
    public void onMessage(AuditLogPayload payload, Message message) {
        log.warn("收到消息: queue=gsc-audit-log.platform, messageId={}", message.getMessageProperties().getMessageId());
        // 防御性校验：载荷为空视为异常消息，跳过不落库（无业务数据可写）
        if (payload == null) {
            log.warn("审计消息载荷缺失，跳过: messageId={}", message.getMessageProperties().getMessageId());
            return;
        }
        // 载荷 → 实体：event_id 取消息 messageId（幂等键），其余字段一一映射
        AuditLogEntry entry = AuditLogEntry.builder()
                .eventId(message.getMessageProperties().getMessageId())
                .tenantId(payload.getTenantId())
                .userId(payload.getUserId())
                .username(payload.getUsername())
                .module(payload.getModule())
                .operation(payload.getOperation())
                .description(payload.getDescription())
                .requestMethod(payload.getRequestMethod())
                .requestUrl(payload.getRequestUrl())
                .requestParams(payload.getRequestParams())
                .ip(payload.getIp())
                .status(payload.getStatus())
                .errorMsg(payload.getErrorMsg())
                .costTimeMs(payload.getCostTimeMs())
                .targetType(payload.getTargetType())
                .targetId(payload.getTargetId())
                .targetSummary(payload.getTargetSummary())
                .createTime(payload.getCreateTime())
                .build();
        try {
            auditLogMapper.insert(entry);
        } catch (DuplicateKeyException e) {
            // 重复消息（重试/重投）：已落库，幂等忽略
            log.info("审计消息重复投递，忽略: messageId={}", message.getMessageProperties().getMessageId());
        }
    }
}
