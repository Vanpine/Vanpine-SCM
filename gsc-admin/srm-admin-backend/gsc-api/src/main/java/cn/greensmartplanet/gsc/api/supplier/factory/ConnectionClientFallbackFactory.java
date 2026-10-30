package cn.greensmartplanet.gsc.api.supplier.factory;

import cn.greensmartplanet.gsc.api.supplier.ConnectionClient;
import cn.greensmartplanet.gsc.api.supplier.dto.MyConnectionStatusDTO;
import cn.greensmartplanet.gsc.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 供应商域对接查询 Feign 降级工厂 — 降级返回空/0
 * <p>
 * 对接计数与状态是需求列表/广场的展示性字段，供应商域不可用时
 * 显示 0/无状态，不阻断需求列表主流程；配合告警日志人工兜底。
 *
 * @author gsc
 */
@Slf4j
@Component
public class ConnectionClientFallbackFactory implements FallbackFactory<ConnectionClient> {

    @Override
    public ConnectionClient create(Throwable cause) {
        return new ConnectionClient() {
            @Override
            public R<Map<Long, Long>> countGroupByRequirementIds(Long buyerTenantId, List<Long> requirementIds) {
                log.warn("供应商服务不可用，对接计数降级返回空: size={}, cause={}",
                        requirementIds != null ? requirementIds.size() : 0,
                        cause != null ? cause.getMessage() : "unknown");
                return R.ok(Map.of());
            }

            @Override
            public R<Map<Long, MyConnectionStatusDTO>> getMyConnectionStatus(Long tenantId, List<Long> requirementIds) {
                log.warn("供应商服务不可用，我的对接状态降级返回空: size={}, cause={}",
                        requirementIds != null ? requirementIds.size() : 0,
                        cause != null ? cause.getMessage() : "unknown");
                return R.ok(Map.of());
            }
        };
    }
}
