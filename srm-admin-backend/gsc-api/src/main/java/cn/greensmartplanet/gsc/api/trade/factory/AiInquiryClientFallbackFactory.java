package cn.greensmartplanet.gsc.api.trade.factory;

import cn.greensmartplanet.gsc.api.trade.AiInquiryClient;
import cn.greensmartplanet.gsc.api.trade.dto.AiInquiryDeadlineRequest;
import cn.greensmartplanet.gsc.api.trade.dto.AiInquiryQuery;
import cn.greensmartplanet.gsc.api.trade.dto.AiInquirySummaryDTO;
import cn.greensmartplanet.gsc.api.trade.dto.AiInquiryUpdateRequest;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 交易域 AI 工具询盘 Feign 降级工厂 — 交易服务不可用时抛 BizException，不静默通过
 * <p>
 * 决策依据（与 {@link AiContractClientFallbackFactory} 一致）：AI 工具查询返回的是
 * 要喂给模型作答的业务事实，写操作降级若静默通过会让用户误以为已落库，故一律拒绝（红线 6）。
 *
 * @author gsc
 */
@Slf4j
@Component
public class AiInquiryClientFallbackFactory implements FallbackFactory<AiInquiryClient> {

    @Override
    public AiInquiryClient create(Throwable cause) {
        return new AiInquiryClient() {
            @Override
            public R<List<AiInquirySummaryDTO>> listInquiries(AiInquiryQuery query) {
                log.error("交易服务不可用，AI 工具询盘列表降级失败: tenantId={}, cause={}",
                        query != null ? query.getTenantId() : null,
                        cause != null ? cause.getMessage() : "unknown");
                throw new BizException(ErrorCode.INTERNAL_ERROR, "交易服务不可用，请稍后重试");
            }

            @Override
            public R<AiInquirySummaryDTO> getInquiry(AiInquiryQuery query) {
                log.error("交易服务不可用，AI 工具询盘详情降级失败: inquiryId={}, cause={}",
                        query != null ? query.getInquiryId() : null,
                        cause != null ? cause.getMessage() : "unknown");
                throw new BizException(ErrorCode.INTERNAL_ERROR, "交易服务不可用，请稍后重试");
            }

            @Override
            public R<Void> updateInquiry(AiInquiryUpdateRequest request) {
                log.error("交易服务不可用，AI 工具修改询盘降级失败: inquiryId={}, cause={}",
                        request != null ? request.getInquiryId() : null,
                        cause != null ? cause.getMessage() : "unknown");
                throw new BizException(ErrorCode.INTERNAL_ERROR, "交易服务不可用，请稍后重试");
            }

            @Override
            public R<Void> extendInquiryDeadline(AiInquiryDeadlineRequest request) {
                log.error("交易服务不可用，AI 工具延长询盘截止降级失败: inquiryId={}, cause={}",
                        request != null ? request.getInquiryId() : null,
                        cause != null ? cause.getMessage() : "unknown");
                throw new BizException(ErrorCode.INTERNAL_ERROR, "交易服务不可用，请稍后重试");
            }
        };
    }
}
