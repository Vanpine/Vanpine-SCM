package cn.greensmartplanet.gsc.trade.mq.producer;

import cn.greensmartplanet.gsc.common.event.ContractConfirmedPayload;
import cn.greensmartplanet.gsc.trade.entity.Contract;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 合同确认发布器 — RabbitTemplate 原生发送 gsc-contract-confirmed，交付域消费建第 1 批交付单
 * <p>
 * 合同签署（signed）后发布事件，交付域 ContractConfirmedConsumer 消费建单。
 * 幂等：messageId 贴 UUID（RabbitMQ 原生属性）+ 消费侧按 contract_id 查重（createFromContract 内部幂等），
 * 消息重放不重复建单。
 * <p>
 * 发送失败仅记日志：合同建单的最终一致由交付域兜底（Feign 重查合同状态），
 * 消息落盘由 publisher confirm（yml 配置）确认。
 *
 * @author gsc
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractConfirmedPublisher {

    /** 合同确认交换机（delivery 域 RabbitMqConfig 声明同名交换机/队列） */
    public static final String EXCHANGE = "gsc-contract-confirmed";
    /** 路由键：固定合同路由，消费队列按此绑定 */
    public static final String ROUTING_KEY = "contract";

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发布合同确认事件（交付域消费建交付单）
     *
     * @param contract 已签署合同实体
     */
    public void publish(Contract contract) {
        // 实体 → 载荷：契约字段一一对应（金额/数量/交付条款等）
        ContractConfirmedPayload payload = new ContractConfirmedPayload(
                contract.getId(), contract.getInquiryId(), contract.getQuantity(), contract.getAmount(),
                contract.getCurrency() != null ? contract.getCurrency() : "CNY",
                contract.getDeliveryTerms(), contract.getDeliveryPlace(), contract.getDeliveryDate(),
                contract.getBuyerTenantId(), contract.getSupplierTenantId(), contract.getCreateBy());

        // messageId 贴 UUID：消费端 event_id 幂等键（合同建单幂等由 contract_id 查重兜底）
        String messageId = UUID.randomUUID().toString();
        MessagePostProcessor postProcessor = msg -> {
            msg.getMessageProperties().setMessageId(messageId);
            // 持久化消息：broker 重启不丢
            msg.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            return msg;
        };
        try {
            rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, payload, postProcessor);
            log.warn("发送消息: 合同确认事件, contractId={}, messageId={}", contract.getId(), messageId);
        } catch (Exception e) {
            // 发送失败仅记日志：交付域 Feign 重查兜底，不阻断合同签署主事务
            log.error("合同确认事件发送失败: contractId={}", contract.getId(), e);
        }
    }
}
