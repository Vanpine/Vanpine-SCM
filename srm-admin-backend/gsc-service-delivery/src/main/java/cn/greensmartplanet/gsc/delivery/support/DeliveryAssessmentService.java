package cn.greensmartplanet.gsc.delivery.support;

import cn.greensmartplanet.gsc.api.supplier.ProfileClient;
import cn.greensmartplanet.gsc.api.supplier.dto.PerformanceUpdateDTO;
import cn.greensmartplanet.gsc.api.trade.ContractClient;
import cn.greensmartplanet.gsc.api.trade.dto.ContractBatchPlanDTO;
import cn.greensmartplanet.gsc.api.trade.dto.ContractCompleteDTO;
import cn.greensmartplanet.gsc.api.trade.dto.ContractInfoDTO;
import cn.greensmartplanet.gsc.common.enums.ContractStatus;
import cn.greensmartplanet.gsc.delivery.entity.Delivery;
import cn.greensmartplanet.gsc.delivery.entity.Notification;
import cn.greensmartplanet.gsc.delivery.mapper.DeliveryMapper;
import cn.greensmartplanet.gsc.delivery.projection.PerformanceStatRow;
import cn.greensmartplanet.gsc.delivery.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 交付终态评估协作者 assessment 评估
 * <p>
 * 聚合三个共享评估逻辑（从 DeliveryServiceImpl 私有方法迁出），
 * 被验收合格 / 验收不合格 / 部分残次三个终态方法共用：
 * <ul>
 *   <li>recalcSupplierPerformance：履约表现重算并 Feign 回写供应商画像</li>
 *   <li>calcPenalty：违约金快照（Feign 读合同条款）</li>
 *   <li>completeContractIfAllDelivered：合同完成聚合判断（Feign 回写交易域）</li>
 * </ul>
 *
 * @author gsc
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryAssessmentService {

    private final DeliveryMapper deliveryMapper;
    // 合同信息归交易域，Feign 查询/回写（违约金条款/合同完成状态）
    private final ContractClient contractClient;
    // 画像归供应商域，Feign 回写（履约表现）
    private final ProfileClient profileClient;
    private final NotificationService notificationService;

    /**
     * 重算供应商履约表现并回写企业画像（交付终态触发，补发批 excluded；画像归供应商域，Feign 回写）
     * <p>
     * 本域聚合计算留在事务内同步完成（决策依据），跨域 Feign 回写移到事务提交后
     * （afterCommit 回调）——避免长事务占用 DB 连接等待远程响应。
     */
    public void recalcSupplierPerformance(Long supplierTenantId) {
        if (supplierTenantId == null) return;
        // 1. 本域聚合履约统计（样本=已验收普通批，补发批 excluded）
        PerformanceStatRow stat = deliveryMapper.selectPerformanceStat(supplierTenantId);
        long sampleSize = stat.getSampleSize() == null ? 0 : stat.getSampleSize();
        // 2. 组装回写 DTO（无样本清空 / 有样本按时准率+验收通过率+综合分）
        PerformanceUpdateDTO dto;
        if (sampleSize == 0) {
            dto = PerformanceUpdateDTO.builder().tenantId(supplierTenantId).returnCount(0).build();
        } else {
            long passCount = stat.getPassCount() == null ? 0 : stat.getPassCount();
            long returnCount = stat.getReturnCount() == null ? 0 : stat.getReturnCount();
            long onTimeCount = stat.getOnTimeCount() == null ? 0 : stat.getOnTimeCount();
            BigDecimal sample = BigDecimal.valueOf(sampleSize);
            BigDecimal onTimeRate = BigDecimal.valueOf(onTimeCount)
                    .multiply(BigDecimal.valueOf(100)).divide(sample, 2, RoundingMode.HALF_UP);
            BigDecimal acceptPassRate = BigDecimal.valueOf(passCount)
                    .multiply(BigDecimal.valueOf(100)).divide(sample, 2, RoundingMode.HALF_UP);
            BigDecimal perfScore = onTimeRate.add(acceptPassRate)
                    .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            dto = PerformanceUpdateDTO.builder()
                    .tenantId(supplierTenantId)
                    .onTimeRate(onTimeRate)
                    .acceptPassRate(acceptPassRate)
                    .returnCount((int) returnCount)
                    .perfScore(perfScore)
                    .perfSampleSize((int) sampleSize)
                    .build();
        }
        // 3. 跨域 Feign 回写移到事务提交后执行（长事务不等远程响应；失败仅日志不影响主业务）
        PerformanceUpdateDTO finalDto = dto;
        runAfterCommit(() -> {
            profileClient.updatePerformance(finalDto);
            log.info("履约表现重算/清空: supplierTenantId={}, sampleSize={}", supplierTenantId, sampleSize);
        });
    }

    /**
     * 事务提交后执行远程 Feign 写（避免长事务占连接等待远程响应）
     * <p>
     * 无活跃事务时直接执行（防御，正常调用链均有事务）；afterCommit 内异常仅日志不影响主业务。
     */
    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        action.run();
                    } catch (Exception e) {
                        log.warn("事务提交后远程回写失败（不影响已提交的主业务）", e);
                    }
                }
            });
        } else {
            // 无事务上下文（防御性兜底）：直接同步执行，异常上抛由调用方处理
            action.run();
        }
    }

    /**
     * 计算违约金快照（验收合格/部分验收时调用）
     * <p>
     * 延迟天数 = 收货日期 - 考核基准（已确认承诺优先，回退合同交期）- 容差天数；
     * 金额 = 本批金额 × 每日费率 × 延迟天数，与上限（本批金额 × 上限比例）取小。
     * 合同未配违约金条款 / 补发批（金额为空）/ 延迟≤0 均不计算。
     */
    public void calcPenalty(Delivery d) {
        // 补发批金额为空（免费补发），不计算违约金
        if (d.getAmount() == null) {
            return;
        }
        // 合同条款走 Feign 契约查询（合同归交易域，不直查他域表）
        ContractInfoDTO contract = contractClient.getContractById(d.getContractId()).getData();
        if (contract == null || contract.getPenaltyDailyRate() == null) {
            return;
        }
        // 延迟天数：收货 - 考核基准 - 容差
        LocalDateTime receiveTime = d.getReceiveTime();
        LocalDate effectiveDate = d.getConfirmedDeliveryDate() != null
                ? d.getConfirmedDeliveryDate() : d.getDeliveryDate();
        if (receiveTime == null || effectiveDate == null) {
            return;
        }
        int tolerance = contract.getAllowedDelayDays() != null ? contract.getAllowedDelayDays() : 0;
        long delayDays = Duration.between(effectiveDate.atStartOfDay(), receiveTime.toLocalDate().atStartOfDay()).toDays() - tolerance;
        if (delayDays <= 0) {
            // 未超期：写 0 快照，明确"已算过无违约金"
            deliveryMapper.updatePenalty(d.getId(), 0, BigDecimal.ZERO);
            return;
        }
        BigDecimal daily = contract.getPenaltyDailyRate();
        BigDecimal raw = d.getAmount().multiply(daily).multiply(BigDecimal.valueOf(delayDays));
        BigDecimal amount = raw;
        if (contract.getPenaltyMaxRate() != null) {
            BigDecimal cap = d.getAmount().multiply(contract.getPenaltyMaxRate());
            amount = raw.min(cap);
        }
        deliveryMapper.updatePenalty(d.getId(), (int) delayDays, amount.setScale(4, RoundingMode.HALF_UP));
        log.info("违约金计算: deliveryId={}, delayDays={}, amount={}", d.getId(), delayDays, amount);
    }

    /**
     * 聚合判断：该合同所有交付单均验收合格 → 合同 completed（状态回写走 Feign 调交易域）
     * <p>
     * 决策逻辑（本域 count + 分批计划比对 + 合同 signed 校验）留在事务内同步完成并返回，
     * 跨域 Feign 写 updateCompleted 与完成通知移到事务提交后执行（afterCommit）——
     * 既避免长事务占连接等远程响应，也保证事务回滚时不误发完成通知/误回写合同状态。
     */
    public boolean completeContractIfAllDelivered(Long contractId, Long buyerTenantId, Long userId) {
        // 1. 本域统计：合同下交付单总数与已验收数（全部验收才可能完成）
        long total = deliveryMapper.countTotalByContract(contractId);
        long accepted = deliveryMapper.countAcceptedByContract(contractId);
        if (total == 0 || accepted < total) {
            return false;
        }
        // 2. 分批计划比对：只要还有计划没生成对应交付单，合同不能提前完成
        // 2a. Feign 取该合同分批计划（契约 DTO 含 batchNo，替代跨域直查 contract_batch_plan）
        List<ContractBatchPlanDTO> plans = contractClient.getBatchPlansByContractIds(List.of(contractId))
                .getData().getOrDefault(contractId, List.of());
        // 2b. 本域查该合同已有交付单的批次号集合（去重）
        Set<Integer> deliveredBatchNos = new HashSet<>(deliveryMapper.selectBatchNosByContract(contractId));
        // 2c. 逐条比对：任一计划批次未建交付单 → 未全部交付，不能完成
        boolean allPlansDelivered = plans.stream()
                .allMatch(p -> deliveredBatchNos.contains(p.getBatchNo()));
        if (!allPlansDelivered) {
            return false;
        }
        // 3. 合同须仍为 signed 状态（Feign 读，决策必需）
        ContractInfoDTO contract = contractClient.getContractById(contractId).getData();
        if (contract == null || !ContractStatus.SIGNED.getCode().equals(contract.getStatus())) {
            return false;
        }
        // 4. 事务提交后：回写合同 completed + 发完成通知（Feign 写 + 通知移出长事务）
        runAfterCommit(() -> {
            // 4a. Feign 回写合同状态（幂等：已 completed 返回 true）；降级拒绝时状态不推进，恢复后可重试
            Boolean updated = contractClient.updateCompleted(contractId, ContractCompleteDTO.builder()
                    .buyerTenantId(buyerTenantId)
                    .userId(userId)
                    .build()).getData();
            if (!Boolean.TRUE.equals(updated)) {
                log.warn("合同完成状态回写未生效（可能已 completed 或交易域不可用）: contractId={}", contractId);
            }
            // 4b. 完成通知（用户可见，MQ 落库由平台域消费）
            notificationService.notify(Notification.builder()
                    .type("CONTRACT_COMPLETE")
                    .title("合同已完成")
                    .content("合同《" + contract.getTitle() + "》已执行完成")
                    .receiverTenantId(contract.getSupplierTenantId())
                    .relatedType("contract")
                    .relatedId(contractId)
                    .fromTenantId(buyerTenantId)
                    .fromUserId(userId)
                    .build());
            log.info("合同全部交付完成，自动置为 completed: contractId={}", contractId);
        });
        return true;
    }
}
