package cn.greensmartplanet.gsc.delivery.service.impl;

import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.delivery.dto.CompleteRecoveryActionDTO;
import cn.greensmartplanet.gsc.delivery.dto.CreateRecoveryActionDTO;
import cn.greensmartplanet.gsc.delivery.entity.Delivery;
import cn.greensmartplanet.gsc.delivery.entity.DeliveryRecoveryAction;
import cn.greensmartplanet.gsc.delivery.entity.DeliveryRisk;
import cn.greensmartplanet.gsc.delivery.enums.RecoveryActionStatus;
import cn.greensmartplanet.gsc.delivery.enums.RiskStatus;
import cn.greensmartplanet.gsc.delivery.mapper.DeliveryMapper;
import cn.greensmartplanet.gsc.delivery.mapper.DeliveryRecoveryActionMapper;
import cn.greensmartplanet.gsc.delivery.mapper.DeliveryRiskMapper;
import cn.greensmartplanet.gsc.delivery.service.DeliveryRecoveryActionService;
import cn.greensmartplanet.gsc.delivery.vo.RecoveryActionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 交付补救工单服务实现
 * <p>
 * 闭环：用户确认推荐方案 → 生成工单（责任人/截止时间）→ 执行 → 完成（回执/结果）
 * → 自动缓解风险 → 关闭风险（须有回执证明，不能仅点"已处理"）。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryRecoveryActionServiceImpl implements DeliveryRecoveryActionService {

    private final DeliveryRecoveryActionMapper actionMapper;
    private final DeliveryRiskMapper riskMapper;
    private final DeliveryMapper deliveryMapper;

    /** 创建补救工单：校验风险归属 + 风险未关闭，生成 pending 工单 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RecoveryActionVO create(Long tenantId, Long userId, CreateRecoveryActionDTO dto) {
        DeliveryRisk risk = getRisk(dto.getRiskId(), tenantId);
        if (RiskStatus.CLOSED.getCode().equals(risk.getRiskStatus())) {
            throw new BizException(ErrorCode.RISK_STATUS_INVALID);
        }
        DeliveryRecoveryAction action = DeliveryRecoveryAction.builder()
                .riskId(dto.getRiskId())
                .actionType(dto.getActionType())
                .actionName(dto.getActionName())
                .recommendation(dto.getRecommendation())
                .expectedEta(dto.getExpectedEta())
                .expectedCost(dto.getExpectedCost())
                .expectedRecoveryQty(dto.getExpectedRecoveryQty())
                .status(RecoveryActionStatus.PENDING.getCode())
                .ownerUserId(dto.getOwnerUserId())
                .deadline(dto.getDeadline())
                .build();
        actionMapper.insert(action);
        // 有补救工单在处置 → 风险进入处置中
        riskMapper.updateRiskState(risk.getId(), risk.getRiskLevel(),
                RiskStatus.DISPOSING.getCode(), risk.getEstimatedArrivalTime(),
                risk.getEvidence(), risk.getDueTime());
        log.info("补救工单创建: id={}, riskId={}, type={}", action.getId(), dto.getRiskId(), dto.getActionType());
        return RecoveryActionVO.fromEntity(action);
    }

    /** 按风险查补救工单列表 */
    @Override
    public List<RecoveryActionVO> listByRisk(Long riskId, Long tenantId) {
        getRisk(riskId, tenantId);
        return actionMapper.selectByRiskId(riskId).stream()
                .map(RecoveryActionVO::fromEntity)
                .collect(Collectors.toList());
    }

    /** 开始执行工单：pending → executing，记录责任人 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void start(Long id, Long tenantId, Long userId) {
        DeliveryRecoveryAction action = actionMapper.selectById(id);
        if (action == null) {
            throw new BizException(ErrorCode.RECOVERY_ACTION_NOT_FOUND);
        }
        getRisk(action.getRiskId(), tenantId);
        int n = actionMapper.start(id, userId);
        if (n == 0) {
            throw new BizException(ErrorCode.RECOVERY_ACTION_STATUS_INVALID);
        }
        log.info("补救工单开始执行: id={}, ownerUserId={}", id, userId);
    }

    /** 完成工单：executing → completed，填回执/结果，自动缓解风险 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long id, Long tenantId, Long userId, CompleteRecoveryActionDTO dto) {
        DeliveryRecoveryAction action = actionMapper.selectById(id);
        if (action == null) {
            throw new BizException(ErrorCode.RECOVERY_ACTION_NOT_FOUND);
        }
        DeliveryRisk risk = getRisk(action.getRiskId(), tenantId);
        int n = actionMapper.complete(id, dto.getExecutionRemark().trim(), dto.getResult().trim(),
                LocalDateTime.now());
        if (n == 0) {
            throw new BizException(ErrorCode.RECOVERY_ACTION_STATUS_INVALID);
        }
        // 工单完成即视为风险缓解（已执行补救动作，不再是 active/warned/high/disposing）
        riskMapper.updateRiskState(risk.getId(), risk.getRiskLevel(),
                RiskStatus.MITIGATED.getCode(), risk.getEstimatedArrivalTime(),
                risk.getEvidence(), risk.getDueTime());
        log.info("补救工单完成: id={}, riskId={}", id, risk.getId());
    }

    /** 关闭风险：须有已完成补救工单回执（或风险已自动缓解），否则拒绝 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeRisk(Long riskId, Long tenantId, Long userId) {
        DeliveryRisk risk = getRisk(riskId, tenantId);
        if (RiskStatus.CLOSED.getCode().equals(risk.getRiskStatus())) {
            throw new BizException(ErrorCode.RISK_STATUS_INVALID);
        }
        // 关闭证据：已有完成的补救工单，或风险已被新物流事件缓解
        int completedCount = actionMapper.countCompletedByRiskId(riskId);
        if (completedCount == 0 && !RiskStatus.MITIGATED.getCode().equals(risk.getRiskStatus())) {
            throw new BizException(ErrorCode.RISK_CLOSE_EVIDENCE_REQUIRED);
        }
        riskMapper.close(riskId, RiskStatus.CLOSED.getCode());
        log.info("风险关闭: riskId={}, operator={}", riskId, userId);
    }

    /** 查风险并校验归属（采购方或供应方） */
    private DeliveryRisk getRisk(Long riskId, Long tenantId) {
        DeliveryRisk risk = riskMapper.selectById(riskId);
        if (risk == null) {
            throw new BizException(ErrorCode.RISK_NOT_FOUND);
        }
        Delivery d = deliveryMapper.selectById(risk.getDeliveryId());
        if (d == null) {
            throw new BizException(ErrorCode.RISK_NOT_FOUND);
        }
        if (!tenantId.equals(d.getBuyerTenantId()) && !tenantId.equals(d.getSupplierTenantId())) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }
        return risk;
    }
}
