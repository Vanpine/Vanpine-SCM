package cn.greensmartplanet.gsc.api.delivery;

import cn.greensmartplanet.gsc.api.constant.ServiceNameConstants;
import cn.greensmartplanet.gsc.api.delivery.dto.DeliveryStatsDTO;
import cn.greensmartplanet.gsc.api.delivery.factory.DeliveryStatsClientFallbackFactory;
import cn.greensmartplanet.gsc.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 交付域服务 Feign 客户端契约 — 工作台统计计数
 * <p>
 * 契约约定（调用方与提供方共同遵守）：
 * <ul>
 *   <li>提供方 gsc-service-delivery 的 InternalStatsController 实现本接口，URL/参数编译期强一致</li>
 *   <li>平台域工作台 BFF 经此契约聚合交付/超期计数，替代跨服务直查 delivery 表</li>
 *   <li>返回一律契约 DTO（字段为 long 基本类型，无需判空）</li>
 *   <li>降级（FallbackFactory）：统计数字是工作台纯展示性数据，delivery 不可用时
 *       返回全 0 + warn 日志，工作台照常渲染</li>
 * </ul>
 *
 * @author gsc
 */
@FeignClient(
        name = ServiceNameConstants.GSC_DELIVERY_SERVICE,
        path = "/internal/stats",
        contextId = "deliveryStatsClient",
        fallbackFactory = DeliveryStatsClientFallbackFactory.class)
public interface DeliveryStatsClient {

    /** 工作台交付/超期计数（仅采购/供应视角，超管不调） */
    @GetMapping("/dashboard")
    R<DeliveryStatsDTO> getDashboardStats(@RequestParam("tenantId") Long tenantId);
}
