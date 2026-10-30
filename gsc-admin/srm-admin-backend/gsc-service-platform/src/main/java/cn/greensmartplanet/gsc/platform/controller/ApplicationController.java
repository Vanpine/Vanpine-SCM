package cn.greensmartplanet.gsc.platform.controller;

import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.framework.audit.AuditLog;
import cn.greensmartplanet.gsc.framework.security.JwtUserDetails;
import cn.greensmartplanet.gsc.platform.dto.ReviewApplicationDTO;
import cn.greensmartplanet.gsc.platform.service.ApplicationService;
import cn.greensmartplanet.gsc.platform.vo.ApplicationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 入驻申请控制器 — 超管审核企业入驻申请
 * <p>
 * 端点：
 * <ul>
 *   <li><b>入驻审核</b>：分页查询、详情、审核（通过+驳回）</li>
 *   <li><b>用户入口</b>：当前租户的入驻申请</li>
 * </ul>
 * <p>
 * Controller 层只做参数接收和委托，所有业务逻辑在 Service 层。
 *
 * @author gsc
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "入驻审核", description = "企业入驻申请的查询和审核操作")
public class ApplicationController {

    /** 入驻申请 Service（分页、详情、审核、提交） */
    private final ApplicationService applicationService;

    // ==================== 入驻审核 ====================

    /**
     * 分页查询入驻申请列表（超管后台）
     * <p>
     * keyword 模糊匹配公司名/联系人/邮箱，status 精确筛选审核状态。
     */
    @Operation(summary = "分页查询入驻申请列表")
    @GetMapping("/applications")
    @PreAuthorize("hasAnyAuthority('*:*:*', 'admin', 'application')")
    public R<Map<String, Object>> page(@RequestParam(defaultValue = "1") int pageNum,
                                       @RequestParam(defaultValue = "10") int pageSize,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) String companyName,
                                       @RequestParam(required = false) String contactName,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false) String applicantRole,
                                       @RequestParam(required = false) String packageType,
                                       @RequestParam(required = false) String paymentStatus,
                                       @RequestParam(required = false) String ddFirmStatus,
                                       @RequestParam(required = false) String ddReportStatus) {
        return R.ok(applicationService.page(pageNum, pageSize, keyword, companyName, contactName, status,
                applicantRole, packageType, paymentStatus, ddFirmStatus, ddReportStatus));
    }

    /**
     * 入驻申请详情（超管查看，含尽调数据）
     */
    @Operation(summary = "入驻申请详情")
    @GetMapping("/applications/{id}")
    @PreAuthorize("hasAnyAuthority('*:*:*', 'admin', 'application')")
    public R<ApplicationVO> detail(@PathVariable Long id) {
        return R.ok(applicationService.detail(id));
    }

    /**
     * 审核入驻申请（超管操作，核心端点）
     * <p>
     * 通过：创建租户 + 管理员账号 + 尽调记录 + 发送通知邮件
     * 驳回：记录审核意见 + 发送驳回通知
     */
    @Operation(summary = "审核入驻申请")
    @AuditLog(module = "入驻审核", operation = "UPDATE", desc = "审核入驻申请", targetType = "Application")
    @PutMapping("/applications/{id}/review")
    @PreAuthorize("hasAnyAuthority('*:*:*', 'admin', 'application')")
    public R<Void> review(@PathVariable Long id,
                          @Valid @RequestBody ReviewApplicationDTO dto,
                          @AuthenticationPrincipal JwtUserDetails user) {
        applicationService.review(id, dto, user.getUserId());
        return R.ok();
    }

    // ==================== 用户入口 ====================

    /**
     * 当前租户的入驻申请详情（尽调页面用）
     * <p>
     * 登录用户通过 JWT 中的 tenantId 查到自己企业的申请信息。
     */
    @Operation(summary = "当前租户的入驻申请")
    @GetMapping("/applications/my-application")
    @PreAuthorize("isAuthenticated()")
    public R<ApplicationVO> myApplication(@AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(applicationService.getByTenantId(user.getTenantId()));
    }
}
