package cn.greensmartplanet.gsc.trade.controller;

import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.framework.security.JwtUserDetails;
import cn.greensmartplanet.gsc.trade.service.InquiryAiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 询盘 AI Controller — AI 比价分析 / 缓存读取 / 授标理由生成 / 询盘内容辅助
 * <p>
 * 基路径 /api/admin/procurement 不变，前端零感知。委托 InquiryAiService。
 *
 * @author gsc
 */
@RestController
@RequestMapping("/api/admin/procurement")
@RequiredArgsConstructor
@Tag(name = "询盘 AI 功能")
public class InquiryAiController {

    private final InquiryAiService inquiryAiService;

    @Operation(summary = "获取AI比价分析缓存")
    @GetMapping("/inquiry/{id}/ai-analysis")
    public R<Map<String, Object>> getAiAnalysis(@PathVariable Long id,
                                                  @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(inquiryAiService.getAiAnalysis(id, user.getTenantId()));
    }

    @Operation(summary = "AI 综合比价分析（执行并缓存）")
    @PostMapping("/inquiry/{id}/analyze-quotes")
    public R<Map<String, Object>> analyzeQuotes(@PathVariable Long id,
                                                  @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(inquiryAiService.analyzeQuotes(id, user.getTenantId()));
    }

    @Operation(summary = "AI 授标理由生成")
    @PostMapping("/inquiry/{id}/generate-award-reason")
    public R<String> generateAwardReason(@PathVariable Long id,
                                          @Parameter(description = "中标报价ID") @RequestParam Long quoteId,
                                          @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(inquiryAiService.generateAwardReason(id, quoteId, user.getTenantId()));
    }

    @Operation(summary = "AI 询盘内容辅助")
    @PostMapping("/inquiry/suggest-fields")
    public R<Map<String, Object>> suggestInquiryFields(
            @Parameter(description = "询盘标题") @RequestParam(required = false) String title,
            @Parameter(description = "采购品类") @RequestParam String category,
            @Parameter(description = "数量") @RequestParam(required = false) String quantity,
            @Parameter(description = "单位") @RequestParam(required = false) String unit) {
        return R.ok(inquiryAiService.suggestInquiryFields(title, category, quantity, unit));
    }
}
