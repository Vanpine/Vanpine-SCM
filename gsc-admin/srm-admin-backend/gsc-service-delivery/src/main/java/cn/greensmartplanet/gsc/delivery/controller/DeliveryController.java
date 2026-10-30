package cn.greensmartplanet.gsc.delivery.controller;

import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.framework.audit.AuditLog;
import cn.greensmartplanet.gsc.framework.security.JwtUserDetails;
import cn.greensmartplanet.gsc.delivery.dto.CorrectTrackingDTO;
import cn.greensmartplanet.gsc.delivery.dto.CreateDeliveryDTO;
import cn.greensmartplanet.gsc.delivery.dto.PartialRejectDTO;
import cn.greensmartplanet.gsc.delivery.dto.PromiseDateDTO;
import cn.greensmartplanet.gsc.delivery.dto.ReceiveDeliveryDTO;
import cn.greensmartplanet.gsc.delivery.dto.RejectDeliveryDTO;
import cn.greensmartplanet.gsc.delivery.dto.ReturnLogisticsDTO;
import cn.greensmartplanet.gsc.delivery.dto.ShipDeliveryDTO;
import cn.greensmartplanet.gsc.delivery.projection.CreatableContractRow;
import cn.greensmartplanet.gsc.delivery.service.DeliveryLifecycleService;
import cn.greensmartplanet.gsc.delivery.service.DeliveryPromiseDateService;
import cn.greensmartplanet.gsc.delivery.service.DeliveryQueryService;
import cn.greensmartplanet.gsc.delivery.service.DeliveryReturnService;
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
 * 交付管理 Controller
 * <p>
 * 承接合同签署后的履约环节：供应商发货、采购商收货/验收。
 * 查询（GET）不记审计；发货/收货/验收/退回/更正运单为写操作，按规范记 @AuditLog。
 * 薄壳委托 4 个细分服务：查询/生命周期/退货状态机/承诺交期。
 *
 * @author gsc
 */
@Tag(name = "交付管理")
@RestController
@RequestMapping("/api/admin/delivery")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryQueryService queryService;
    private final DeliveryLifecycleService lifecycleService;
    private final DeliveryReturnService returnService;
    private final DeliveryPromiseDateService promiseDateService;

    @Operation(summary = "交付单列表（采购方/供应方各自视角）")
    @GetMapping
    public R<Map<String, Object>> list(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "状态筛选，支持逗号分隔") @RequestParam(required = false) String status,
            @Parameter(description = "关键词（交付单号/运单号）") @RequestParam(required = false) String keyword,
            @Parameter(description = "角色视角：buyer/supplier") @RequestParam(required = false) String role,
            @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(queryService.list(user.getTenantId(), pageNum, pageSize, status, keyword, role));
    }

    @Operation(summary = "可创建交付单的合同列表（供应方视角，已签署且有未分配数量）")
    @GetMapping("/creatable-contracts")
    public R<List<CreatableContractRow>> creatableContracts(@AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(queryService.listCreatableContracts(user.getTenantId()));
    }

    @Operation(summary = "交付单详情（含履约时间线）")
    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id,
                                         @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(queryService.getDetail(id, user.getTenantId()));
    }

    @AuditLog(module = "交付管理", operation = "CREATE", desc = "手动创建交付单", targetType = "Delivery")
    @Operation(summary = "供应商手动创建交付单（主动分批）")
    @PostMapping
    public R<Long> create(@Valid @RequestBody CreateDeliveryDTO dto,
                          @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(lifecycleService.createManually(user.getTenantId(), user.getUserId(), dto));
    }

    @AuditLog(module = "交付管理", operation = "DELETE", desc = "删除交付单", targetType = "Delivery")
    @Operation(summary = "供应商删除交付单（仅待发货）")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id,
                          @AuthenticationPrincipal JwtUserDetails user) {
        lifecycleService.delete(id, user.getTenantId(), user.getUserId());
        return R.ok();
    }

    @AuditLog(module = "交付管理", operation = "UPDATE", desc = "交付发货", targetType = "Delivery")
    @Operation(summary = "供应商发货")
    @PutMapping("/{id}/ship")
    public R<Void> ship(@PathVariable Long id,
                        @Valid @RequestBody ShipDeliveryDTO dto,
                        @AuthenticationPrincipal JwtUserDetails user) {
        lifecycleService.ship(id, user.getTenantId(), user.getUserId(), dto);
        return R.ok();
    }

    @AuditLog(module = "交付管理", operation = "UPDATE", desc = "确认收货", targetType = "Delivery")
    @Operation(summary = "采购商确认收货")
    @PutMapping("/{id}/receive")
    public R<Void> receive(@PathVariable Long id,
                           @Valid @RequestBody ReceiveDeliveryDTO dto,
                           @AuthenticationPrincipal JwtUserDetails user) {
        lifecycleService.receive(id, user.getTenantId(), user.getUserId(), dto);
        return R.ok();
    }

    @AuditLog(module = "交付管理", operation = "UPDATE", desc = "验收合格", targetType = "Delivery")
    @Operation(summary = "采购商验收合格")
    @PutMapping("/{id}/accept")
    public R<Void> accept(@PathVariable Long id,
                          @Parameter(description = "验收备注") @RequestParam(required = false) String remark,
                          @AuthenticationPrincipal JwtUserDetails user) {
        lifecycleService.accept(id, user.getTenantId(), user.getUserId(), remark);
        return R.ok();
    }

    @AuditLog(module = "交付管理", operation = "UPDATE", desc = "验收不合格", targetType = "Delivery")
    @Operation(summary = "采购商验收不合格（进入待供应商确认）")
    @PutMapping("/{id}/reject")
    public R<Void> reject(@PathVariable Long id,
                          @Valid @RequestBody RejectDeliveryDTO dto,
                          @AuthenticationPrincipal JwtUserDetails user) {
        returnService.reject(id, user.getTenantId(), user.getUserId(), dto);
        return R.ok();
    }

    @AuditLog(module = "交付管理", operation = "UPDATE", desc = "部分残次发起补发", targetType = "Delivery")
    @Operation(summary = "采购商验收部分残次（合格部分验收通过 + 残次部分发起补发）")
    @PutMapping("/{id}/partial-reject")
    public R<Void> partialReject(@PathVariable Long id,
                                 @Valid @RequestBody PartialRejectDTO dto,
                                 @AuthenticationPrincipal JwtUserDetails user) {
        returnService.partialReject(id, user.getTenantId(), user.getUserId(), dto);
        return R.ok();
    }

    @AuditLog(module = "交付管理", operation = "UPDATE", desc = "供应商确认退回", targetType = "Delivery")
    @Operation(summary = "供应商确认退回")
    @PutMapping("/{id}/confirm-return")
    public R<Void> confirmReturn(@PathVariable Long id,
                                 @AuthenticationPrincipal JwtUserDetails user) {
        returnService.confirmReturn(id, user.getTenantId(), user.getUserId());
        return R.ok();
    }

    @AuditLog(module = "交付管理", operation = "UPDATE", desc = "供应商提出异议", targetType = "Delivery")
    @Operation(summary = "供应商对验收不合格提出异议")
    @PutMapping("/{id}/dispute-return")
    public R<Void> disputeReturn(@PathVariable Long id,
                                 @Parameter(description = "异议原因") @RequestParam(required = false) String remark,
                                 @AuthenticationPrincipal JwtUserDetails user) {
        returnService.disputeReturn(id, user.getTenantId(), user.getUserId(), remark);
        return R.ok();
    }

    @AuditLog(module = "交付管理", operation = "UPDATE", desc = "填写退货物流", targetType = "Delivery")
    @Operation(summary = "采购商填写退货物流")
    @PutMapping("/{id}/return-logistics")
    public R<Void> fillReturnLogistics(@PathVariable Long id,
                                       @Valid @RequestBody ReturnLogisticsDTO dto,
                                       @AuthenticationPrincipal JwtUserDetails user) {
        returnService.fillReturnLogistics(id, user.getTenantId(), user.getUserId(), dto);
        return R.ok();
    }

    @AuditLog(module = "交付管理", operation = "UPDATE", desc = "供应商确认收到退货", targetType = "Delivery")
    @Operation(summary = "供应商确认收到退货")
    @PutMapping("/{id}/confirm-return-receipt")
    public R<Void> confirmReturnReceipt(@PathVariable Long id,
                                        @AuthenticationPrincipal JwtUserDetails user) {
        returnService.confirmReturnReceipt(id, user.getTenantId(), user.getUserId());
        return R.ok();
    }

    @AuditLog(module = "交付管理", operation = "UPDATE", desc = "更正运单", targetType = "Delivery")
    @Operation(summary = "供应商更正运单（仅已发货未收货）")
    @PutMapping("/{id}/correct-tracking")
    public R<Void> correctTracking(@PathVariable Long id,
                                   @Valid @RequestBody CorrectTrackingDTO dto,
                                   @AuthenticationPrincipal JwtUserDetails user) {
        lifecycleService.correctTracking(id, user.getTenantId(), user.getUserId(), dto);
        return R.ok();
    }

    @Operation(summary = "供应商发货排期列表（分批计划 + 发货状态）")
    @GetMapping("/shipment-plan")
    public R<Map<String, Object>> shipmentPlan(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "关键词（合同编号/标题）") @RequestParam(required = false) String keyword,
            @Parameter(description = "状态筛选") @RequestParam(required = false) String status,
            @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(queryService.listShipmentPlan(user.getTenantId(), pageNum, pageSize, keyword, status));
    }

    @AuditLog(module = "交付管理", operation = "CREATE", desc = "按分批计划发货", targetType = "Delivery")
    @Operation(summary = "供应商按分批计划发货（创建发货单+发货一步到位）")
    @PostMapping("/ship-batch")
    public R<Void> shipFromBatchPlan(
            @Parameter(description = "合同ID") @RequestParam Long contractId,
            @Parameter(description = "分批计划ID") @RequestParam Long batchPlanId,
            @Valid @RequestBody ShipDeliveryDTO dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        lifecycleService.shipFromBatchPlan(user.getTenantId(), user.getUserId(), contractId, batchPlanId, dto);
        return R.ok();
    }

    @AuditLog(module = "交付管理", operation = "CREATE", desc = "供应商提交承诺交期", targetType = "Delivery")
    @Operation(summary = "供应商提交承诺交期（未闭环单，同单 pending 时不可重复提交）")
    @PostMapping("/{id}/promise-date")
    public R<Long> submitPromiseDate(@PathVariable Long id,
                                     @Valid @RequestBody PromiseDateDTO dto,
                                     @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(promiseDateService.submitPromiseDate(id, user.getTenantId(), user.getUserId(), dto));
    }

    @AuditLog(module = "交付管理", operation = "UPDATE", desc = "采购方确认承诺交期", targetType = "Delivery")
    @Operation(summary = "采购方确认承诺交期（写回考核基准）")
    @PutMapping("/promise-date/{promiseId}/confirm")
    public R<Void> confirmPromiseDate(@PathVariable Long promiseId,
                                      @AuthenticationPrincipal JwtUserDetails user) {
        promiseDateService.confirmPromiseDate(promiseId, user.getTenantId(), user.getUserId());
        return R.ok();
    }

    @AuditLog(module = "交付管理", operation = "UPDATE", desc = "采购方拒绝承诺交期", targetType = "Delivery")
    @Operation(summary = "采购方拒绝承诺交期")
    @PutMapping("/promise-date/{promiseId}/reject")
    public R<Void> rejectPromiseDate(@PathVariable Long promiseId,
                                     @AuthenticationPrincipal JwtUserDetails user) {
        promiseDateService.rejectPromiseDate(promiseId, user.getTenantId(), user.getUserId());
        return R.ok();
    }
}
