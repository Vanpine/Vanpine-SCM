package cn.greensmartplanet.gsc.trade.service;

import cn.greensmartplanet.gsc.api.trade.dto.AiContractQuery;
import cn.greensmartplanet.gsc.api.trade.dto.AiContractSummaryDTO;
import cn.greensmartplanet.gsc.api.trade.dto.ContractBatchPlanDTO;
import cn.greensmartplanet.gsc.api.trade.dto.ContractInfoDTO;
import cn.greensmartplanet.gsc.trade.vo.ContractVO;

import java.util.List;
import java.util.Map;

/**
 * 合同查询服务接口 — 列表 / 详情 / 活跃合同报价 / 内部契约查询
 * <p>
 * 只读查询归口本服务，与写操作（生命周期/签署/完成）解耦。
 *
 * @author gsc
 */
public interface ContractQueryService {

    /**
     * 分页列表（采购方/供应方）
     */
    Map<String, Object> list(Long tenantId, int pageNum, int pageSize,
                             String status, String keyword, String role);

    /**
     * 合同详情
     */
    ContractVO getDetail(Long id, Long tenantId);

    /**
     * 查某询盘下已有活跃（非终止）合同的 winning_quote_id 列表（前端禁选用）
     */
    List<Long> getActiveContractQuoteIds(Long inquiryId);

    /**
     * 按合同ID查合同信息契约 DTO（内部契约，不存在返回 null）
     */
    ContractInfoDTO getContractInfoById(Long id);

    /**
     * 批量按合同ID查合同信息契约 DTO（内部契约，contractId → DTO，不存在的合同不在 map 中）
     */
    Map<Long, ContractInfoDTO> getContractInfoByIds(List<Long> ids);

    /**
     * 批量按合同ID查询盘编号（内部契约，contractId → inquiryNo，无询盘或合同不存在的不在 map 中）
     */
    Map<Long, String> getInquiryNosByContractIds(List<Long> contractIds);

    /**
     * 按供应商租户查已签署合同契约 DTO 列表（内部契约，交付域列可建交付单合同/发货排期用）
     */
    List<ContractInfoDTO> listSignedBySupplier(Long supplierTenantId);

    /**
     * 批量按合同ID查分批计划契约 DTO（内部契约，contractId → 计划列表，交付域发货排期/完成聚合用）
     */
    Map<Long, List<ContractBatchPlanDTO>> getBatchPlansByContractIds(List<Long> contractIds);

    /**
     * 按当前租户分页查合同摘要（AI 工具 list_contracts 专用）
     * <p>
     * 复用 list 的 buyer/supplier 双视角查询逻辑，返回 AI 友好的摘要字段子集。
     * tenantId 由调用方（AI 域）从登录态注入，禁止由模型传值（防越权，红线 2）。
     *
     * @param query 查询条件（tenantId 必填，status/keyword/role/page/pageSize 可空）
     * @return 合同摘要列表（无则空列表）
     */
    List<AiContractSummaryDTO> listForAi(AiContractQuery query);
}
