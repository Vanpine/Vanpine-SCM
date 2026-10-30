package cn.greensmartplanet.gsc.api.delivery;

import cn.greensmartplanet.gsc.api.constant.ServiceNameConstants;
import cn.greensmartplanet.gsc.api.delivery.factory.DeliveryClientFallbackFactory;
import cn.greensmartplanet.gsc.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 交付域服务 Feign 客户端契约 — 合同终止/手动完成的"存在进行中交付单"校验
 * <p>
 * 契约约定（调用方与提供方共同遵守）：
 * <ul>
 *   <li>提供方 gsc-service-delivery 的 InternalDeliveryController 实现本接口，URL/参数编译期强一致</li>
 *   <li>服务不可用时降级（FallbackFactory）：返回 false（视为无进行中交付单），允许操作继续并告警
 *       ——迁移期兜底，避免交付服务抖动阻断交易域</li>
 * </ul>
 *
 * @author gsc
 */
@FeignClient(
        name = ServiceNameConstants.GSC_DELIVERY_SERVICE, //Feign 会根据服务名去 Nacos 找可用实例。
        path = "/internal/delivery",
        contextId = "deliveryClient",
        fallbackFactory = DeliveryClientFallbackFactory.class)
public interface DeliveryClient {

    /**
     * 合同是否存在进行中（非 accepted）交付单
     */
    @GetMapping("/active")
    R<Boolean> hasActive(@RequestParam("contractId") Long contractId);
}
