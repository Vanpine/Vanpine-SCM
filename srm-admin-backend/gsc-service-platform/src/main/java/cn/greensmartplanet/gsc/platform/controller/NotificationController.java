package cn.greensmartplanet.gsc.platform.controller;

import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.framework.audit.AuditLog;
import cn.greensmartplanet.gsc.framework.security.JwtUserDetails;
import cn.greensmartplanet.gsc.platform.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 站内消息通知 Controller
 * <p>
 * 查询（GET）不记审计；标记已读为写操作，按规范记 @AuditLog。
 *
 * @author gsc
 */
@Tag(name = "站内消息通知")
@RestController
@RequestMapping("/api/admin/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "我的通知列表")
    @GetMapping
    public R<Map<String, Object>> list(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int pageSize,
            @Parameter(description = "是否仅未读") @RequestParam(required = false) Boolean unreadOnly,
            @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(notificationService.list(user.getTenantId(), pageNum, pageSize, unreadOnly));
    }

    @Operation(summary = "未读通知数")
    @GetMapping("/unread-count")
    public R<Integer> unreadCount(@AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(notificationService.countUnread(user.getTenantId()));
    }

    @AuditLog(module = "站内消息", operation = "UPDATE", desc = "标记通知已读", targetType = "Notification")
    @Operation(summary = "标记单条已读")
    @PutMapping("/{id}/read")
    public R<Void> markRead(@PathVariable Long id,
                            @AuthenticationPrincipal JwtUserDetails user) {
        notificationService.markRead(id, user.getTenantId());
        return R.ok();
    }

    @AuditLog(module = "站内消息", operation = "UPDATE", desc = "全部标记已读", targetType = "Notification")
    @Operation(summary = "全部标记已读")
    @PutMapping("/read-all")
    public R<Void> markAllRead(@AuthenticationPrincipal JwtUserDetails user) {
        notificationService.markAllRead(user.getTenantId());
        return R.ok();
    }
}
