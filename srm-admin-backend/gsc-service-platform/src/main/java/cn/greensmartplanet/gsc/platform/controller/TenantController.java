package cn.greensmartplanet.gsc.platform.controller;

import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.framework.audit.AuditLog;
import cn.greensmartplanet.gsc.framework.security.JwtUserDetails;
import cn.greensmartplanet.gsc.platform.dto.CreateTenantDTO;
import cn.greensmartplanet.gsc.platform.dto.UpdateTenantDTO;
import cn.greensmartplanet.gsc.platform.entity.Tenant;
import cn.greensmartplanet.gsc.platform.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 租户管理控制器
 * 超管用于开辟/管理入驻企业
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('*:*:*')")
@Tag(name = "租户管理", description = "租户的增删改查和审核操作")
public class TenantController {

    private final TenantService tenantService;

    /**
     * 分页查询租户列表（keyword模糊匹配编码+名称，status筛选）
     */
    @Operation(summary = "分页查询租户列表")
    @GetMapping("/tenants")
    public R<Map<String, Object>> page(@RequestParam(defaultValue = "1") int pageNum,
                                       @RequestParam(defaultValue = "10") int pageSize,
                                       @RequestParam(required = false) String tenantCode,
                                       @RequestParam(required = false) String companyName,
                                       @RequestParam(required = false) String status) {
        var page = tenantService.page(pageNum, pageSize, tenantCode, companyName, status);
        Map<String, Object> result = new HashMap<>();
        result.put("records", page.getRecords());
        result.put("total", page.getTotal());
        result.put("page", page.getCurrent());
        result.put("pageSize", page.getSize());
        return R.ok(result);
    }

    /**
     * 租户详情
     */
    @Operation(summary = "租户详情")
    @GetMapping("/tenants/{id}")
    public R<Tenant> detail(@PathVariable Long id) {
        return R.ok(tenantService.detail(id));
    }

    /**
     * 新增租户：校验编码唯一→入库→可选创建管理员账号
     */
    @Operation(summary = "新增租户")
    @AuditLog(module = "租户管理", operation = "CREATE", desc = "新增租户", targetType = "Tenant")
    @PostMapping("/tenants")
    public R<Void> create(@Valid @RequestBody CreateTenantDTO dto) {
        tenantService.create(dto);
        return R.ok();
    }

    /**
     * 编辑租户基本信息（编码不可改）
     */
    @Operation(summary = "编辑租户")
    @AuditLog(module = "租户管理", operation = "UPDATE", desc = "编辑租户", targetType = "Tenant")
    @PutMapping("/tenants/{id}")
    public R<Void> update(@PathVariable Long id,
                          @Valid @RequestBody UpdateTenantDTO dto) {
        tenantService.update(id, dto);
        return R.ok();
    }

    /**
     * 审核租户：更新状态（active/suspended/rejected）+ 审核意见
     */
    @Operation(summary = "审核租户")
    @AuditLog(module = "租户管理", operation = "UPDATE", desc = "审核租户", targetType = "Tenant")
    @PutMapping("/tenants/{id}/status")
    public R<Void> review(@PathVariable Long id,
                          @RequestBody Map<String, String> body,
                          @AuthenticationPrincipal JwtUserDetails user) {
        tenantService.review(id, body.get("status"), body.get("comment"), user.getUserId());
        return R.ok();
    }

    /**
     * 逻辑删除租户（平台租户不可删）
     */
    @Operation(summary = "删除租户")
    @AuditLog(module = "租户管理", operation = "DELETE", desc = "删除租户", targetType = "Tenant")
    @DeleteMapping("/tenants/{id}")
    public R<Void> delete(@PathVariable Long id) {
        tenantService.delete(id);
        return R.ok();
    }
}
