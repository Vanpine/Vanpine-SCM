package cn.greensmartplanet.gsc.trade.service.impl;

import cn.greensmartplanet.gsc.api.trade.dto.ContractBatchPlanDTO;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.trade.dto.BatchPlanItem;
import cn.greensmartplanet.gsc.trade.entity.Contract;
import cn.greensmartplanet.gsc.trade.entity.ContractBatchPlan;
import cn.greensmartplanet.gsc.trade.mapper.ContractBatchPlanMapper;
import cn.greensmartplanet.gsc.trade.service.ContractBatchPlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 合同分批计划服务实现
 * <p>
 * 分批计划是合同的履约数量拆解（分批交货），保存时校验各批数量之和必须等于合同履约数量，
 * 为空时默认一批全量；详情/列表批量回填时做 实体→DTO 转换。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractBatchPlanServiceImpl implements ContractBatchPlanService {

    private final ContractBatchPlanMapper contractBatchPlanMapper;

    /**
     * 保存合同分批计划：校验各批数量之和 = 履约数量后批量写入；空则默认一批全量
     */
    @Override
    public void saveBatchPlans(Contract contract, List<BatchPlanItem> items) {
        Integer total = contract.getQuantity();
        // 1. 未传批次计划：默认一批全量（数量=履约数量，交货日期=合同交期）
        if (items == null || items.isEmpty()) {
            if (total != null) {
                contractBatchPlanMapper.batchInsert(List.of(ContractBatchPlan.builder()
                        .contractId(contract.getId())
                        .batchNo(1)
                        .quantity(total)
                        .deliveryDate(contract.getDeliveryDate())
                        .build()));
            }
            return;
        }
        // 2. 校验各批数量之和必须等于合同履约数量（分批必须覆盖全部履约量）
        int sum = items.stream().mapToInt(i -> i.getQuantity() == null ? 0 : i.getQuantity()).sum();
        if (total == null || sum != total) {
            throw new BizException(ErrorCode.PROCUREMENT_CONTRACT_BATCH_SUM_MISMATCH);
        }
        // 3. 批量写入分批计划（batchNo 从 1 递增）
        List<ContractBatchPlan> plans = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            BatchPlanItem item = items.get(i);
            plans.add(ContractBatchPlan.builder()
                    .contractId(contract.getId())
                    .batchNo(i + 1)
                    .quantity(item.getQuantity())
                    .deliveryDate(item.getDeliveryDate())
                    .remark(item.getRemark())
                    .build());
        }
        contractBatchPlanMapper.batchInsert(plans);
    }

    /**
     * 重建合同分批计划（编辑合同时调用）：先逻辑删除旧的，再按本次提交重新写入
     */
    @Override
    public void rebuildBatchPlans(Long contractId, Contract contract, List<BatchPlanItem> items) {
        // 1. 逻辑删除旧的分批计划
        contractBatchPlanMapper.deleteByContractId(contractId);
        // 2. 按本次提交重新写入（空则默认一批全量）
        saveBatchPlans(contract, items);
    }

    /**
     * 按合同ID查分批计划 DTO（详情回填用）
     */
    @Override
    public List<BatchPlanItem> listByContractId(Long contractId) {
        return toBatchPlanItems(contractBatchPlanMapper.selectByContractId(contractId));
    }

    /**
     * 按合同ID批量查分批计划 DTO（列表页回填，消 N+1）
     */
    @Override
    public Map<Long, List<BatchPlanItem>> listByContractIds(List<Long> contractIds) {
        if (contractIds == null || contractIds.isEmpty()) {
            return Map.of();
        }
        return contractBatchPlanMapper.selectByContractIds(contractIds).stream()
                .collect(Collectors.groupingBy(ContractBatchPlan::getContractId))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> toBatchPlanItems(e.getValue())));
    }

    /**
     * 按合同ID批量查分批计划契约 DTO（内部契约用，含 batchNo；交付域发货排期/完成聚合）
     */
    @Override
    public Map<Long, List<ContractBatchPlanDTO>> listContractBatchPlanDTOsByContractIds(List<Long> contractIds) {
        if (contractIds == null || contractIds.isEmpty()) {
            return Map.of();
        }
        // 1. 按合同批量查分批计划实体（batch_no 升序，含雪花 id）
        List<ContractBatchPlan> plans = contractBatchPlanMapper.selectByContractIds(contractIds);
        // 2. 按合同ID分组：一个合同可能有多条分批计划，需按 contractId 聚合
        Map<Long, List<ContractBatchPlan>> groupedByContract = plans.stream()
                .collect(Collectors.groupingBy(ContractBatchPlan::getContractId));
        // 3. 每组计划实体转契约 DTO（id/batchNo 交付域拼装必需），输出 contractId → List<DTO>
        return groupedByContract.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().stream()
                        .map(p -> ContractBatchPlanDTO.builder()
                                .id(p.getId())
                                .contractId(p.getContractId())
                                .batchNo(p.getBatchNo())
                                .quantity(p.getQuantity())
                                .deliveryDate(p.getDeliveryDate())
                                .remark(p.getRemark())
                                .build())
                        .collect(Collectors.toList())));
    }

    /**
     * 分批计划实体 → DTO（编辑预填/详情回填共用）
     */
    @Override
    public List<BatchPlanItem> toBatchPlanItems(List<ContractBatchPlan> plans) {
        if (plans == null || plans.isEmpty()) {
            return List.of();
        }
        return plans.stream().map(p -> {
            BatchPlanItem item = new BatchPlanItem();
            item.setQuantity(p.getQuantity());
            item.setDeliveryDate(p.getDeliveryDate());
            item.setRemark(p.getRemark());
            return item;
        }).collect(Collectors.toList());
    }
}
