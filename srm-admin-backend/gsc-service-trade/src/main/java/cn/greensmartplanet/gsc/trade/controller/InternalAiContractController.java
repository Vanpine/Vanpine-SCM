package cn.greensmartplanet.gsc.trade.controller;

import cn.greensmartplanet.gsc.api.trade.dto.AiContractQuery;
import cn.greensmartplanet.gsc.api.trade.dto.AiContractSummaryDTO;
import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.trade.service.ContractQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 交易内部接口 — AI 工具合同查询（仅服务间经内网服务发现 Feign 调用）
 * <p>
 * 网关已用 InternalBlockWebFilter 拒绝 /internal/** 外部访问（403），
 * 此处接口只对注册中心内的其他服务可见。
 * <p>
 * ⚠️ 路径一致性：本控制器路径与 gsc-api 的 AiContractClient 契约保持一致
 * （契约接口为调用方声明，提供方不实现该接口，两端路径由本注释约定同步修改）。
 *
 * @author gsc
 */
@RestController
@RequestMapping("/internal/ai/contract")
@RequiredArgsConstructor
public class InternalAiContractController {

    private final ContractQueryService contractQueryService;

    /** 按当前租户（buyer/supplier 双视角）分页查合同摘要（AI 工具 list_contracts；无则空列表） */
    @PostMapping("/list")
    public R<List<AiContractSummaryDTO>> listContracts(@RequestBody AiContractQuery query) {
        return R.ok(contractQueryService.listForAi(query));
    }
}
