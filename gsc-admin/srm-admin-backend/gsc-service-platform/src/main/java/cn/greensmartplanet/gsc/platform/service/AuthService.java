package cn.greensmartplanet.gsc.platform.service;

import cn.greensmartplanet.gsc.platform.dto.*;
import cn.greensmartplanet.gsc.platform.vo.LoginVO;
import cn.greensmartplanet.gsc.platform.vo.MenuTreeVO;
import cn.greensmartplanet.gsc.platform.vo.UserInfoVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Map;

/**
 * 认证服务接口 — 登录、登出、Token刷新、用户信息、菜单、修改密码
 *
 * @author gsc
 */
public interface AuthService {

    /**
     * 登录：查租户→查用户→验密码→查角色权限→查菜单→生成JWT→返回
     */
    LoginVO login(LoginDTO dto, HttpServletRequest request);

    /**
     * 登出：删除Redis白名单，标记refreshToken失效
     */
    void logout(HttpServletRequest request);

    /**
     * 刷新Token：用RefreshToken换新的AccessToken
     */
    LoginVO refreshToken(RefreshTokenDTO dto);

    /**
     * 获取当前用户信息（角色+权限）
     */
    UserInfoVO getCurrentUser(Long userId);

    /**
     * 获取当前用户菜单树
     */
    List<MenuTreeVO> getUserMenus(Long userId);

    /**
     * 修改密码
     */
    void changePassword(Long userId, ChangePasswordDTO dto);

    /**
     * 忘记密码 - 发送验证码到邮箱
     */
    void sendResetCode(ForgotPasswordDTO dto);

    /**
     * 忘记密码 - 通过验证码重置密码
     */
    void resetPasswordByCode(ResetPasswordDTO dto);

    /**
     * 更新当前用户个人信息
     */
    void updateProfile(Long userId, UpdateProfileDTO dto);

    /**
     * 同步权限（带版本号）：版本一致返回 changed=false，不一致返回最新权限列表
     */
    Map<String, Object> syncPermissions(Long userId, long clientVersion);
}
