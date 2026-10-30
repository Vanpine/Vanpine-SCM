package cn.greensmartplanet.gsc.platform.controller;

import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.framework.security.JwtUserDetails;
import cn.greensmartplanet.gsc.platform.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 操作日志控制器 — 只读查询，无增删改
 *
 * @author gsc
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('*:*:*', 'admin', 'audit-log')")
@Tag(name = "操作日志", description = "操作日志的查询")
public class AuditLogController {

    private final AuditLogService auditLogService;

    /** 分页查询操作日志（超管看全部租户，企业管理员只看自己租户） */
    @Operation(summary = "分页查询操作日志")
    @GetMapping("/audit-logs")
    public R<Map<String, Object>> page(@RequestParam(defaultValue = "1") int pageNum,
                                       @RequestParam(defaultValue = "10") int pageSize,
                                       @RequestParam(required = false) String module,
                                       @RequestParam(required = false) String operation,
                                       @RequestParam(required = false) String username,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false) String startTime,
                                       @RequestParam(required = false) String endTime,
                                       @AuthenticationPrincipal JwtUserDetails user) {
        // 超管(tenant_id=1)查全部，企业管理员只看自己租户
        Long filterTenantId = user.getTenantId() != null && user.getTenantId() == 1 ? null : user.getTenantId();
        return R.ok(auditLogService.page(pageNum, pageSize, filterTenantId,
                module, operation, username, status, startTime, endTime));
    }
}
