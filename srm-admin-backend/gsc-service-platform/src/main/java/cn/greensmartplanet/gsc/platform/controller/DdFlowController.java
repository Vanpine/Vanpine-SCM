package cn.greensmartplanet.gsc.platform.controller;

import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.framework.audit.AuditLog;
import cn.greensmartplanet.gsc.framework.security.JwtUserDetails;
import cn.greensmartplanet.gsc.platform.dto.AssignFirmDTO;
import cn.greensmartplanet.gsc.platform.dto.FirmInfo;
import cn.greensmartplanet.gsc.platform.dto.PayDTO;
import cn.greensmartplanet.gsc.platform.dto.SubmitFirmResultDTO;
import cn.greensmartplanet.gsc.platform.dto.UpdateChecklistItemDTO;
import cn.greensmartplanet.gsc.platform.service.ApplicationService;
import cn.greensmartplanet.gsc.platform.vo.CreatePaymentVO;
import cn.greensmartplanet.gsc.platform.vo.DdPackageCatalogVO;
import cn.greensmartplanet.gsc.platform.vo.DueDiligenceVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 尽调流程控制器（平台域） — 支付/事务所流程
 * <p>
 * ⚠️ 微服务拆分端点归属（阶段 7）：
 * <ul>
 *   <li><b>本服务</b>：dd-packages / pay / assign-firm / firm/* / confirm-dd / firms/search
 *       ——与申请状态、用户角色、邮件通知强耦合（user/role/mail/application 表）</li>
 *   <li><b>供应商域</b>：dd-info / dd-detail / generate-report
 *       ——操作供应商域自有表 due_diligence / dd_checklist（gsc-service-supplier）</li>
 * </ul>
 * 网关已按子路径精确路由（本控制器的端点先于通用 applications 路由匹配）。
 * URL 与旧单体一致，前端零感知。
 *
 * @author gsc
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "尽调管理", description = "尽调支付与事务所管理（平台域）")
public class DdFlowController {

    /** 入驻申请 + 尽调全流程 Service（事务所分配/核查清单等跨表协调） */
    private final ApplicationService applicationService;

    /**
     * 尽调套餐价目表（平台开通币种 + 独立标价，不做汇率折算）
     */
    @Operation(summary = "尽调套餐价目表")
    @GetMapping("/dd-packages")
    public R<DdPackageCatalogVO> packageCatalog() {
        return R.ok(DdPackageCatalogVO.fromCatalog());
    }

    /**
     * 创建尽调支付单：调中台下单，返回收款码。
     * 这里不改支付状态；paid 只由 {@code /api/pub/payment/callback} 写入。
     */
    @AuditLog(module = "尽调管理", operation = "UPDATE", desc = "创建支付单", targetType = "DueDiligence")
    @Operation(summary = "创建尽调支付单")
    @PutMapping("/applications/{id}/pay")
    public R<CreatePaymentVO> pay(@PathVariable Long id, @Valid @RequestBody PayDTO dto) {
        return R.ok(applicationService.createPayment(id, dto.getPackageType(), dto.getPaymentCurrency()));
    }

    /**
     * 收款码页轮询：只读 due_diligence.payment_status。
     * 点「我已付款」也走这里，不会因为点击就改成已支付。
     */
    @Operation(summary = "查询尽调支付状态")
    @GetMapping("/applications/{id}/pay-status")
    public R<java.util.Map<String, String>> payStatus(@PathVariable Long id) {
        String status = applicationService.getPaymentStatus(id);
        return R.ok(java.util.Map.of("paymentStatus", status == null ? "unpaid" : status));
    }

    /**
     * 分配事务所（超管操作）
     * <p>
     * 选择外部事务所 → 更新尽调记录 → 生成核查清单 → 创建事务所账号 → 发邮件通知。
     */
    @Operation(summary = "分配事务所")
    @AuditLog(module = "尽调管理", operation = "UPDATE", desc = "分配事务所", targetType = "DueDiligence")
    @PutMapping("/applications/{id}/assign-firm")
    public R<Void> assignFirm(@PathVariable Long id,
                              @Valid @RequestBody AssignFirmDTO dto) {
        applicationService.assignFirm(id, dto);
        return R.ok();
    }

    /**
     * 事务所工作台（FIRM 角色登录后获取已分配的企业尽调列表）
     * <p>
     * firmId 从 JWT 获取，Service 层校验非事务所角色拒绝访问。
     */
    @Operation(summary = "事务所工作台")
    @GetMapping("/firm/workbench")
    public R<List<DueDiligenceVO>> firmWorkbench(@AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(applicationService.getFirmWorkbench(user.getFirmId()));
    }

    /**
     * 事务所提交尽调结果
     * <p>
     * 提交工作备注 + 报告文件（url|filename 格式），状态设为 completed。
     */
    @AuditLog(module = "尽调管理", operation = "UPDATE", desc = "提交核查结果", targetType = "DueDiligence")
    @Operation(summary = "提交尽调结果")
    @PutMapping("/firm/submit/{applicationId}")
    public R<Void> submitFirmResult(@PathVariable Long applicationId,
                                    @RequestBody SubmitFirmResultDTO dto,
                                    @AuthenticationPrincipal JwtUserDetails user) {
        applicationService.submitFirmResult(applicationId, user.getFirmId(), dto.getProgress(), dto.getResultUrl());
        return R.ok();
    }

    /**
     * 管理员确认尽调通过（两步流程的第二步）
     * <p>
     * 事务所提交后状态为 submitted（待确认），管理员查看报告后确认通过，
     * 状态变为 completed + 升级用户角色 + 发邮件通知企业。
     */
    @AuditLog(module = "尽调管理", operation = "UPDATE", desc = "确认尽调通过", targetType = "DueDiligence")
    @Operation(summary = "管理员确认尽调通过")
    @PutMapping("/applications/{id}/confirm-dd")
    public R<Void> confirmDdComplete(@PathVariable Long id,
                                      @AuthenticationPrincipal JwtUserDetails user) {
        applicationService.confirmDdComplete(id, user.getUserId());
        return R.ok();
    }

    /**
     * 事务所退回尽调给企业补充材料
     * <p>
     * 事务所发现问题时调用，状态 assigned → returned，企业补充后自动回到 assigned。
     */
    @AuditLog(module = "尽调管理", operation = "UPDATE", desc = "退回尽调", targetType = "DueDiligence")
    @Operation(summary = "退回尽调补充材料")
    @PutMapping("/firm/return/{applicationId}")
    public R<Void> returnToEnterprise(@PathVariable Long applicationId,
                                       @RequestParam String reason,
                                       @AuthenticationPrincipal JwtUserDetails user) {
        applicationService.returnToEnterprise(applicationId, user.getFirmId(), reason);
        return R.ok();
    }

    /**
     * 查询事务所列表（从外部事务所 API 获取，支持关键字搜索）
     * <p>
     * 用于超管分配事务所时的下拉搜索。
     */
    @Operation(summary = "查询事务所列表")
    @GetMapping("/firms/search")
    public R<List<FirmInfo>> searchFirms(@RequestParam(required = false) String keyword) {
        return R.ok(applicationService.searchFirms(keyword));
    }

    /**
     * 更新单条核查清单项（事务所勾选/取消打勾 + 写备注）
     * <p>
     * firmId 从 JWT 获取，Service 层做三重权限校验（存在性 + 归属 + 未提交）。
     */
    @AuditLog(module = "尽调管理", operation = "UPDATE", desc = "更新核查清单", targetType = "DdChecklist")
    @Operation(summary = "更新核查清单项")
    @PutMapping("/firm/checklist/{itemId}")
    public R<Void> updateChecklistItem(@PathVariable Long itemId,
                                       @RequestBody UpdateChecklistItemDTO dto,
                                       @AuthenticationPrincipal JwtUserDetails user) {
        applicationService.updateChecklistItem(itemId, dto.getChecked(), dto.getRemark(), user.getFirmId());
        return R.ok();
    }
}
