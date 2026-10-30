package cn.greensmartplanet.gsc.framework.audit;

import cn.greensmartplanet.gsc.common.event.AuditLogPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.UUID;

/**
 * 审计日志 MQ 落库实现 — RabbitTemplate 原生发送 gsc-audit-log，平台域消费落库
 * <p>
 * 阶段 8 切流后业务域（trade/delivery/supplier）审计日志的唯一通道：
 * 框架切面构建的 AuditLogEntry 转为载荷发送，audit_log 表只由平台域写入。
 * <p>
 * 幂等：messageId 贴 UUID（RabbitMQ 原生属性），同一条消息重试/重投 messageId 不变，
 * 平台消费端落库到 audit_log.event_id 唯一索引挡重复。
 * <p>
 * 发送失败仅记日志不阻断业务（审计不阻塞主流程，与切面兜底 catch 语义一致）；
 * 消息落盘由 publisher confirm（yml 配置）确认。
 *
 * @author gsc
 */
@Slf4j
public class MqAuditLogSink implements AuditLogSink {

    /** 审计事件交换机（平台域声明同名交换机/队列并绑定） */
    public static final String EXCHANGE = "gsc-audit-log";
    /** 路由键：固定审计路由，消费队列按此绑定 */
    public static final String ROUTING_KEY = "audit";

    private final RabbitTemplate rabbitTemplate;

    public MqAuditLogSink(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /** 切面入口：发送一条审计事件（旁路语义，失败仅 log） */
    @Override
    public void save(AuditLogEntry entry) {
        // 框架层实体 → 跨服务载荷（字段一一对应）
        AuditLogPayload payload = new AuditLogPayload();
        payload.setTenantId(entry.getTenantId());
        payload.setUserId(entry.getUserId());
        payload.setUsername(entry.getUsername());
        payload.setModule(entry.getModule());
        payload.setOperation(entry.getOperation());
        payload.setDescription(entry.getDescription());
        payload.setRequestMethod(entry.getRequestMethod());
        payload.setRequestUrl(entry.getRequestUrl());
        payload.setRequestParams(entry.getRequestParams());
        payload.setIp(entry.getIp());
        payload.setStatus(entry.getStatus());
        payload.setErrorMsg(entry.getErrorMsg());
        payload.setCostTimeMs(entry.getCostTimeMs());
        payload.setTargetType(entry.getTargetType());
        payload.setTargetId(entry.getTargetId());
        payload.setTargetSummary(entry.getTargetSummary());
        payload.setCreateTime(entry.getCreateTime());

        // messageId 贴 UUID：消费端落库幂等键（event_id 唯一索引）
        String messageId = UUID.randomUUID().toString();
        MessagePostProcessor postProcessor = msg -> {
            msg.getMessageProperties().setMessageId(messageId);
            // 持久化消息：broker 重启不丢（旁路审计可接受少量丢失，持久化仅兜底）
            msg.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            return msg;
        };
        try {
            rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, payload, postProcessor);
            log.warn("发送消息: 审计事件, module={}, operation={}, messageId={}", entry.getModule(), entry.getOperation(), messageId);
        } catch (Exception e) {
            // 审计失败不阻断主业务，仅记录日志（框架切面亦有兜底 catch，双保险）
            log.error("审计事件发送失败: module={}, operation={}, targetId={}",
                    entry.getModule(), entry.getOperation(), entry.getTargetId(), e);
        }
    }
}
