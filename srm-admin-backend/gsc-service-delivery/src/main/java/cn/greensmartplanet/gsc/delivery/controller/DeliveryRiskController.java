package cn.greensmartplanet.gsc.delivery.controller;

import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.framework.audit.AuditLog;
import cn.greensmartplanet.gsc.framework.security.JwtUserDetails;
import cn.greensmartplanet.gsc.delivery.dto.Create8dDTO;
import cn.greensmartplanet.gsc.delivery.dto.CreateLogisticsEventDTO;
import cn.greensmartplanet.gsc.delivery.dto.Submit8dDTO;
import cn.greensmartplanet.gsc.delivery.service.DeliveryRiskLifecycleService;
import cn.greensmartplanet.gsc.delivery.service.DeliveryRiskQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 可信交付 Controller
 * <p>
 * 承接物流事件录入、交付风险查询/详情。查询不记审计；
 * 录入物流事件为写操作，记 @AuditLog。
 * 薄壳委托两个细分服务：查询/详情走 Query，物流事件/8D 走 Lifecycle。
 *
 * @author gsc
 */
@Tag(name = "可信交付")
@RestController
@RequestMapping("/api/admin/delivery-risk")
@RequiredArgsConstructor
public class DeliveryRiskController {

    private final DeliveryRiskQueryService queryService;
    private final DeliveryRiskLifecycleService lifecycleService;

    @Operation(summary = "交付风险列表（采购方/供应方各自视角）")
    @GetMapping
    public R<Map<String, Object>> list(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "风险状态筛选") @RequestParam(required = false) String riskStatus,
            @Parameter(description = "风险等级筛选") @RequestParam(required = false) String riskLevel,
            @Parameter(description = "角色视角：buyer/supplier") @RequestParam(required = false) String role,
            @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(queryService.list(user.getTenantId(), pageNum, pageSize, riskStatus, riskLevel, role));
    }

    @Operation(summary = "供应保障驾驶舱：按交付单聚合活跃风险（一行一交付单）")
    @GetMapping("/dashboard")
    public R<Map<String, Object>> listDashboard(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "风险状态筛选") @RequestParam(required = false) String riskStatus,
            @Parameter(description = "风险等级筛选") @RequestParam(required = false) String riskLevel,
            @Parameter(description = "角色视角：buyer/supplier") @RequestParam(required = false) String role,
            @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(queryService.listDashboard(user.getTenantId(), pageNum, pageSize, riskStatus, riskLevel, role));
    }

    @Operation(summary = "交付风险详情（含物流事件时间线）")
    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id,
                                         @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(queryService.detail(id, user.getTenantId()));
    }

    @Operation(summary = "按交付单查未关闭风险（物流追踪页风险联动，无则 data.risk=null）")
    @GetMapping("/by-delivery/{deliveryId}")
    public R<Map<String, Object>> getActiveByDelivery(@PathVariable Long deliveryId,
                                                      @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(queryService.getActiveByDelivery(deliveryId, user.getTenantId()));
    }

    @AuditLog(module = "可信交付", operation = "CREATE", desc = "录入物流事件", targetType = "Delivery")
    @Operation(summary = "录入物流事件（平台内人工填报）")
    @PostMapping("/logistics-event")
    public R<Void> recordEvent(@Valid @RequestBody CreateLogisticsEventDTO dto,
                               @AuthenticationPrincipal JwtUserDetails user) {
        lifecycleService.recordEvent(user.getTenantId(), user.getUserId(), dto);
        return R.ok();
    }

    @AuditLog(module = "可信交付", operation = "CREATE", desc = "发起8D整改", targetType = "DeliveryRisk")
    @Operation(summary = "采购方发起 8D 整改（一风险一张）")
    @PostMapping("/{riskId}/8d")
    public R<Long> create8d(@PathVariable Long riskId,
                            @Valid @RequestBody Create8dDTO dto,
                            @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(lifecycleService.create8d(riskId, user.getTenantId(), user.getUserId(), dto));
    }

    @AuditLog(module = "可信交付", operation = "UPDATE", desc = "供应商提交8D报告", targetType = "DeliveryRisk")
    @Operation(summary = "供应商提交 8D 报告（D1-D8 全必填）")
    @PutMapping("/8d/{reportId}/submit")
    public R<Void> submit8d(@PathVariable Long reportId,
                            @Valid @RequestBody Submit8dDTO dto,
                            @AuthenticationPrincipal JwtUserDetails user) {
        lifecycleService.submit8d(reportId, user.getTenantId(), dto);
        return R.ok();
    }

    @AuditLog(module = "可信交付", operation = "UPDATE", desc = "采购方审核通过8D", targetType = "DeliveryRisk")
    @Operation(summary = "采购方审核通过 8D")
    @PutMapping("/8d/{reportId}/approve")
    public R<Void> approve8d(@PathVariable Long reportId,
                             @AuthenticationPrincipal JwtUserDetails user) {
        lifecycleService.approve8d(reportId, user.getTenantId(), user.getUserId());
        return R.ok();
    }

    @AuditLog(module = "可信交付", operation = "UPDATE", desc = "采购方打回8D", targetType = "DeliveryRisk")
    @Operation(summary = "采购方打回 8D（打回原因必填）")
    @PutMapping("/8d/{reportId}/reject")
    public R<Void> reject8d(@PathVariable Long reportId,
                            @Parameter(description = "打回原因") @RequestParam String reason,
                            @AuthenticationPrincipal JwtUserDetails user) {
        lifecycleService.reject8d(reportId, user.getTenantId(), user.getUserId(), reason);
        return R.ok();
    }
}
