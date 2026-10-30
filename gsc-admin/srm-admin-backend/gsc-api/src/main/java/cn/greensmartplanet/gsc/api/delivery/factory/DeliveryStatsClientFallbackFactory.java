package cn.greensmartplanet.gsc.api.delivery.factory;

import cn.greensmartplanet.gsc.api.delivery.DeliveryStatsClient;
import cn.greensmartplanet.gsc.api.delivery.dto.DeliveryStatsDTO;
import cn.greensmartplanet.gsc.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 交付域统计 Feign 降级工厂 — 降级返回全 0
 * <p>
 * 统计数字是工作台纯展示性数据，交付域不可用时工作台照常渲染
 * （面板数字为 0），配合告警日志人工兜底。
 *
 * @author gsc
 */
@Slf4j
@Component
public class DeliveryStatsClientFallbackFactory implements FallbackFactory<DeliveryStatsClient> {

    @Override
    public DeliveryStatsClient create(Throwable cause) {
        return tenantId -> {
            log.warn("交付服务不可用，工作台统计降级返回 0: tenantId={}, cause={}",
                    tenantId, cause != null ? cause.getMessage() : "unknown");
            return R.ok(new DeliveryStatsDTO());
        };
    }
}
