package cn.greensmartplanet.gsc.trade.controller;

import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.framework.audit.AuditLog;
import cn.greensmartplanet.gsc.framework.security.JwtUserDetails;
import cn.greensmartplanet.gsc.trade.service.ContractSignService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 合同签署 Controller — 上传盖章件 / 确认盖章件签署
 * <p>
 * 基路径 /api/admin/procurement 不变，前端零感知。委托 ContractSignService。
 * 签署阶段状态机：pending_supplier → pending_confirm → signed。
 *
 * @author gsc
 */
@RestController
@RequestMapping("/api/admin/procurement")
@RequiredArgsConstructor
@Tag(name = "合同签署管理")
public class ContractSignController {

    private final ContractSignService contractSignService;

    @Operation(summary = "上传盖章件（任一方，pending_supplier → pending_confirm）")
    @AuditLog(module = "合同管理", operation = "UPDATE", desc = "上传合同盖章件", targetType = "Contract")
    @PutMapping("/contract/{id}/upload-sign")
    public R<Void> uploadSignContract(@PathVariable Long id,
                                      @Parameter(description = "盖章件的 OSS object key（上传接口返回的 key 字段）")
                                      @RequestParam String key,
                                      @AuthenticationPrincipal JwtUserDetails user) {
        contractSignService.uploadSign(id, user.getTenantId(), user.getUserId(), key);
        return R.ok();
    }

    @Operation(summary = "确认盖章件签署（对方，pending_confirm → signed）")
    @AuditLog(module = "合同管理", operation = "UPDATE", desc = "确认盖章件签署合同", targetType = "Contract")
    @PutMapping("/contract/{id}/confirm-sign")
    public R<Void> confirmSignContract(@PathVariable Long id,
                                       @AuthenticationPrincipal JwtUserDetails user) {
        contractSignService.confirmSign(id, user.getTenantId(), user.getUserId());
        return R.ok();
    }

    @Operation(summary = "确认盖章件签署（sign 旧路径兼容）")
    @AuditLog(module = "合同管理", operation = "UPDATE", desc = "确认盖章件签署合同", targetType = "Contract")
    @PutMapping("/contract/{id}/sign")
    public R<Void> signContract(@PathVariable Long id,
                                 @AuthenticationPrincipal JwtUserDetails user) {
        contractSignService.sign(id, user.getTenantId(), user.getUserId());
        return R.ok();
    }
}
