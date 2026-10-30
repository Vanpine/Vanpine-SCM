package cn.greensmartplanet.gsc.supplier.controller;

import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.framework.audit.AuditLog;
import cn.greensmartplanet.gsc.framework.security.JwtUserDetails;
import cn.greensmartplanet.gsc.supplier.dto.CreateConnectionDTO;
import cn.greensmartplanet.gsc.supplier.service.ConnectionLifecycleService;
import cn.greensmartplanet.gsc.supplier.service.ConnectionQueryService;
import cn.greensmartplanet.gsc.supplier.vo.ConnectionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 对接 Controller（供应商域）— 发起/申请/接受/拒绝/关闭/列表/我的对接详情
 * <p>
 * 单资源控制器：只服务 connection 对接资源。URL 保持原有语义（部分端点带 /requirement 前缀，
 * 因业务锚定在需求上：申请需求、查我对某需求的对接）。
 *
 * @author gsc
 */
@RestController
@RequestMapping("/api/admin/match")
@RequiredArgsConstructor
@Tag(name = "对接管理")
public class ConnectionController {

    private final ConnectionLifecycleService connectionLifecycleService;
    private final ConnectionQueryService connectionQueryService;

    /** 供应商主动申请需求（从需求广场发起对接） */
    @AuditLog(module = "对接管理", operation = "CREATE", desc = "申请对接", targetType = "Connection")
    @Operation(summary = "供应商申请需求")
    @PostMapping("/requirement/{id}/apply")
    public R<ConnectionVO> applyRequirement(@PathVariable Long id,
                                             @Parameter(description = "申请留言") @RequestParam(required = false) String message,
                                             @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(connectionLifecycleService.apply(id, user.getTenantId(), user.getUserId(), message));
    }

    /** 查我对某需求的对接详情（广场内查看联系方式） */
    @Operation(summary = "我的对接详情")
    @GetMapping("/requirement/{id}/my-connection")
    public R<ConnectionVO> getMyConnection(@PathVariable Long id,
                                           @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(connectionQueryService.getMyConnectionByRequirement(id, user.getTenantId()));
    }

    /** 发起对接意向 */
    @AuditLog(module = "对接管理", operation = "CREATE", desc = "发起对接意向", targetType = "Connection")
    @Operation(summary = "发起对接意向")
    @PostMapping("/connection")
    public R<ConnectionVO> createConnection(@Valid @RequestBody CreateConnectionDTO dto,
                                            @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(connectionLifecycleService.create(user.getTenantId(), user.getUserId(), dto));
    }

    /** 我的对接记录 */
    @Operation(summary = "我的对接记录")
    @GetMapping("/connection")
    public R<Map<String, Object>> listConnections(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "状态筛选") @RequestParam(required = false) String status,
            @Parameter(description = "关键词搜索（需求标题/企业名）") @RequestParam(required = false) String keyword,
            @Parameter(description = "角色筛选：buyer/supplier") @RequestParam(required = false) String role,
            @Parameter(description = "需求ID过滤") @RequestParam(required = false) Long requirementId,
            @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(connectionQueryService.listMyConnections(user.getTenantId(), pageNum, pageSize, status, keyword, role, requirementId));
    }

    /** 接受对接 */
    @AuditLog(module = "对接管理", operation = "UPDATE", desc = "接受对接", targetType = "Connection")
    @Operation(summary = "接受对接")
    @PutMapping("/connection/{id}/accept")
    public R<Void> acceptConnection(@PathVariable Long id,
                                    @AuthenticationPrincipal JwtUserDetails user) {
        connectionLifecycleService.accept(id, user.getTenantId(), user.getUserId());
        return R.ok();
    }

    /** 拒绝对接 */
    @AuditLog(module = "对接管理", operation = "UPDATE", desc = "拒绝对接", targetType = "Connection")
    @Operation(summary = "拒绝对接")
    @PutMapping("/connection/{id}/reject")
    public R<Void> rejectConnection(@PathVariable Long id,
                                    @Parameter(description = "拒绝原因") @RequestParam(required = false) String replyMessage,
                                    @AuthenticationPrincipal JwtUserDetails user) {
        connectionLifecycleService.reject(id, user.getTenantId(), user.getUserId(), replyMessage);
        return R.ok();
    }

    /** 关闭对接（发起方取消 pending 对接） */
    @AuditLog(module = "对接管理", operation = "UPDATE", desc = "关闭对接", targetType = "Connection")
    @Operation(summary = "关闭对接")
    @PutMapping("/connection/{id}/close")
    public R<Void> closeConnection(@PathVariable Long id,
                                    @AuthenticationPrincipal JwtUserDetails user) {
        connectionLifecycleService.close(id, user.getTenantId(), user.getUserId());
        return R.ok();
    }
}
