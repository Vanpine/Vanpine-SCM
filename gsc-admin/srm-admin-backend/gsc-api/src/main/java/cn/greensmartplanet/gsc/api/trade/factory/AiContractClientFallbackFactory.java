package cn.greensmartplanet.gsc.api.trade.factory;

import cn.greensmartplanet.gsc.api.trade.AiContractClient;
import cn.greensmartplanet.gsc.api.trade.dto.AiContractQuery;
import cn.greensmartplanet.gsc.api.trade.dto.AiContractSummaryDTO;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 交易域 AI 工具查询 Feign 降级工厂 — 交易服务不可用时抛 BizException，不静默通过
 * <p>
 * 决策依据（与 {@link ContractClientFallbackFactory} 降级放行相反）：AI 工具查询返回的是
 * 要喂给模型作答的业务事实，缺了它模型会基于空数据编造答案（幻觉），比直接报错更危险，
 * 故按数据底座语义拒绝（红线 6）。
 *
 * @author gsc
 */
@Slf4j
@Component
public class AiContractClientFallbackFactory implements FallbackFactory<AiContractClient> {

    @Override
    public AiContractClient create(Throwable cause) {
        return new AiContractClient() {
            @Override
            public R<List<AiContractSummaryDTO>> listContracts(AiContractQuery query) {
                log.error("交易服务不可用，AI 工具合同列表降级失败: tenantId={}, cause={}",
                        query != null ? query.getTenantId() : null,
                        cause != null ? cause.getMessage() : "unknown");
                throw new BizException(ErrorCode.INTERNAL_ERROR, "交易服务不可用，请稍后重试");
            }
        };
    }
}
