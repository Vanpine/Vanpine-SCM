package cn.greensmartplanet.gsc.api.supplier;

import cn.greensmartplanet.gsc.api.constant.ServiceNameConstants;
import cn.greensmartplanet.gsc.api.supplier.dto.PerformanceUpdateDTO;
import cn.greensmartplanet.gsc.api.supplier.dto.ProfileInfoDTO;
import cn.greensmartplanet.gsc.api.supplier.factory.ProfileClientFallbackFactory;
import cn.greensmartplanet.gsc.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

/**
 * 供应商域服务 Feign 客户端契约 — 企业画像信息查询
 * <p>
 * 契约约定（调用方与提供方共同遵守）：
 * <ul>
 *   <li>提供方 gsc-service-supplier 的 InternalProfileController 实现本接口，URL/参数编译期强一致</li>
 *   <li>平台/交易/交付域用它替代跨服务直查 enterprise_profile 表</li>
 *   <li>返回类型一律契约 DTO（禁止 Map），字段由编译期保证一致</li>
 *   <li>降级（FallbackFactory）：返回 null/空——画像用于通知文案、身份过滤等非数据底座场景，
 *       供应商服务不可用时降级放行（文案显示兜底名），不阻断主业务流程</li>
 * </ul>
 *
 * @author gsc
 */
@FeignClient(
        name = ServiceNameConstants.GSC_SUPPLIER_SERVICE,
        path = "/internal/profile",
        contextId = "profileClient",
        fallbackFactory = ProfileClientFallbackFactory.class)
public interface ProfileClient {

    /** 按租户ID查企业画像（不存在返回 data=null） */
    @GetMapping("/{tenantId}")
    R<ProfileInfoDTO> getByTenantId(@PathVariable("tenantId") Long tenantId);

    /** 批量按租户ID查画像（tenantId → ProfileInfoDTO，不存在的不在 map 中） */
    @PostMapping("/batch")
    R<Map<Long, ProfileInfoDTO>> getByTenantIds(@RequestBody List<Long> tenantIds);

    /** 交付域回写履约表现：仅更新 5 个履约字段（交付终态触发，避免全量覆盖画像） */
    @PutMapping("/performance")
    R<Void> updatePerformance(@RequestBody PerformanceUpdateDTO dto);

    /** 平台域尽调确认通过后回调：从入驻申请初始化企业画像（幂等 upsert） */
    @PostMapping("/init/{applicationId}")
    R<Void> initFromApplication(@PathVariable("applicationId") Long applicationId);
}
