package cn.greensmartplanet.gsc.trade.controller;

import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.framework.audit.AuditLog;
import cn.greensmartplanet.gsc.framework.security.JwtUserDetails;
import cn.greensmartplanet.gsc.trade.service.InquiryInviteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 询盘邀请 Controller — 供应商接受/拒绝邀请、采购方追加/重邀供应商
 * <p>
 * 基路径 /api/admin/procurement 不变，前端零感知。委托 InquiryInviteService。
 *
 * @author gsc
 */
@RestController
@RequestMapping("/api/admin/procurement")
@RequiredArgsConstructor
@Tag(name = "询盘邀请管理")
public class InquiryInviteController {

    private final InquiryInviteService inquiryInviteService;

    @Operation(summary = "供应商接受邀请")
    @AuditLog(module = "询盘管理", operation = "UPDATE", desc = "接受邀请", targetType = "InquiryInvite")
    @PutMapping("/inquiry/{id}/invite/{inviteId}/accept")
    public R<Void> acceptInvite(@PathVariable Long id,
                                 @PathVariable Long inviteId,
                                 @AuthenticationPrincipal JwtUserDetails user) {
        inquiryInviteService.acceptInvite(id, inviteId, user.getTenantId(), user.getUserId());
        return R.ok();
    }

    @Operation(summary = "供应商拒绝邀请")
    @AuditLog(module = "询盘管理", operation = "UPDATE", desc = "拒绝邀请", targetType = "InquiryInvite")
    @PutMapping("/inquiry/{id}/invite/{inviteId}/decline")
    public R<Void> declineInvite(@PathVariable Long id,
                                  @PathVariable Long inviteId,
                                  @Parameter(description = "拒绝原因（可选）") @RequestParam(required = false) String declineReason,
                                  @AuthenticationPrincipal JwtUserDetails user) {
        inquiryInviteService.declineInvite(id, inviteId, user.getTenantId(), user.getUserId(), declineReason);
        return R.ok();
    }

    @Operation(summary = "追加供应商到已有询盘")
    @AuditLog(module = "询盘管理", operation = "UPDATE", desc = "追加供应商", targetType = "Inquiry")
    @PutMapping("/inquiry/{id}/add-suppliers")
    public R<Void> addSuppliers(@PathVariable Long id,
                                 @RequestBody List<Long> supplierTenantIds,
                                 @AuthenticationPrincipal JwtUserDetails user) {
        inquiryInviteService.addSuppliers(id, user.getTenantId(), user.getUserId(), supplierTenantIds);
        return R.ok();
    }

    @Operation(summary = "重新邀请已拒绝的供应商")
    @AuditLog(module = "询盘管理", operation = "UPDATE", desc = "重新邀请", targetType = "InquiryInvite")
    @PutMapping("/inquiry/{id}/invite/{inviteId}/reinvite")
    public R<Void> reinvite(@PathVariable Long id,
                             @PathVariable Long inviteId,
                             @AuthenticationPrincipal JwtUserDetails user) {
        inquiryInviteService.reinvite(id, inviteId, user.getTenantId(), user.getUserId());
        return R.ok();
    }
}
