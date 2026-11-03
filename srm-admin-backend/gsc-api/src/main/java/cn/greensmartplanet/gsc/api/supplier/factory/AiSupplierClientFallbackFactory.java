package cn.greensmartplanet.gsc.api.supplier.factory;

import cn.greensmartplanet.gsc.api.supplier.AiSupplierClient;
import cn.greensmartplanet.gsc.api.supplier.dto.AiSupplierDetailDTO;
import cn.greensmartplanet.gsc.api.supplier.dto.AiSupplierNoteCreateRequest;
import cn.greensmartplanet.gsc.api.supplier.dto.AiSupplierNoteVO;
import cn.greensmartplanet.gsc.api.supplier.dto.AiSupplierRiskDTO;
import cn.greensmartplanet.gsc.api.supplier.dto.AiSupplierSearchQuery;
import cn.greensmartplanet.gsc.api.supplier.dto.AiSupplierSummaryDTO;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 供应商域 AI 工具查询 Feign 降级工厂 — 供应商服务不可用时抛 BizException，不静默通过
 * <p>
 * 决策依据（与 {@link ProfileClientFallbackFactory} 降级放行相反）：AI 工具查询返回的是
 * 要喂给模型作答的业务事实，缺了它模型会基于空数据编造答案（幻觉），比直接报错更危险，
 * 故按数据底座语义拒绝（红线 6）。
 *
 * @author gsc
 */
@Slf4j
@Component
public class AiSupplierClientFallbackFactory implements FallbackFactory<AiSupplierClient> {

    @Override
    public AiSupplierClient create(Throwable cause) {
        return new AiSupplierClient() {
            @Override
            public R<List<AiSupplierSummaryDTO>> searchSuppliers(AiSupplierSearchQuery query) {
                log.error("供应商服务不可用，AI 工具供应商搜索降级失败: keyword={}, cause={}",
                        query != null ? query.getKeyword() : null,
                        cause != null ? cause.getMessage() : "unknown");
                throw new BizException(ErrorCode.INTERNAL_ERROR, "供应商服务不可用，请稍后重试");
            }

            @Override
            public R<AiSupplierDetailDTO> getSupplierDetail(Long tenantId) {
                log.error("供应商服务不可用，AI 工具供应商详情降级失败: tenantId={}, cause={}",
                        tenantId, cause != null ? cause.getMessage() : "unknown");
                throw new BizException(ErrorCode.INTERNAL_ERROR, "供应商服务不可用，请稍后重试");
            }

            @Override
            public R<AiSupplierNoteVO> createSupplierNote(AiSupplierNoteCreateRequest request) {
                log.error("供应商服务不可用，AI 工具供应商备注降级失败: supplierTenantId={}, cause={}",
                        request != null ? request.getSupplierTenantId() : null,
                        cause != null ? cause.getMessage() : "unknown");
                throw new BizException(ErrorCode.INTERNAL_ERROR, "供应商服务不可用，请稍后重试");
            }

            @Override
            public R<AiSupplierRiskDTO> assessSupplierRisk(Long tenantId) {
                log.error("供应商服务不可用，AI 工具供应商风险评分降级失败: tenantId={}, cause={}",
                        tenantId, cause != null ? cause.getMessage() : "unknown");
                throw new BizException(ErrorCode.INTERNAL_ERROR, "供应商服务不可用，请稍后重试");
            }
        };
    }
}
