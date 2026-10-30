package cn.greensmartplanet.gsc.trade.controller;

import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.framework.audit.AuditLog;
import cn.greensmartplanet.gsc.framework.security.JwtUserDetails;
import cn.greensmartplanet.gsc.trade.dto.CreateInquiryDTO;
import cn.greensmartplanet.gsc.trade.service.InquiryLifecycleService;
import cn.greensmartplanet.gsc.trade.service.InquiryQueryService;
import cn.greensmartplanet.gsc.trade.vo.InquiryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 询盘 Controller — 询盘资源主体（创建/编辑/关闭/取消/延长截止/列表/详情）
 * <p>
 * 基路径 /api/admin/procurement 不变，前端零感知。委托 InquiryLifecycleService（写）+ InquiryQueryService（读）。
 *
 * @author gsc
 */
@RestController
@RequestMapping("/api/admin/procurement")
@RequiredArgsConstructor
@Tag(name = "询盘管理")
public class InquiryController {

    private final InquiryLifecycleService inquiryLifecycleService;
    private final InquiryQueryService inquiryQueryService;

    @Operation(summary = "创建询盘 + 受邀供应商")
    @AuditLog(module = "询盘管理", operation = "CREATE", desc = "创建询盘", targetType = "Inquiry")
    @PostMapping("/inquiry")
    public R<InquiryVO> createInquiry(@Valid @RequestBody CreateInquiryDTO dto,
                                       @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(inquiryLifecycleService.create(user.getTenantId(), user.getUserId(), dto));
    }

    @Operation(summary = "询盘列表（采购商看自己 / 供应商看受邀）")
    @GetMapping("/inquiry")
    public R<Map<String, Object>> listInquiries(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "状态筛选") @RequestParam(required = false) String status,
            @Parameter(description = "关键词搜索") @RequestParam(required = false) String keyword,
            @Parameter(description = "角色视角：buyer/supplier") @RequestParam(required = false) String role,
            @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(inquiryQueryService.list(user.getTenantId(), pageNum, pageSize, status, keyword, role));
    }

    @Operation(summary = "询盘详情（含受邀名单 + 报价列表）")
    @GetMapping("/inquiry/{id}")
    public R<InquiryVO> getInquiryDetail(@PathVariable Long id,
                                          @Parameter(description = "角色视角") @RequestParam(required = false) String role,
                                          @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(inquiryQueryService.getDetail(id, user.getTenantId(), role));
    }

    @Operation(summary = "编辑询盘（仅 open 状态）")
    @AuditLog(module = "询盘管理", operation = "UPDATE", desc = "编辑询盘", targetType = "Inquiry")
    @PutMapping("/inquiry/{id}")
    public R<Void> updateInquiry(@PathVariable Long id,
                                  @Valid @RequestBody CreateInquiryDTO dto,
                                  @AuthenticationPrincipal JwtUserDetails user) {
        inquiryLifecycleService.update(id, user.getTenantId(), user.getUserId(), dto);
        return R.ok();
    }

    @Operation(summary = "关闭询盘（open/quoting/expired，无授标）")
    @AuditLog(module = "询盘管理", operation = "UPDATE", desc = "关闭询盘", targetType = "Inquiry")
    @PutMapping("/inquiry/{id}/close")
    public R<Void> closeInquiry(@PathVariable Long id,
                                 @AuthenticationPrincipal JwtUserDetails user) {
        inquiryLifecycleService.close(id, user.getTenantId(), user.getUserId());
        return R.ok();
    }

    @Operation(summary = "取消询盘（仅 open 状态）")
    @AuditLog(module = "询盘管理", operation = "DELETE", desc = "取消询盘", targetType = "Inquiry")
    @PutMapping("/inquiry/{id}/cancel")
    public R<Void> cancelInquiry(@PathVariable Long id,
                                  @AuthenticationPrincipal JwtUserDetails user) {
        inquiryLifecycleService.cancel(id, user.getTenantId(), user.getUserId());
        return R.ok();
    }

    @Operation(summary = "延长报价截止时间")
    @AuditLog(module = "询盘管理", operation = "UPDATE", desc = "延长报价截止", targetType = "Inquiry")
    @PutMapping("/inquiry/{id}/extend-deadline")
    public R<Void> extendDeadline(@PathVariable Long id,
                                   @Parameter(description = "新截止时间") @RequestParam String newDeadline,
                                   @AuthenticationPrincipal JwtUserDetails user) {
        inquiryLifecycleService.extendDeadline(id, user.getTenantId(), user.getUserId(), java.time.LocalDateTime.parse(newDeadline));
        return R.ok();
    }

    @Operation(summary = "批量查哪些对接已有活跃询盘")
    @GetMapping("/inquiry/active-check")
    public R<Map<Long, String>> activeCheck(
            @Parameter(description = "对接记录ID列表，逗号分隔") @RequestParam List<Long> connectionIds,
            @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(inquiryQueryService.getActiveInquiryMap(user.getTenantId(), connectionIds));
    }

    @Operation(summary = "查当前采购商在该需求下已授标过的供应商（前端禁选用）")
    @GetMapping("/inquiry/invited-suppliers")
    public R<List<Long>> getInvitedSuppliers(
            @Parameter(description = "采购需求ID，按需求维度判定已授标") @RequestParam(required = false) Long requirementId,
            @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(inquiryQueryService.getInvitedSupplierIds(user.getTenantId(), requirementId));
    }

    @Operation(summary = "查该需求下进行中询盘已邀请的供应商（前端禁选用）")
    @GetMapping("/inquiry/active-inquiry-suppliers")
    public R<List<Long>> getActiveInquirySuppliers(
            @Parameter(description = "采购需求ID") @RequestParam(required = false) Long requirementId,
            @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(inquiryQueryService.getActiveInquirySupplierIds(user.getTenantId(), requirementId));
    }
}
