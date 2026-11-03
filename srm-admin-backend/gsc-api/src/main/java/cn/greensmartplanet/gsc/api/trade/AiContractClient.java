package cn.greensmartplanet.gsc.api.trade;

import cn.greensmartplanet.gsc.api.constant.ServiceNameConstants;
import cn.greensmartplanet.gsc.api.trade.dto.AiContractQuery;
import cn.greensmartplanet.gsc.api.trade.dto.AiContractSummaryDTO;
import cn.greensmartplanet.gsc.api.trade.factory.AiContractClientFallbackFactory;
import cn.greensmartplanet.gsc.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 交易域服务 Feign 客户端契约 — 专供 AI 工具调用的合同查询
 * <p>
 * 契约约定（调用方与提供方共同遵守）：
 * <ul>
 *   <li>提供方 gsc-service-trade 的 InternalAiContractController 实现本接口，URL/参数编译期强一致</li>
 *   <li>AI 域用它替代直查 contract 表，合同数据由交易域统一出口</li>
 *   <li>返回类型一律契约 DTO（禁止 Map），字段由编译期保证一致</li>
 *   <li>查询语义：无则空列表；HTTP 错误仅代表服务故障</li>
 *   <li>降级（FallbackFactory）：抛 BizException——AI 工具查询是喂给模型作答的数据底座，
 *       缺了它模型会基于空数据编造答案，故不静默通过</li>
 * </ul>
 *
 * @author gsc
 */
@FeignClient(
        name = ServiceNameConstants.GSC_TRADE_SERVICE,
        path = "/internal/ai/contract",
        contextId = "aiContractClient",
        fallbackFactory = AiContractClientFallbackFactory.class)
public interface AiContractClient {

    /** 按当前租户（buyer/supplier 双视角）分页查合同摘要（无则空列表） */
    @PostMapping("/list")
    R<List<AiContractSummaryDTO>> listContracts(@RequestBody AiContractQuery query);
}
