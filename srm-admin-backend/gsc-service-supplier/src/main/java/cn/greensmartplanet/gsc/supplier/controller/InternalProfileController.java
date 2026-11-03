package cn.greensmartplanet.gsc.supplier.controller;

import cn.greensmartplanet.gsc.api.supplier.dto.PerformanceUpdateDTO;
import cn.greensmartplanet.gsc.api.supplier.dto.ProfileInfoDTO;
import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.supplier.service.ProfileLifecycleService;
import cn.greensmartplanet.gsc.supplier.service.ProfileQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 供应商内部接口 — 企业画像（仅服务间经内网服务发现 Feign 调用）
 * <p>
 * 网关已用 InternalBlockWebFilter 拒绝 /internal/** 外部访问（403），
 * 此处接口只对注册中心内的其他服务可见。
 * <p>
 * ⚠️ 路径一致性：本控制器路径与 gsc-api 的 ProfileClient 契约保持一致
 * （契约接口为调用方声明，提供方不实现该接口，两端路径由本注释约定同步修改）。
 *
 * @author gsc
 */
@RestController
@RequestMapping("/internal/profile")
@RequiredArgsConstructor
public class InternalProfileController {

    private final ProfileQueryService profileQueryService;
    private final ProfileLifecycleService profileLifecycleService;

    /** 按租户ID查询画像信息（契约 DTO，仅返回其他域需要的字段子集；不存在返回 data=null） */
    @GetMapping("/{tenantId}")
    public R<ProfileInfoDTO> getByTenantId(@PathVariable("tenantId") Long tenantId) {
        return R.ok(profileQueryService.getInfoByTenantId(tenantId));
    }

    /** 批量按租户ID查画像（tenantId → DTO，不存在的不在 map 中） */
    @PostMapping("/batch")
    public R<Map<Long, ProfileInfoDTO>> getByTenantIds(@RequestBody List<Long> tenantIds) {
        return R.ok(profileQueryService.getInfoByTenantIds(tenantIds));
    }

    /** 交付域回写履约表现：仅更新 5 个履约字段（交付终态触发） */
    @PutMapping("/performance")
    public R<Void> updatePerformance(@RequestBody PerformanceUpdateDTO dto) {
        profileLifecycleService.updatePerformance(dto);
        return R.ok();
    }

    /** 平台域尽调确认通过后回调：从入驻申请初始化企业画像（幂等 upsert） */
    @PostMapping("/init/{applicationId}")
    public R<Void> initFromApplication(@PathVariable("applicationId") Long applicationId) {
        profileLifecycleService.initFromApplication(applicationId);
        return R.ok();
    }
}
