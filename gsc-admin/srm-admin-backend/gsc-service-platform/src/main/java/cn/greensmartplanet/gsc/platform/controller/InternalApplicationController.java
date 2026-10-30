package cn.greensmartplanet.gsc.platform.controller;

import cn.greensmartplanet.gsc.api.platform.dto.ApplicationInfoDTO;
import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.platform.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 平台内部接口 — 入驻申请（仅服务间经内网服务发现 Feign 调用）
 * <p>
 * 网关已用 InternalBlockWebFilter 拒绝 /internal/** 外部访问（403），
 * 此处接口只对注册中心内的其他服务可见。
 * <p>
 * ⚠️ 路径一致性：本控制器路径与 gsc-api 的 ApplicationClient 契约保持一致
 * （契约接口为调用方声明，提供方不实现该接口，两端路径由本注释约定同步修改）。
 *
 * @author gsc
 */
@RestController
@RequestMapping("/internal/application")
@RequiredArgsConstructor
public class InternalApplicationController {

    private final ApplicationService applicationService;

    /** 按申请ID查询入驻申请信息（契约 DTO，仅返回供应商域需要的字段子集；不存在返回 data=null） */
    @GetMapping("/{id}")
    public R<ApplicationInfoDTO> getById(@PathVariable("id") Long id) {
        return R.ok(applicationService.getInfoById(id));
    }

    /** 按租户ID查询入驻申请信息（一个租户一条；不存在返回 data=null） */
    @GetMapping("/by-tenant/{tenantId}")
    public R<ApplicationInfoDTO> getByTenantId(@PathVariable("tenantId") Long tenantId) {
        return R.ok(applicationService.getInfoByTenantId(tenantId));
    }

    /** 批量按租户ID查申请映射（tenantId → applicationId），智能匹配批量回填用 */
    @PostMapping("/ids-by-tenants")
    public R<Map<Long, Long>> getIdsByTenantIds(@RequestBody List<Long> tenantIds) {
        return R.ok(applicationService.getIdsByTenantIds(tenantIds));
    }
}
