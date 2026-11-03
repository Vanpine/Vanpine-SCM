package cn.greensmartplanet.gsc.trade.controller;

import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.framework.audit.AuditLog;
import cn.greensmartplanet.gsc.framework.security.JwtUserDetails;
import cn.greensmartplanet.gsc.trade.dto.AwardDTO;
import cn.greensmartplanet.gsc.trade.service.InquiryAwardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 询盘授标 Controller — 选定中标 / 主动完成授标
 * <p>
 * 基路径 /api/admin/procurement 不变，前端零感知。委托 InquiryAwardService。
 *
 * @author gsc
 */
@RestController
@RequestMapping("/api/admin/procurement")
@RequiredArgsConstructor
@Tag(name = "询盘授标管理")
public class InquiryAwardController {

    private final InquiryAwardService inquiryAwardService;

    @Operation(summary = "选定中标（支持分量授标，未满量保持报价中）")
    @AuditLog(module = "询盘管理", operation = "UPDATE", desc = "选定中标", targetType = "Inquiry")
    @PutMapping("/inquiry/{id}/award")
    public R<Map<String, Object>> awardInquiry(@PathVariable Long id,
                                 @Parameter(description = "授标请求") @RequestBody @Valid AwardDTO dto,
                                 @Parameter(description = "中标理由") @RequestParam(required = false) String awardReason,
                                 @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(inquiryAwardService.award(id, user.getTenantId(), user.getUserId(), dto.getItems(), awardReason));
    }

    @Operation(summary = "完成授标（数量未满也结束询盘）")
    @AuditLog(module = "询盘管理", operation = "UPDATE", desc = "完成授标", targetType = "Inquiry")
    @PutMapping("/inquiry/{id}/complete-award")
    public R<Void> completeAward(@PathVariable Long id,
                                 @AuthenticationPrincipal JwtUserDetails user) {
        inquiryAwardService.completeAward(id, user.getTenantId(), user.getUserId());
        return R.ok();
    }
}
