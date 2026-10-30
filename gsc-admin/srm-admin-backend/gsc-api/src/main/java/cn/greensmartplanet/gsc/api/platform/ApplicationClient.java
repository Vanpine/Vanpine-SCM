package cn.greensmartplanet.gsc.api.platform;

import cn.greensmartplanet.gsc.api.constant.ServiceNameConstants;
import cn.greensmartplanet.gsc.api.platform.dto.ApplicationInfoDTO;
import cn.greensmartplanet.gsc.api.platform.factory.ApplicationClientFallbackFactory;
import cn.greensmartplanet.gsc.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

/**
 * 平台域服务 Feign 客户端契约 — 入驻申请信息查询
 * <p>
 * 契约约定（调用方与提供方共同遵守）：
 * <ul>
 *   <li>提供方 gsc-service-platform 的 InternalApplicationController 实现本接口，URL/参数编译期强一致</li>
 *   <li>供应商域用它替代跨服务直查 application 表（尽调兜底/画像初始化/匹配回填）</li>
 *   <li>降级（FallbackFactory）：抛 BizException 不静默通过——申请信息是尽调/画像的
 *       数据底座，平台服务不可用时宁可报错也不产出缺字段的业务结果</li>
 * </ul>
 *
 * @author gsc
 */
@FeignClient(
        name = ServiceNameConstants.GSC_PLATFORM_SERVICE,
        path = "/internal/application",
        contextId = "applicationClient",
        fallbackFactory = ApplicationClientFallbackFactory.class)
public interface ApplicationClient {

    /** 按申请ID查询入驻申请信息 */
    @GetMapping("/{id}")
    R<ApplicationInfoDTO> getById(@PathVariable("id") Long id);

    /** 按租户ID查询入驻申请信息（一个租户一条） */
    @GetMapping("/by-tenant/{tenantId}")
    R<ApplicationInfoDTO> getByTenantId(@PathVariable("tenantId") Long tenantId);

    /**
     * 批量按租户ID查申请映射（tenantId → applicationId）
     * 智能匹配批量回填尽调信息用，避免对候选池逐个 Feign 调用
     */
    @PostMapping("/ids-by-tenants")
    R<Map<Long, Long>> getIdsByTenantIds(@RequestBody List<Long> tenantIds);
}
