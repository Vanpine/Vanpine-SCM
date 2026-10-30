package cn.greensmartplanet.gsc.api.delivery.factory;

import cn.greensmartplanet.gsc.api.delivery.AiDeliveryClient;
import cn.greensmartplanet.gsc.api.delivery.dto.AiDeliveryDetailDTO;
import cn.greensmartplanet.gsc.api.delivery.dto.AiDeliveryOperateRequest;
import cn.greensmartplanet.gsc.api.delivery.dto.AiDeliveryQuery;
import cn.greensmartplanet.gsc.api.delivery.dto.AiDeliverySummaryDTO;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 交付域 AI 工具 Feign 降级工厂 — 交付服务不可用时抛 BizException，不静默通过
 * <p>
 * 决策依据（与 {@link cn.greensmartplanet.gsc.api.trade.factory.AiInquiryClientFallbackFactory} 一致）：
 * AI 工具查询返回的是要喂给模型作答的业务事实，写操作降级若静默通过会让用户误以为已落库，
 * 故一律拒绝（红线 6）。
 *
 * @author gsc
 */
@Slf4j
@Component
public class AiDeliveryClientFallbackFactory implements FallbackFactory<AiDeliveryClient> {

    @Override
    public AiDeliveryClient create(Throwable cause) {
        return new AiDeliveryClient() {
            @Override
            public R<List<AiDeliverySummaryDTO>> listDeliveries(AiDeliveryQuery query) {
                log.error("交付服务不可用，AI 工具交付单列表降级失败: tenantId={}, cause={}",
                        query != null ? query.getTenantId() : null,
                        cause != null ? cause.getMessage() : "unknown");
                throw new BizException(ErrorCode.INTERNAL_ERROR, "交付服务不可用，请稍后重试");
            }

            @Override
            public R<AiDeliveryDetailDTO> getDelivery(AiDeliveryQuery query) {
                log.error("交付服务不可用，AI 工具交付单详情降级失败: deliveryId={}, cause={}",
                        query != null ? query.getDeliveryId() : null,
                        cause != null ? cause.getMessage() : "unknown");
                throw new BizException(ErrorCode.INTERNAL_ERROR, "交付服务不可用，请稍后重试");
            }

            @Override
            public R<Long> submitPromiseDate(AiDeliveryOperateRequest request) {
                log.error("交付服务不可用，AI 工具提交承诺交期降级失败: deliveryId={}, cause={}",
                        request != null ? request.getDeliveryId() : null,
                        cause != null ? cause.getMessage() : "unknown");
                throw new BizException(ErrorCode.INTERNAL_ERROR, "交付服务不可用，请稍后重试");
            }

            @Override
            public R<Void> fillReturnLogistics(AiDeliveryOperateRequest request) {
                log.error("交付服务不可用，AI 工具填写退货物流降级失败: deliveryId={}, cause={}",
                        request != null ? request.getDeliveryId() : null,
                        cause != null ? cause.getMessage() : "unknown");
                throw new BizException(ErrorCode.INTERNAL_ERROR, "交付服务不可用，请稍后重试");
            }
        };
    }
}
