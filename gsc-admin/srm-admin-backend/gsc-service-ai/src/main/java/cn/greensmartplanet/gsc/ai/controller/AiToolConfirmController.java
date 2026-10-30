package cn.greensmartplanet.gsc.ai.controller;

import cn.greensmartplanet.gsc.ai.dto.AiToolConfirmRequest;
import cn.greensmartplanet.gsc.ai.dto.AiToolConfirmResult;
import cn.greensmartplanet.gsc.ai.service.AiToolService;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.framework.audit.AuditLog;
import cn.greensmartplanet.gsc.framework.security.JwtUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 工具二次确认控制器 — 前端「确认/取消」按钮的确认落库入口
 * <p>
 * 与 AiToolController（/internal/ai/tool，Python 回调）分离：
 * 本控制器挂 /api/admin/ai/**，只对登录用户开放，由登录 JWT 鉴权。
 * 真正的数据变更在此记 @AuditLog（targetType=SupplierNote），操作者即当前登录用户。
 * 模型只能生成提案，永远无法直接落库——落库动作只在用户点确认按钮后由此触发。
 *
 * @author gsc
 */
@RestController
@RequestMapping("/api/admin/ai")
@RequiredArgsConstructor
@Tag(name = "AI 工具二次确认", description = "写操作的用户确认落库")
public class AiToolConfirmController {

    private final AiToolService aiToolService;

    /**
     * 确认并执行一个写操作提案（前端确认按钮触发）。
     *
     * @param request 提案ID
     * @param user    当前登录用户（归属校验 + 审计归属）
     * @return 最终展示文案（前端据此覆盖占位气泡）
     */
    @AuditLog(module = "AI工具", operation = "CREATE", desc = "确认供应商备注", targetType = "SupplierNote")
    @Operation(summary = "确认 AI 工具写操作（二次确认）")
    @PostMapping("/tool/confirm")
    public R<AiToolConfirmResult> confirm(@Valid @RequestBody AiToolConfirmRequest request,
                             @AuthenticationPrincipal JwtUserDetails user) {
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return R.ok(aiToolService.confirm(request.getProposalId(), user.getTenantId(), user.getUserId()));
    }

    /**
     * 确认并执行一个询盘写操作提案（修改/关闭/取消询盘，前端确认按钮触发）。
     * <p>
     * 与 /tool/confirm 分离：询盘三操作审计语义是 UPDATE（修改/状态流转），
     * 而供应商备注是 CREATE，二者 @AuditLog 的 operation/targetType 不同，须拆端点静态区分。
     *
     * @param request 提案ID
     * @param user    当前登录用户（归属校验 + 审计归属）
     * @return 最终展示文案（前端据此覆盖占位气泡）
     */
    @AuditLog(module = "AI工具", operation = "UPDATE", desc = "确认询盘操作", targetType = "Inquiry")
    @Operation(summary = "确认 AI 询盘写操作（二次确认）")
    @PostMapping("/tool/confirm-inquiry")
    public R<AiToolConfirmResult> confirmInquiry(@Valid @RequestBody AiToolConfirmRequest request,
                                    @AuthenticationPrincipal JwtUserDetails user) {
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return R.ok(aiToolService.confirm(request.getProposalId(), user.getTenantId(), user.getUserId()));
    }

    /**
     * 取消一个写操作提案（前端取消按钮触发，不落库）。
     * <p>
     * 只消费 Redis 提案、不产生业务变更，无 @AuditLog（无数据变更）；
     * 把气泡占位覆盖成「已取消」文案，历史里清晰记录「未做任何变更」。
     *
     * @param request 提案ID
     * @param user    当前登录用户（归属校验）
     * @return 最终展示文案（前端据此覆盖占位气泡）
     */
    @Operation(summary = "取消 AI 工具写操作（二次确认取消）")
    @PostMapping("/tool/cancel")
    public R<AiToolConfirmResult> cancel(@Valid @RequestBody AiToolConfirmRequest request,
                                         @AuthenticationPrincipal JwtUserDetails user) {
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return R.ok(aiToolService.cancel(request.getProposalId(), user.getTenantId(), user.getUserId()));
    }
}
