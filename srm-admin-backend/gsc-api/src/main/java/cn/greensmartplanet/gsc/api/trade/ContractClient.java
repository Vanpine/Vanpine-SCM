package cn.greensmartplanet.gsc.api.trade;

import cn.greensmartplanet.gsc.api.constant.ServiceNameConstants;
import cn.greensmartplanet.gsc.api.trade.dto.ContractBatchPlanDTO;
import cn.greensmartplanet.gsc.api.trade.dto.ContractCompleteDTO;
import cn.greensmartplanet.gsc.api.trade.dto.ContractInfoDTO;
import cn.greensmartplanet.gsc.api.trade.factory.ContractClientFallbackFactory;
import cn.greensmartplanet.gsc.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 交易域服务 Feign 客户端契约 — 合同信息查询与状态回写
 * <p>
 * 契约约定（调用方与提供方共同遵守）：
 * <ul>
 *   <li>提供方 gsc-service-trade 的 InternalContractController 实现本接口，URL/参数编译期强一致</li>
 *   <li>交付域用它替代跨服务直查 contract 表及直改合同状态</li>
 *   <li>返回类型一律契约 DTO（禁止 Map），字段由编译期保证一致</li>
 *   <li>降级（FallbackFactory）：查询返回 null/空（列表回填最多缺文案，发货校验判空抛原错误码）；
 *       状态回写返回 false（降级时拒绝完成合同，交付侧状态不推进，与直改行为保守一致）</li>
 * </ul>
 *
 * @author gsc
 */
@FeignClient(
        name = ServiceNameConstants.GSC_TRADE_SERVICE,
        path = "/internal/contract",
        contextId = "contractClient",
        fallbackFactory = ContractClientFallbackFactory.class)
public interface ContractClient {

    /** 按合同ID查合同信息（不存在返回 data=null） */
    @GetMapping("/{id}")
    R<ContractInfoDTO> getContractById(@PathVariable("id") Long id);

    /** 批量按合同ID查合同信息（contractId → DTO，不存在的合同不在 map 中） */
    @PostMapping("/batch")
    R<Map<Long, ContractInfoDTO>> getContractByIds(@RequestBody List<Long> ids);

    /** 批量按合同ID查询盘编号（contractId → inquiryNo，无询盘或合同不存在的不在 map 中） */
    @PostMapping("/inquiry-nos")
    R<Map<Long, String>> getInquiryNosByContractIds(@RequestBody List<Long> contractIds);

    /** 按供应商租户查已签署合同列表（替代交付域直查 contract 表；无则空列表） */
    @GetMapping("/signed-by-supplier")
    R<List<ContractInfoDTO>> listSignedBySupplier(@RequestParam("supplierTenantId") Long supplierTenantId);

    /** 批量按合同ID查分批计划（contractId → 计划列表，替代交付域直查 contract_batch_plan 表） */
    @PostMapping("/batch-plans")
    R<Map<Long, List<ContractBatchPlanDTO>>> getBatchPlansByContractIds(@RequestBody List<Long> contractIds);

    /** 合同全部交付验收完成 → 回写合同状态 completed（幂等：已 completed 返回 true） */
    @PutMapping("/{id}/completed")
    R<Boolean> updateCompleted(@PathVariable("id") Long id,
                               @RequestBody ContractCompleteDTO context);
}
