package cn.greensmartplanet.gsc.supplier.controller;

import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.framework.audit.AuditLog;
import cn.greensmartplanet.gsc.framework.security.JwtUserDetails;
import cn.greensmartplanet.gsc.supplier.dto.EditProfileDTO;
import cn.greensmartplanet.gsc.supplier.service.ProfileLifecycleService;
import cn.greensmartplanet.gsc.supplier.service.ProfileQueryService;
import cn.greensmartplanet.gsc.supplier.vo.EnterpriseProfileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 企业画像 Controller（供应商域）— 查/改我的画像、查对方画像、供应商广场列表
 * <p>
 * 单资源控制器：只服务 enterprise_profile 画像资源。薄壳转发，业务逻辑在 Service 层。
 *
 * @author gsc
 */
@RestController
@RequestMapping("/api/admin/match/profile")
@RequiredArgsConstructor
@Tag(name = "企业画像")
public class ProfileController {

    private final ProfileQueryService profileQueryService;
    private final ProfileLifecycleService profileLifecycleService;

    /** 获取当前租户的企业画像（超管返回合成画像，逻辑在 Service） */
    @Operation(summary = "获取企业画像")
    @GetMapping("")
    public R<EnterpriseProfileVO> getProfile(@AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(profileQueryService.getMyProfile(user.getTenantId()));
    }

    /** 查对方企业画像+尽调信息（对接记录/需求广场查看） */
    @Operation(summary = "查对方企业画像")
    @GetMapping("/{tenantId}")
    public R<EnterpriseProfileVO> getPublicProfile(@PathVariable Long tenantId) {
        return R.ok(profileQueryService.getPublicProfile(tenantId));
    }

    /** 更新企业画像 */
    @AuditLog(module = "企业画像", operation = "UPDATE", desc = "更新企业画像", targetType = "EnterpriseProfile")
    @Operation(summary = "更新企业画像")
    @PutMapping("")
    public R<Void> updateProfile(@RequestBody EditProfileDTO dto,
                                 @AuthenticationPrincipal JwtUserDetails user) {
        profileLifecycleService.updateFromDto(user.getTenantId(), dto);
        return R.ok();
    }

    /** 需求广场-找供应商：分页查供应商列表 */
    @Operation(summary = "供应商列表")
    @GetMapping("/suppliers")
    public R<Map<String, Object>> listSuppliers(
            @Parameter(description = "关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(profileQueryService.listSuppliers(keyword, pageNum, pageSize));
    }
}
