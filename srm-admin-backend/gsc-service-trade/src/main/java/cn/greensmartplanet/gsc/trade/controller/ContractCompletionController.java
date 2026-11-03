package cn.greensmartplanet.gsc.trade.controller;

import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.framework.audit.AuditLog;
import cn.greensmartplanet.gsc.framework.security.JwtUserDetails;
import cn.greensmartplanet.gsc.trade.service.ContractCompletionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 合同完成 Controller — 完成合同 / 终止合同
 * <p>
 * 基路径 /api/admin/procurement 不变，前端零感知。委托 ContractCompletionService。
 * 终态状态机：signed → completed / terminated。
 *
 * @author gsc
 */
@RestController
@RequestMapping("/api/admin/procurement")
@RequiredArgsConstructor
@Tag(name = "合同完成管理")
public class ContractCompletionController {

    private final ContractCompletionService contractCompletionService;

    @Operation(summary = "完成合同（signed → completed）")
    @AuditLog(module = "合同管理", operation = "UPDATE", desc = "完成合同", targetType = "Contract")
    @PutMapping("/contract/{id}/complete")
    public R<Void> completeContract(@PathVariable Long id,
                                     @AuthenticationPrincipal JwtUserDetails user) {
        contractCompletionService.complete(id, user.getTenantId(), user.getUserId());
        return R.ok();
    }

    @Operation(summary = "终止合同（signed → terminated）")
    @AuditLog(module = "合同管理", operation = "UPDATE", desc = "终止合同", targetType = "Contract")
    @PutMapping("/contract/{id}/terminate")
    public R<Void> terminateContract(@PathVariable Long id,
                                      @Parameter(description = "终止原因") @RequestParam String reason,
                                      @Parameter(description = "终止类型：mutual|supplier_breach|buyer_breach|force_majeure") @RequestParam(required = false) String terminateType,
                                      @AuthenticationPrincipal JwtUserDetails user) {
        contractCompletionService.terminate(id, user.getTenantId(), user.getUserId(), reason, terminateType);
        return R.ok();
    }
}
