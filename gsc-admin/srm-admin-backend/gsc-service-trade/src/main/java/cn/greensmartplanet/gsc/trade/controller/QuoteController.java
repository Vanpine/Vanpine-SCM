package cn.greensmartplanet.gsc.trade.controller;

import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.framework.audit.AuditLog;
import cn.greensmartplanet.gsc.framework.security.JwtUserDetails;
import cn.greensmartplanet.gsc.trade.dto.CreateQuoteDTO;
import cn.greensmartplanet.gsc.trade.service.QuoteLifecycleService;
import cn.greensmartplanet.gsc.trade.service.QuoteQueryService;
import cn.greensmartplanet.gsc.trade.vo.QuoteVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 报价 Controller — 供应商提交/撤回报价、报价列表
 * <p>
 * 基路径 /api/admin/procurement 不变，前端零感知。委托 QuoteLifecycleService（写）+ QuoteQueryService（读）。
 *
 * @author gsc
 */
@RestController
@RequestMapping("/api/admin/procurement")
@RequiredArgsConstructor
@Tag(name = "报价管理")
public class QuoteController {

    private final QuoteLifecycleService quoteLifecycleService;
    private final QuoteQueryService quoteQueryService;

    @Operation(summary = "供应商提交报价")
    @AuditLog(module = "报价管理", operation = "CREATE", desc = "提交报价", targetType = "Quote")
    @PostMapping("/quote")
    public R<QuoteVO> submitQuote(@Valid @RequestBody CreateQuoteDTO dto,
                                   @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(quoteLifecycleService.submit(user.getTenantId(), user.getUserId(), dto));
    }

    @Operation(summary = "查看某询盘的报价列表（采购方全量 / 供应商只自己）")
    @GetMapping("/inquiry/{id}/quotes")
    public R<List<QuoteVO>> listQuotes(@PathVariable Long id,
                                        @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(quoteQueryService.listByInquiry(id, user.getTenantId()));
    }

    @Operation(summary = "撤回报价")
    @AuditLog(module = "报价管理", operation = "UPDATE", desc = "撤回报价", targetType = "Quote")
    @PutMapping("/quote/{id}/withdraw")
    public R<Void> withdrawQuote(@PathVariable Long id,
                                  @AuthenticationPrincipal JwtUserDetails user) {
        quoteLifecycleService.withdraw(id, user.getTenantId(), user.getUserId());
        return R.ok();
    }
}
