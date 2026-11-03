package cn.greensmartplanet.gsc.api.delivery.factory;

import cn.greensmartplanet.gsc.api.delivery.DeliveryClient;
import cn.greensmartplanet.gsc.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 交付域 Feign 降级工厂 — 交付服务不可用时返回 false（视为无进行中交付单）
 * <p>
 * 用 FallbackFactory 而非 fallback：回调可拿到 Throwable，
 * 日志记录完整异常堆栈，能定位"为什么降级"（服务下线/超时/熔断）。
 * <p>
 * 降级决策依据：终止/完成保护是"挡错"性质校验，降级放行最多允许一次误操作；
 * 反向（降级拒绝）会让整个合同域不可用。配合告警日志人工兜底。
 *
 * @author gsc
 */
@Slf4j
@Component
public class DeliveryClientFallbackFactory implements FallbackFactory<DeliveryClient> {

    @Override
    public DeliveryClient create(Throwable cause) {
        return contractId -> {
            log.warn("交付服务不可用，hasActive 降级返回 false（放行）: contractId={}, cause={}",
                    contractId, cause != null ? cause.getMessage() : "unknown");
            return R.ok(false);
        };
    }
}
