package cn.greensmartplanet.gsc.delivery.mq.consumer;

import cn.greensmartplanet.gsc.api.trade.ContractClient;
import cn.greensmartplanet.gsc.api.trade.dto.ContractInfoDTO;
import cn.greensmartplanet.gsc.common.event.ContractConfirmedPayload;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.delivery.service.DeliveryLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 合同确认消费者 — 监听 gsc-contract-confirmed.delivery 队列，生成第 1 批交付单
 * <p>
 * 幂等：createFromContract 内部按 contract_id 查重（与 direct 模式 countByContract 同一幂等键），
 * MQ 重放不重复建单。载荷仅作触达依据，不信任载荷状态——重查合同校验 signed 后再建单
 * （合同归交易域，Feign 重查；降级返回 null 时抛异常，由队列重试 3 次后进 DLQ，不静默 ack）。
 * 租户越权：建单数据全部取自合同行本身（买方/供应商/数量/金额），无请求上下文注入，
 * 载荷租户字段篡改不影响落库内容。
 * <p>
 * 消费失败抛异常 → 队列重试（yml listener.retry 3 次）→ 仍失败进死信队列 .dlq；
 * 阶段 8 切流后 trade 的 DeliveryDirectWriter 直写收敛删除，本消费者成为唯一建单入口。
 *
 * @author gsc
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractConfirmedConsumer {

    private final DeliveryLifecycleService lifecycleService;
    private final ContractClient contractClient;

    /**
     * 消费一条合同确认消息：重查合同校验 signed → 建第 1 批交付单（contract_id 幂等）
     * <p>
     * 建单属数据底座性质，载荷缺失 / 合同重查失败（不存在或交易域不可用）一律抛异常，
     * 让队列按 listener.retry 重试 3 次后进 DLQ，由运维排查——不能静默 ack 导致合同漏建。
     *
     * @param payload 合同确认载荷（JSON 反序列化）
     * @param message 原始消息（messageId 仅日志用；建单幂等键是 contract_id）
     */
    @RabbitListener(queues = "gsc-contract-confirmed.delivery")
    public void onMessage(ContractConfirmedPayload payload, Message message) {
        log.warn("收到消息: queue=gsc-contract-confirmed.delivery, messageId={}", message.getMessageProperties().getMessageId());
        // 防御性校验：载荷为空视为异常消息，抛异常进重试→DLQ（无合同ID无法重查，交由运维处理）
        if (payload == null || payload.getContractId() == null) {
            log.error("合同确认消息载荷缺失，进入重试→DLQ: messageId={}", message.getMessageProperties().getMessageId());
            throw new BizException(ErrorCode.PROCUREMENT_CONTRACT_NOT_FOUND);
        }
        // 重查合同校验状态（不信任载荷），direct/mq 双通道幂等键一致：contract_id
        ContractInfoDTO contract = contractClient.getContractById(payload.getContractId()).getData();
        if (contract == null) {
            // 合同不存在或交易域不可用（Feign 降级返回 null）：抛异常重试，3 次后进 DLQ，不静默 ack 丢合同
            log.error("合同重查为空（不存在或交易域不可用），进入重试→DLQ: contractId={}", payload.getContractId());
            throw new BizException(ErrorCode.PROCUREMENT_CONTRACT_NOT_FOUND);
        }
        lifecycleService.createFromContract(contract);
        log.info("合同确认消息消费完成: contractId={}, messageId={}", contract.getId(), message.getMessageProperties().getMessageId());
    }
}
