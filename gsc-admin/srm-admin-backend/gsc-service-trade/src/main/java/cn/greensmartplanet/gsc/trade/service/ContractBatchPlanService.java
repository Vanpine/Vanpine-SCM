package cn.greensmartplanet.gsc.trade.service;

import cn.greensmartplanet.gsc.api.trade.dto.ContractBatchPlanDTO;
import cn.greensmartplanet.gsc.trade.dto.BatchPlanItem;
import cn.greensmartplanet.gsc.trade.entity.Contract;
import cn.greensmartplanet.gsc.trade.entity.ContractBatchPlan;

import java.util.List;
import java.util.Map;

/**
 * 合同分批计划服务接口 — 保存 / 查询 / DTO↔Entity 转换
 * <p>
 * 拆分自 ContractServiceImpl：创建/编辑合同保存批次计划（校验各批数量之和 = 履约数量），
 * 详情/列表批量回填计划，消除 ContractLifecycleService 对 ContractBatchPlanMapper 的直接依赖。
 *
 * @author gsc
 */
public interface ContractBatchPlanService {

    /**
     * 保存合同分批计划
     * <p>
     * batchPlans 非空时校验各批数量之和等于合同履约数量后批量写入；
     * 为空则默认写一条全量计划（batchNo=1，数量=合同履约数量，交货日期=合同交期）。
     * 合同履约数量为空时不落计划，由 createFromContract 兜底生成单批全量交付单。
     */
    void saveBatchPlans(Contract contract, List<BatchPlanItem> items);

    /**
     * 重建合同分批计划（编辑合同时调用）：先逻辑删除旧的，再按本次提交重新写入
     */
    void rebuildBatchPlans(Long contractId, Contract contract, List<BatchPlanItem> items);

    /**
     * 按合同ID查分批计划 DTO（batch_no 升序）
     */
    List<BatchPlanItem> listByContractId(Long contractId);

    /**
     * 按合同ID批量查分批计划 DTO（contractId → 计划列表，列表页回填用）
     */
    Map<Long, List<BatchPlanItem>> listByContractIds(List<Long> contractIds);

    /**
     * 按合同ID批量查分批计划契约 DTO（contractId → 计划列表，交付域内部契约用，含 batchNo）
     */
    Map<Long, List<ContractBatchPlanDTO>> listContractBatchPlanDTOsByContractIds(List<Long> contractIds);

    /**
     * 分批计划实体 → DTO（回填详情/列表用）
     */
    List<BatchPlanItem> toBatchPlanItems(List<ContractBatchPlan> plans);
}
