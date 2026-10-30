package cn.greensmartplanet.gsc.trade.controller;

import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.framework.audit.AuditLog;
import cn.greensmartplanet.gsc.framework.security.JwtUserDetails;
import cn.greensmartplanet.gsc.trade.dto.CreateContractDTO;
import cn.greensmartplanet.gsc.trade.service.ContractLifecycleService;
import cn.greensmartplanet.gsc.trade.service.ContractPdfService;
import cn.greensmartplanet.gsc.trade.service.ContractQueryService;
import cn.greensmartplanet.gsc.trade.vo.ContractVO;
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
 * 合同 Controller — 合同主资源 + 生命周期（创建/编辑/发起/磋商）+ 查询 + PDF
 * <p>
 * 签署（ContractSignController）与完成/终止（ContractCompletionController）已按 8.2 规范拆为独立控制器。
 * 基路径 /api/admin/procurement 不变，前端零感知。
 *
 * @author gsc
 */
@RestController
@RequestMapping("/api/admin/procurement")
@RequiredArgsConstructor
@Tag(name = "合同管理")
public class ContractController {

    private final ContractLifecycleService contractLifecycleService;
    private final ContractQueryService contractQueryService;
    private final ContractPdfService contractPdfService;

    // ==================== 合同主资源 + 生命周期 ====================

    @Operation(summary = "创建合同")
    @AuditLog(module = "合同管理", operation = "CREATE", desc = "创建合同", targetType = "Contract")
    @PostMapping("/contract")
    public R<ContractVO> createContract(@Valid @RequestBody CreateContractDTO dto,
                                         @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(contractLifecycleService.create(user.getTenantId(), user.getUserId(), dto));
    }

    @Operation(summary = "合同列表（采购方/供应方）")
    @GetMapping("/contract")
    public R<Map<String, Object>> listContracts(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "状态筛选") @RequestParam(required = false) String status,
            @Parameter(description = "关键词搜索") @RequestParam(required = false) String keyword,
            @Parameter(description = "角色视角：buyer/supplier") @RequestParam(required = false) String role,
            @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(contractQueryService.list(user.getTenantId(), pageNum, pageSize, status, keyword, role));
    }

    @Operation(summary = "合同详情")
    @GetMapping("/contract/{id}")
    public R<ContractVO> getContractDetail(@PathVariable Long id,
                                            @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(contractQueryService.getDetail(id, user.getTenantId()));
    }

    @Operation(summary = "编辑合同（仅 draft）")
    @AuditLog(module = "合同管理", operation = "UPDATE", desc = "编辑合同", targetType = "Contract")
    @PutMapping("/contract/{id}")
    public R<Void> updateContract(@PathVariable Long id,
                                   @Valid @RequestBody CreateContractDTO dto,
                                   @AuthenticationPrincipal JwtUserDetails user) {
        contractLifecycleService.update(id, user.getTenantId(), user.getUserId(), dto);
        return R.ok();
    }

    @Operation(summary = "发起合同（draft → pending_supplier，采购方）")
    @AuditLog(module = "合同管理", operation = "UPDATE", desc = "发起合同", targetType = "Contract")
    @PutMapping("/contract/{id}/submit")
    public R<Void> submitContract(@PathVariable Long id,
                                  @AuthenticationPrincipal JwtUserDetails user) {
        contractLifecycleService.submit(id, user.getTenantId(), user.getUserId());
        return R.ok();
    }

    @Operation(summary = "供应商确认条款（pending_supplier 记确认标记，状态不变）")
    @AuditLog(module = "合同管理", operation = "UPDATE", desc = "供应商确认合同条款", targetType = "Contract")
    @PutMapping("/contract/{id}/confirm")
    public R<Void> confirmContract(@PathVariable Long id,
                                   @AuthenticationPrincipal JwtUserDetails user) {
        contractLifecycleService.confirm(id, user.getTenantId(), user.getUserId());
        return R.ok();
    }

    @Operation(summary = "供应商拒绝条款（pending_supplier → draft，必填理由）")
    @AuditLog(module = "合同管理", operation = "UPDATE", desc = "供应商拒绝合同", targetType = "Contract")
    @PutMapping("/contract/{id}/decline")
    public R<Void> declineContract(@PathVariable Long id,
                                   @Parameter(description = "拒绝理由") @RequestParam String reason,
                                   @AuthenticationPrincipal JwtUserDetails user) {
        contractLifecycleService.decline(id, user.getTenantId(), user.getUserId(), reason);
        return R.ok();
    }

    @Operation(summary = "采购方撤回/修订（pending_supplier → draft，清确认标记与拒绝理由）")
    @AuditLog(module = "合同管理", operation = "UPDATE", desc = "采购方撤回合同", targetType = "Contract")
    @PutMapping("/contract/{id}/withdraw")
    public R<Void> withdrawContract(@PathVariable Long id,
                                    @AuthenticationPrincipal JwtUserDetails user) {
        contractLifecycleService.withdraw(id, user.getTenantId(), user.getUserId());
        return R.ok();
    }

    @Operation(summary = "生成合同 PDF（标准采购合同模板）")
    @AuditLog(module = "合同管理", operation = "CREATE", desc = "生成合同PDF", targetType = "Contract")
    @PostMapping("/contract/{id}/pdf")
    public R<Map<String, String>> generateContractPdf(@PathVariable Long id,
                                                      @AuthenticationPrincipal JwtUserDetails user) {
        // 归属校验（仅合同双方可生成）
        contractQueryService.getDetail(id, user.getTenantId());
        return R.ok(contractPdfService.generate(id));
    }

    @Operation(summary = "查某询盘下已有活跃合同的报价ID列表（前端禁选用）")
    @GetMapping("/contract/active-quotes")
    public R<List<Long>> getActiveContractQuoteIds(
            @Parameter(description = "询盘ID") @RequestParam Long inquiryId) {
        return R.ok(contractQueryService.getActiveContractQuoteIds(inquiryId));
    }
}
