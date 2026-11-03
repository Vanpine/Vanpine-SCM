package cn.greensmartplanet.gsc.platform.controller;

import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.framework.audit.AuditLog;
import cn.greensmartplanet.gsc.framework.security.JwtUserDetails;
import cn.greensmartplanet.gsc.platform.dto.CreateUserDTO;
import cn.greensmartplanet.gsc.platform.dto.UpdateUserDTO;
import cn.greensmartplanet.gsc.platform.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('*:*:*', 'admin', 'user')")
@Tag(name = "用户管理", description = "用户 CRUD、角色分配、重置密码、状态切换")
public class UserController {

    private final UserService userService;

    /**
     * 用户分页列表（租户隔离，支持用户名/姓名/状态筛选）
     */
    @Operation(summary = "用户分页列表")
    @GetMapping("/users")
    public R<Map<String, Object>> list(@RequestParam(defaultValue = "1") int pageNum,
                                       @RequestParam(defaultValue = "10") int pageSize,
                                       @RequestParam(required = false) String username,
                                       @RequestParam(required = false) String realName,
                                       @RequestParam(required = false) String status,
                                       @AuthenticationPrincipal JwtUserDetails user) {
        var page = userService.listUsers(pageNum, pageSize, user.getTenantId(), username, realName, status);
        Map<String, Object> result = new HashMap<>();
        result.put("records", page.getRecords());
        result.put("total", page.getTotal());
        result.put("page", page.getCurrent());
        result.put("pageSize", page.getSize());
        return R.ok(result);
    }

    /**
     * 用户详情（含已分配角色ID列表）
     */
    @Operation(summary = "用户详情")
    @GetMapping("/users/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id,
                                         @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(userService.getDetail(id, user));
    }

    /**
     * 新增用户（校验用户名租户内唯一，默认active状态）
     */
    @Operation(summary = "新增用户")
    @AuditLog(module = "用户管理", operation = "CREATE", desc = "新增用户", targetType = "User")
    @PostMapping("/users")
    public R<Void> create(@Valid @RequestBody CreateUserDTO dto,
                          @AuthenticationPrincipal JwtUserDetails user) {
        userService.create(user.getTenantId(), user.getUserId(), dto);
        return R.ok();
    }

    /**
     * 编辑用户（用户名修改时校验租户内唯一）
     */
    @Operation(summary = "编辑用户")
    @AuditLog(module = "用户管理", operation = "UPDATE", desc = "编辑用户", targetType = "User")
    @PutMapping("/users/{id}")
    public R<Void> update(@PathVariable Long id,
                          @Valid @RequestBody UpdateUserDTO dto,
                          @AuthenticationPrincipal JwtUserDetails user) {
        userService.update(id, dto, user);
        return R.ok();
    }

    /**
     * 逻辑删除用户
     */
    @Operation(summary = "删除用户")
    @AuditLog(module = "用户管理", operation = "DELETE", desc = "删除用户", targetType = "User")
    @DeleteMapping("/users/{id}")
    public R<Void> delete(@PathVariable Long id,
                          @AuthenticationPrincipal JwtUserDetails user) {
        userService.delete(id, user);
        return R.ok();
    }

    /**
     * 分配角色（全量替换用户角色关联）
     */
    @Operation(summary = "分配角色")
    @AuditLog(module = "用户管理", operation = "UPDATE", desc = "分配角色", targetType = "User")
    @PutMapping("/users/{id}/roles")
    public R<Void> assignRoles(@PathVariable Long id,
                               @RequestBody Map<String, List<Long>> body,
                               @AuthenticationPrincipal JwtUserDetails user) {
        userService.assignRoles(id, body.get("roleIds"), user);
        return R.ok();
    }

    /**
     * 重置密码（管理员强制设置新密码）
     */
    @Operation(summary = "重置密码")
    @AuditLog(module = "用户管理", operation = "UPDATE", desc = "重置密码", targetType = "User")
    @PutMapping("/users/{id}/password")
    public R<Void> resetPassword(@PathVariable Long id,
                                 @RequestBody Map<String, String> body,
                                 @AuthenticationPrincipal JwtUserDetails user) {
        userService.resetPassword(id, body.get("password"), user);
        return R.ok();
    }

    /**
     * 状态切换（启用/禁用/锁定）
     */
    @Operation(summary = "状态切换")
    @AuditLog(module = "用户管理", operation = "UPDATE", desc = "用户状态切换", targetType = "User")
    @PutMapping("/users/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id,
                                @RequestBody Map<String, String> body,
                                @AuthenticationPrincipal JwtUserDetails user) {
        userService.updateStatus(id, body.get("status"), user);
        return R.ok();
    }
}
