package cn.greensmartplanet.gsc.trade.controller;

import cn.greensmartplanet.gsc.api.trade.dto.ContractBatchPlanDTO;
import cn.greensmartplanet.gsc.api.trade.dto.ContractCompleteDTO;
import cn.greensmartplanet.gsc.api.trade.dto.ContractInfoDTO;
import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.trade.service.ContractCompletionService;
import cn.greensmartplanet.gsc.trade.service.ContractQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 交易内部接口 — 合同（仅服务间经内网服务发现 Feign 调用）
 * <p>
 * 网关已用 InternalBlockWebFilter 拒绝 /internal/** 外部访问（403），
 * 此处接口只对注册中心内的其他服务可见。
 * <p>
 * ⚠️ 路径一致性：本控制器路径与 gsc-api 的 ContractClient 契约保持一致
 * （契约接口为调用方声明，提供方不实现该接口，两端路径由本注释约定同步修改）。
 *
 * @author gsc
 */
@RestController
@RequestMapping("/internal/contract")
@RequiredArgsConstructor
public class InternalContractController {

    private final ContractQueryService contractQueryService;
    private final ContractCompletionService contractCompletionService;

    /** 按合同ID查合同信息（不存在返回 data=null） */
    @GetMapping("/{id}")
    public R<ContractInfoDTO> getContractById(@PathVariable("id") Long id) {
        return R.ok(contractQueryService.getContractInfoById(id));
    }

    /** 批量按合同ID查合同信息（不存在的合同不在 map 中） */
    @PostMapping("/batch")
    public R<Map<Long, ContractInfoDTO>> getContractByIds(@RequestBody List<Long> ids) {
        return R.ok(contractQueryService.getContractInfoByIds(ids));
    }

    /** 批量按合同ID查询盘编号（无询盘或合同不存在的不在 map 中） */
    @PostMapping("/inquiry-nos")
    public R<Map<Long, String>> getInquiryNosByContractIds(@RequestBody List<Long> contractIds) {
        return R.ok(contractQueryService.getInquiryNosByContractIds(contractIds));
    }

    /** 按供应商租户查已签署合同列表（交付域列可建交付单合同/发货排期用；无则空列表） */
    @GetMapping("/signed-by-supplier")
    public R<List<ContractInfoDTO>> listSignedBySupplier(@RequestParam("supplierTenantId") Long supplierTenantId) {
        return R.ok(contractQueryService.listSignedBySupplier(supplierTenantId));
    }

    /** 批量按合同ID查分批计划（交付域发货排期/完成聚合用；无则空 map） */
    @PostMapping("/batch-plans")
    public R<Map<Long, List<ContractBatchPlanDTO>>> getBatchPlansByContractIds(@RequestBody List<Long> contractIds) {
        return R.ok(contractQueryService.getBatchPlansByContractIds(contractIds));
    }

    /** 合同全部交付验收完成 → 回写合同状态 completed（幂等：已 completed 返回 true） */
    @PutMapping("/{id}/completed")
    public R<Boolean> updateCompleted(@PathVariable("id") Long id,
                                      @RequestBody ContractCompleteDTO context) {
        return R.ok(contractCompletionService.updateCompleted(id, context));
    }
}
