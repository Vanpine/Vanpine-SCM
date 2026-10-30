package cn.greensmartplanet.gsc.platform.controller;

import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.framework.audit.AuditLog;
import cn.greensmartplanet.gsc.framework.security.JwtUserDetails;
import cn.greensmartplanet.gsc.platform.dto.ChangePasswordDTO;
import cn.greensmartplanet.gsc.platform.dto.ForgotPasswordDTO;
import cn.greensmartplanet.gsc.platform.dto.LoginDTO;
import cn.greensmartplanet.gsc.platform.dto.RefreshTokenDTO;
import cn.greensmartplanet.gsc.platform.dto.ResetPasswordDTO;
import cn.greensmartplanet.gsc.platform.dto.UpdateProfileDTO;
import cn.greensmartplanet.gsc.platform.service.AuthService;
import cn.greensmartplanet.gsc.platform.vo.LoginVO;
import cn.greensmartplanet.gsc.platform.vo.MenuTreeVO;
import cn.greensmartplanet.gsc.platform.vo.UserInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 认证控制器
 *
 * @author gsc
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "登录、登出、Token刷新、用户信息、菜单、修改密码")
public class AuthController {

    private final AuthService authService;  // 认证业务

    /** 登录：校验租户+用户名+密码 → 返回JWT + 用户信息 + 菜单（不记审计日志） */
    @Operation(summary = "登录")
    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginDTO req, HttpServletRequest request) {
        return R.ok(authService.login(req, request));  // 委托 AuthService 处理
    }

    /** 登出：从Redis白名单中删除Token（不记审计日志） */
    @Operation(summary = "登出")
    @PostMapping("/logout")
    public R<Void> logout(HttpServletRequest request) {
        authService.logout(request);  // 清除 Redis 白名单
        return R.ok();
    }

    /** 刷新Token：用 RefreshToken 换新的 AccessToken */
    @Operation(summary = "刷新Token")
    @PostMapping("/refresh-token")
    public R<LoginVO> refreshToken(@Valid @RequestBody RefreshTokenDTO req) {
        return R.ok(authService.refreshToken(req));  // 校验刷新Token → 生成新Token对
    }

    /** 获取当前登录用户完整信息（角色+权限+租户） */
    @Operation(summary = "获取当前用户信息")
    @GetMapping("/me")
    public R<UserInfoVO> me(@AuthenticationPrincipal JwtUserDetails userDetails) {
        return R.ok(authService.getCurrentUser(userDetails.getUserId()));  // 从JWT取userId查用户
    }

    /**
     * 权限同步（带版本号，权限未变时只返回 version，避免每次传全量列表）
     * @param userDetails JWT 中的当前用户
     * @param version     前端持有的权限版本号
     */
    @Operation(summary = "同步用户权限（版本检查）")
    @GetMapping("/permissions")
    public R<java.util.Map<String, Object>> permissions(
            @AuthenticationPrincipal JwtUserDetails userDetails,
            @RequestParam(defaultValue = "0") long version) {
        return R.ok(authService.syncPermissions(userDetails.getUserId(), version));  // 版本一致则空返回
    }

    /** 获取当前用户菜单树（用于侧边栏渲染） */
    @Operation(summary = "获取当前用户菜单树")
    @GetMapping("/menus")
    public R<List<MenuTreeVO>> menus(@AuthenticationPrincipal JwtUserDetails userDetails) {
        return R.ok(authService.getUserMenus(userDetails.getUserId()));  // 按角色查菜单 → 树形
    }

    /** 修改密码：验旧密码 → 加密新密码 → 更新 → 踢掉所有旧登录 */
    @AuditLog(module = "认证管理", operation = "UPDATE", desc = "修改密码", targetType = "User")
    @Operation(summary = "修改密码")
    @PutMapping("/password")
    public R<Void> changePassword(@Valid @RequestBody ChangePasswordDTO req,
                                  @AuthenticationPrincipal JwtUserDetails userDetails) {
        authService.changePassword(userDetails.getUserId(), req);  // 修改后旧Token失效
        return R.ok();
    }

    /** 更新个人信息：用户名、姓名、邮箱、手机、头像 */
    @AuditLog(module = "认证管理", operation = "UPDATE", desc = "修改个人信息", targetType = "User")
    @Operation(summary = "更新当前用户个人信息")
    @PutMapping("/profile")
    public R<Void> updateProfile(@Valid @RequestBody UpdateProfileDTO req,
                                  @AuthenticationPrincipal JwtUserDetails userDetails) {
        authService.updateProfile(userDetails.getUserId(), req);  // 用户名修改时校验唯一性
        return R.ok();
    }

    /** 忘记密码第一步：发送6位验证码到注册邮箱 */
    @Operation(summary = "忘记密码 - 发送验证码")
    @PostMapping("/forgot-password")
    public R<Void> forgotPassword(@Valid @RequestBody ForgotPasswordDTO req) {
        authService.sendResetCode(req);  // 60秒内不可重复发送
        return R.ok();
    }

    /** 忘记密码第二步：验证码通过后重置密码 */
    @Operation(summary = "忘记密码 - 通过验证码重置密码")
    @AuditLog(module = "认证管理", operation = "UPDATE", desc = "重置密码", targetType = "User")
    @PostMapping("/reset-password")
    public R<Void> resetPassword(@Valid @RequestBody ResetPasswordDTO req) {
        authService.resetPasswordByCode(req);  // 校验验证码 → 重置密码 → 踢掉旧登录
        return R.ok();
    }
}
