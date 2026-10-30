package cn.greensmartplanet.gsc.trade.service.impl;

import cn.greensmartplanet.gsc.api.delivery.DeliveryClient;
import cn.greensmartplanet.gsc.api.trade.dto.ContractCompleteDTO;
import cn.greensmartplanet.gsc.common.enums.ContractStatus;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.trade.entity.Contract;
import cn.greensmartplanet.gsc.trade.entity.Notification;
import cn.greensmartplanet.gsc.trade.mapper.ContractMapper;
import cn.greensmartplanet.gsc.trade.service.ContractCompletionService;
import cn.greensmartplanet.gsc.trade.service.NotificationService;
import cn.greensmartplanet.gsc.trade.support.ContractAccessValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 合同完成服务实现 — 完成 / 终止 / 交付域回写完成
 * <p>
 * 终态保护：signed 才可完成/终止；存在进行中交付单时禁止手动完成/终止
 * （⚠️ Feign 调交付域校验，降级返回 false 放行 + 告警，正常路径由末批验收自动置 completed）。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractCompletionServiceImpl implements ContractCompletionService {

    private final ContractMapper contractMapper;
    // 交付域 Feign：完成/终止保护的"存在进行中交付单"校验（替代直查 delivery 表）
    private final DeliveryClient deliveryClient;
    private final ContractAccessValidator contractAccessValidator;
    private final NotificationService notificationService;

    /**
     * 完成合同（signed→completed），通知供应商
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long id, Long tenantId, Long userId) {
        // 1. 校验采购方身份 + signed 状态 + 无未验收交付单（红线 2/3）
        Contract contract = contractAccessValidator.getContract(id, tenantId);
        if (!ContractStatus.SIGNED.getCode().equals(contract.getStatus())) {
            throw new BizException(ErrorCode.PROCUREMENT_CONTRACT_CANNOT_COMPLETE);
        }
        // 完成保护：货未发完或未验收完时禁止手动完成，正常路径由末批验收自动置 completed
        if (Boolean.TRUE.equals(deliveryClient.hasActive(id).getData())) {
            throw new BizException(ErrorCode.PROCUREMENT_CONTRACT_HAS_PENDING_DELIVERY);
        }
        contractMapper.updateStatus(id, tenantId, ContractStatus.COMPLETED.getCode(), null, null, userId);
        log.info("合同已完成: id={}", id);

        // 2. 站内消息：通知供应商合同已完成
        notificationService.notify(Notification.builder()
                .type("CONTRACT_COMPLETE")
                .title("合同已完成")
                .content("合同《" + contract.getTitle() + "》已执行完成")
                .receiverTenantId(contract.getSupplierTenantId())
                .relatedType("contract")
                .relatedId(contract.getId())
                .fromTenantId(tenantId)
                .fromUserId(userId)
                .build());
    }

    /**
     * 终止合同：存在进行中交付单禁止终止，原因必填
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void terminate(Long id, Long tenantId, Long userId, String reason, String terminateType) {
        // 1. 校验终止原因必填（红线 2/3）
        if (reason == null || reason.trim().isEmpty()) {
            throw new BizException(ErrorCode.PROCUREMENT_TERMINATE_REASON_REQUIRED);
        }
        // 2. 校验采购方身份 + signed 状态 + 无进行中交付单
        Contract contract = contractAccessValidator.getContract(id, tenantId);
        if (!ContractStatus.SIGNED.getCode().equals(contract.getStatus())) {
            throw new BizException(ErrorCode.PROCUREMENT_CONTRACT_CANNOT_TERMINATE);
        }
        // 终止保护：存在进行中交付单则禁止终止（⚠️ Feign 调交付域校验，降级返回 false 放行 + 告警）
        if (Boolean.TRUE.equals(deliveryClient.hasActive(id).getData())) {
            throw new BizException(ErrorCode.DELIVERY_CONTRACT_HAS_ACTIVE);
        }
        contractMapper.updateStatus(id, tenantId, ContractStatus.TERMINATED.getCode(), reason.trim(), terminateType, userId);
        log.info("合同已终止: id={}, type={}, reason={}", id, terminateType, reason);

        // 3. 站内消息：通知供应商合同已终止
        notificationService.notify(Notification.builder()
                .type("CONTRACT_TERMINATE")
                .title("合同已终止")
                .content("合同《" + contract.getTitle() + "》已终止：" + reason.trim())
                .receiverTenantId(contract.getSupplierTenantId())
                .relatedType("contract")
                .relatedId(contract.getId())
                .fromTenantId(tenantId)
                .fromUserId(userId)
                .build());
    }

    /**
     * 交付域回写：合同全部交付验收完成 → signed/completed → completed（幂等：已 completed 返回 true）
     */
    @Override
    public boolean updateCompleted(Long id, ContractCompleteDTO context) {
        // 交付域回写契约：buyerTenantId 必传（身份来自交付域上下文，非登录态）
        Long buyerTenantId = context != null ? context.getBuyerTenantId() : null;
        if (buyerTenantId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "缺少 buyerTenantId");
        }
        return contractMapper.updateCompleted(id, buyerTenantId,
                context.getUserId() != null ? context.getUserId() : null) > 0;
    }
}
