package cn.greensmartplanet.gsc.api.supplier;

import cn.greensmartplanet.gsc.api.constant.ServiceNameConstants;
import cn.greensmartplanet.gsc.api.supplier.dto.MyConnectionStatusDTO;
import cn.greensmartplanet.gsc.api.supplier.factory.ConnectionClientFallbackFactory;
import cn.greensmartplanet.gsc.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 供应商域服务 Feign 客户端契约 — 对接记录查询
 * <p>
 * 契约约定（调用方与提供方共同遵守）：
 * <ul>
 *   <li>提供方 gsc-service-supplier 的 InternalConnectionController 实现本接口，URL/参数编译期强一致</li>
 *   <li>交易域需求列表/广场用它替代跨服务直查 connection 表（对接计数与我的对接状态回填）</li>
 *   <li>返回类型一律契约 DTO（禁止 Map），字段由编译期保证一致</li>
 *   <li>降级（FallbackFactory）：返回空/0——对接计数是列表展示性字段，
 *       供应商域不可用时显示 0，不阻断需求列表主流程</li>
 * </ul>
 *
 * @author gsc
 */
@FeignClient(
        name = ServiceNameConstants.GSC_SUPPLIER_SERVICE,
        path = "/internal/connection",
        contextId = "connectionClient",
        fallbackFactory = ConnectionClientFallbackFactory.class)
public interface ConnectionClient {

    /**
     * 按需求ID批量查已接受对接数（requirementId → count，无记录的返回 0）
     */
    @GetMapping("/counts")
    R<Map<Long, Long>> countGroupByRequirementIds(@RequestParam("buyerTenantId") Long buyerTenantId,
                                                  @RequestParam("requirementIds") List<Long> requirementIds);

    /**
     * 查某租户在多个需求下的对接状态（requirementId → DTO；无记录返回空 map）
     */
    @GetMapping("/my-status")
    R<Map<Long, MyConnectionStatusDTO>> getMyConnectionStatus(@RequestParam("tenantId") Long tenantId,
                                                              @RequestParam("requirementIds") List<Long> requirementIds);
}
