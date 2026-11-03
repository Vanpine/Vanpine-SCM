package cn.greensmartplanet.gsc.delivery.controller;

import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.delivery.dto.CompleteRecoveryActionDTO;
import cn.greensmartplanet.gsc.delivery.dto.CreateRecoveryActionDTO;
import cn.greensmartplanet.gsc.delivery.service.DeliveryRecoveryActionService;
import cn.greensmartplanet.gsc.delivery.vo.RecoveryActionVO;
import cn.greensmartplanet.gsc.framework.audit.AuditLog;
import cn.greensmartplanet.gsc.framework.security.JwtUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 交付补救工单 Controller
 * <p>
 * 创建/开始/完成工单、关闭风险均为写操作，记 @AuditLog；查询不记。
 *
 * @author gsc
 */
@Tag(name = "交付补救工单")
@RestController
@RequestMapping("/api/admin/delivery-risk")
@RequiredArgsConstructor
public class DeliveryRecoveryActionController {

    private final DeliveryRecoveryActionService actionService;

    @AuditLog(module = "可信交付", operation = "CREATE", desc = "创建补救工单", targetType = "Delivery")
    @Operation(summary = "创建补救工单")
    @PostMapping("/recovery-action")
    public R<RecoveryActionVO> create(@Valid @RequestBody CreateRecoveryActionDTO dto,
                                      @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(actionService.create(user.getTenantId(), user.getUserId(), dto));
    }

    @Operation(summary = "按风险查补救工单列表")
    @GetMapping("/{riskId}/recovery-actions")
    public R<List<RecoveryActionVO>> listByRisk(@PathVariable Long riskId,
                                                @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(actionService.listByRisk(riskId, user.getTenantId()));
    }

    @AuditLog(module = "可信交付", operation = "UPDATE", desc = "开始执行补救工单", targetType = "Delivery")
    @Operation(summary = "开始执行补救工单")
    @PutMapping("/recovery-action/{id}/start")
    public R<Void> start(@PathVariable Long id,
                         @AuthenticationPrincipal JwtUserDetails user) {
        actionService.start(id, user.getTenantId(), user.getUserId());
        return R.ok();
    }

    @AuditLog(module = "可信交付", operation = "UPDATE", desc = "完成补救工单", targetType = "Delivery")
    @Operation(summary = "完成补救工单（填回执/结果，自动缓解风险）")
    @PutMapping("/recovery-action/{id}/complete")
    public R<Void> complete(@PathVariable Long id,
                            @Valid @RequestBody CompleteRecoveryActionDTO dto,
                            @AuthenticationPrincipal JwtUserDetails user) {
        actionService.complete(id, user.getTenantId(), user.getUserId(), dto);
        return R.ok();
    }

    @AuditLog(module = "可信交付", operation = "UPDATE", desc = "关闭风险", targetType = "Delivery")
    @Operation(summary = "关闭风险（须有补救回执或风险已缓解）")
    @PutMapping("/{riskId}/close")
    public R<Void> closeRisk(@PathVariable Long riskId,
                             @AuthenticationPrincipal JwtUserDetails user) {
        actionService.closeRisk(riskId, user.getTenantId(), user.getUserId());
        return R.ok();
    }
}
