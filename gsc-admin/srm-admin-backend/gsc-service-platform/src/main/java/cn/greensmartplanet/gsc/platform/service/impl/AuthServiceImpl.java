package cn.greensmartplanet.gsc.platform.service.impl;

import cn.greensmartplanet.gsc.common.constant.SystemConstants;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.framework.security.JwtTokenProvider;
import cn.greensmartplanet.gsc.framework.security.PermissionCache;
import cn.greensmartplanet.gsc.framework.security.PermissionProvider;
import cn.greensmartplanet.gsc.framework.security.RefreshTokenStore;
import cn.greensmartplanet.gsc.platform.dto.*;
import cn.greensmartplanet.gsc.platform.entity.Menu;
import cn.greensmartplanet.gsc.platform.entity.Role;
import cn.greensmartplanet.gsc.platform.entity.Tenant;
import cn.greensmartplanet.gsc.platform.entity.User;
import cn.greensmartplanet.gsc.common.enums.TenantStatus;
import cn.greensmartplanet.gsc.common.enums.UserStatus;
import cn.greensmartplanet.gsc.platform.mapper.MenuMapper;
import cn.greensmartplanet.gsc.platform.mapper.TenantMapper;
import cn.greensmartplanet.gsc.platform.mapper.UserMapper;
import cn.greensmartplanet.gsc.platform.service.AuthService;
import cn.greensmartplanet.gsc.platform.support.MenuTreeCache;
import cn.greensmartplanet.gsc.platform.vo.LoginVO;
import cn.greensmartplanet.gsc.platform.vo.MenuTreeVO;
import cn.greensmartplanet.gsc.platform.vo.UserInfoVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import cn.greensmartplanet.gsc.framework.oss.OssService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService, PermissionProvider {

    private final TenantMapper tenantMapper;
    private final UserMapper userMapper;
    private final MenuMapper menuMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;
    private final cn.greensmartplanet.gsc.framework.mail.MailService mailService;
    private final OssService ossService;  // 组装用户信息时按 avatarKey 现签头像 URL

    /** 权限缓存读写（带版本号），与 JwtAuthenticationFilter 共用同一实现，保证格式一致 */
    private final PermissionCache permissionCache;
    /** 菜单树缓存（带全局版本号），避免每次登录/刷新都跑嵌套子查询 */
    private final MenuTreeCache menuTreeCache;
    /** 刷新令牌白名单（原子轮换，防并发刷新互相覆盖） */
    private final RefreshTokenStore refreshTokenStore;

    /**
     * 登录：查租户→查用户→验密码→查角色权限→查菜单→生成JWT→返回
     */
    @Override
    public LoginVO login(LoginDTO dto, HttpServletRequest request) {
        //  1. tenantMapper.selectByCode → 找到租户
        Tenant tenant = tenantMapper.findTenantByCode(dto.getTenantCode());
        //  2. tenant 不存在 → throw BizException(ErrorCode.TENANT_NOT_FOUND)
        if (tenant == null) {
            throw new BizException(ErrorCode.TENANT_NOT_FOUND);
        }
        //  3. tenant.status != active → throw BizException(ErrorCode.TENANT_DISABLED)
        if (!TenantStatus.ACTIVE.getCode().equals(tenant.getStatus())) {
            throw new BizException(ErrorCode.TENANT_DISABLED);
        }
        //  4. userMapper.selectByTenantAndUsername → 找到用户
        User user = userMapper.selectByTenantAndUsername(tenant.getId(), dto.getUsername());
        // 5. user 不存在 → throw BizException（用 LOGIN_FAILED 防用户名枚举）
        if (user == null) {
            throw new BizException(ErrorCode.LOGIN_FAILED);
        }
        // 6. user.status 检查
        if (UserStatus.DISABLED.getCode().equals(user.getStatus())) {
            throw new BizException(ErrorCode.USER_DISABLED);
        }
        if (UserStatus.LOCKED.getCode().equals(user.getStatus())) {
            throw new BizException(ErrorCode.ACCOUNT_LOCKED);
        }
        // Redis 试错计数 key
        String retryKey = SystemConstants.RETRY_KEY + user.getId();
        Integer retryCount = (Integer) redisTemplate.opsForValue().get(retryKey);
        if (retryCount != null && retryCount >= SystemConstants.MAX_LOGIN_RETRY) {
            throw new BizException(ErrorCode.ACCOUNT_LOCKED);
        }
        // 7. BCrypt 验密码
        if (!passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            long count = redisTemplate.opsForValue().increment(retryKey);
            if (count == 1) {
                redisTemplate.expire(retryKey, Duration.ofSeconds(SystemConstants.LOGIN_LOCK_SECONDS));
            }
            if (count >= SystemConstants.MAX_LOGIN_RETRY) {
                throw new BizException(ErrorCode.ACCOUNT_LOCKED);
            }
            throw new BizException(ErrorCode.LOGIN_FAILED);
        }
        // 密码正确，清除失败计数
        redisTemplate.delete(retryKey);

        // 9. 用户信息（复用 getCurrentUser）
        UserInfoVO userInfo = getCurrentUser(user.getId());

        // 10. 菜单树
        List<MenuTreeVO> menuTree = getUserMenus(user.getId());

        // 11. 生成 JWT（权限不存 JWT，改存 Redis）
        String accessToken = jwtTokenProvider.generateToken(
                user.getId(), tenant.getId(), tenant.getTenantCode(),
                user.getUsername(), userInfo.getRoles(), user.getFirmId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        // 12. 权限写入 Redis 缓存 + 递增版本号（缓存值带版本，供读取方判定失效）
        permissionCache.save(user.getId(), userInfo.getPermissions(),
                permissionCache.bumpVersion(user.getId()));

        // 13. 存入 Redis 白名单（登出或过期后自动失效）
        redisTemplate.opsForValue().set(
                "accessToken:" + user.getId(), accessToken,
                Duration.ofSeconds(SystemConstants.JWT_EXPIRATION));
        // 13.1 refresh token 白名单：只认当前这一个，旧 token 复用即拒绝
        refreshTokenStore.save(user.getId(), refreshToken);

        // 14. 更新最后登录信息
        String clientIp = getClientIp(request);
        userMapper.updateLoginInfo(user.getId(), clientIp, LocalDateTime.now());

        return LoginVO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(SystemConstants.JWT_EXPIRATION)
                .userInfo(userInfo)
                .menus(menuTree)
                .build();
    }

    /**
     * 从请求中提取客户端真实IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 扁平菜单列表 → 树形结构
     * parentId=0 的作为根节点，其余挂到对应父节点的 children 下
     */
    private List<MenuTreeVO> buildMenuTree(List<Menu> flatList) {
        if (flatList == null || flatList.isEmpty()) {
            return Collections.emptyList();
        }
        // Entity → VO，放入 Map（Stream 转换）
        Map<Long, MenuTreeVO> map = flatList.stream()
                .collect(LinkedHashMap::new, (mapped, m) -> {
                    MenuTreeVO vo = new MenuTreeVO();
                    vo.setId(m.getId());
                    vo.setParentId(m.getParentId());
                    vo.setMenuName(m.getMenuName());
                    vo.setMenuIcon(m.getMenuIcon());
                    vo.setMenuPath(m.getMenuPath());
                    vo.setPermCode(m.getPermCode());
                    vo.setIsVisible(m.getIsVisible());
                    vo.setIsCache(m.getIsCache());
                    vo.setSortOrder(m.getSortOrder());
                    vo.setChildren(new ArrayList<>());
                    mapped.put(vo.getId(), vo);
                }, LinkedHashMap::putAll);
        // 挂载子节点：parentId=0 → 根，否则 → 父节点的 children
        List<MenuTreeVO> roots = map.values().stream()
                .filter(vo -> vo.getParentId() == null || vo.getParentId() == 0)
                .toList();
        map.values().stream()
                .filter(vo -> vo.getParentId() != null && vo.getParentId() != 0)
                .forEach(vo -> {
                    MenuTreeVO parent = map.get(vo.getParentId());
                    if (parent != null) parent.getChildren().add(vo);
                });
        return roots;
    }

    /**
     * 登出：从Redis白名单中删除Token
     */
    @Override
    public void logout(HttpServletRequest request) {
        // 1. 从请求头提取 Bearer Token
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            return;
        }
        String token = bearerToken.substring(7);

        // 2. 解析 Token 拿到 userId
        Long userId = jwtTokenProvider.getUserIdFromToken(token);

        // 3. 删除 accessToken 白名单
        redisTemplate.delete("accessToken:" + userId);

        // 4. 删除 refreshToken 白名单，使当前 refresh token 立即失效
        refreshTokenStore.remove(userId);
    }

    /**
     * 刷新Token：用RefreshToken换新的AccessToken
     */
    @Override
    public LoginVO refreshToken(RefreshTokenDTO dto) {
        String refreshToken = dto.getRefreshToken();
        // 1. 校验签名 + 类型必须是 refresh（access token 不能换新 token）
        if (!jwtTokenProvider.validateToken(refreshToken)
                || !JwtTokenProvider.TOKEN_TYPE_REFRESH.equals(jwtTokenProvider.getTokenType(refreshToken))) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        // 2. 拿 userId
        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        // 3. 查用户和租户
        User user = userMapper.selectById(userId);
        if (user == null || !UserStatus.ACTIVE.getCode().equals(user.getStatus())) {
            throw new BizException(ErrorCode.LOGIN_FAILED);
        }
        Tenant tenant = tenantMapper.selectById(user.getTenantId());
        // 3.1 租户必须 active（对齐登录时的租户校验，禁用后不允许继续刷新）
        if (tenant == null || !TenantStatus.ACTIVE.getCode().equals(tenant.getStatus())) {
            throw new BizException(ErrorCode.TENANT_DISABLED);
        }

        // 4. 重新查库拿最新角色权限
        UserInfoVO userInfo = getCurrentUser(userId);

        // 5. 生成新 Token（权限不存 JWT，改存 Redis）
        String accessToken = jwtTokenProvider.generateToken(
                userId, tenant.getId(), tenant.getTenantCode(),
                user.getUsername(), userInfo.getRoles(), user.getFirmId());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId);

        // 6. 原子轮换 refreshToken：比对与替换在 Redis 端一次完成
        //    并发刷新时只有第一个成功，其余返回 false —— 只拒绝，不清空登录态
        //    （清空会让两个合法并发请求互相误伤，视为盗用才清，留给后续宽限期方案处理）
        if (!refreshTokenStore.rotate(userId, refreshToken, newRefreshToken)) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }

        // 7. 权限写入 Redis 缓存 + 递增版本号（缓存值带版本，供读取方判定失效）
        permissionCache.save(userId, userInfo.getPermissions(),
                permissionCache.bumpVersion(userId));

        // 8. 覆盖 accessToken 白名单（旧 accessToken 立刻失效）
        redisTemplate.opsForValue().set(
                "accessToken:" + userId, accessToken,
                Duration.ofSeconds(SystemConstants.JWT_EXPIRATION));
        // 9. 返回
        return LoginVO.builder()
                .accessToken(accessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(SystemConstants.JWT_EXPIRATION)
                .userInfo(userInfo)
                .build();
    }

    /**
     * 获取当前用户信息（角色+权限）
     * 权限优先从 Redis 缓存读取，miss 才查 DB（路由守卫每次调用，避免频繁查库）
     */
    @Override
    public UserInfoVO getCurrentUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        Tenant tenant = tenantMapper.selectById(user.getTenantId());

        List<Role> userRoles = userMapper.selectRolesByUserId(userId);
        List<String> roleCodes = userRoles.stream().map(Role::getRoleCode).toList();
        List<String> roleNames = userRoles.stream().map(Role::getRoleName).toList();

        // 权限：缓存优先（带版本号，版本一致直接命中），否则回源 DB
        List<String> permissions = loadPermissionsWithCache(userId, roleCodes);

        return UserInfoVO.builder()
                .id(user.getId())
                .tenantId(tenant != null ? tenant.getId() : null)
                .tenantCode(tenant != null ? tenant.getTenantCode() : null)
                .tenantName(tenant != null ? tenant.getTenantName() : null)
                .username(user.getUsername())
                .realName(user.getRealName())
                .avatarUrl(signAvatar(user.getAvatarKey()))
                .email(user.getEmail())
                .phone(user.getPhone())
                .roles(roleCodes)
                .roleNames(roleNames)
                .permissions(permissions)
                .build();
    }

    /**
     * 获取当前用户菜单树（带全局版本号缓存，菜单变更时整体失效）
     */
    @Override
    public List<MenuTreeVO> getUserMenus(Long userId) {
        return menuTreeCache.load(userId, () -> buildMenuTree(menuMapper.selectByUserId(userId)));
    }

    /**
     * 超管权限兜底：SUPER_ADMIN 恒定拥有通配权限。
     * <p>
     * 平台超管的前端放行依赖 {@code *:*:*} 通配码（见前端 hasPermission / 路由守卫），
     * 而该通配码存在 role_permission 表里、可被「角色重新分配权限」误清空，
     * 一旦清空超管自己也进不了后台。故在此兜底：只要用户有 SUPER_ADMIN 角色，
     * 无论 DB 查询结果如何都补上通配码，彻底杜绝超管被自己锁死。
     *
     * @param roleCodes   用户角色编码列表
     * @param permissions DB 查出的权限码列表（可为 null）
     * @return 兜底后的权限码列表
     */
    private List<String> applySuperAdminWildcard(List<String> roleCodes, List<String> permissions) {
        boolean isSuperAdmin = roleCodes != null && roleCodes.contains(SystemConstants.ROLE_SUPER_ADMIN);
        if (!isSuperAdmin) {
            return permissions;
        }
        // 非超管不可能有该角色，无需替换；用可变列表承载，确保后续调用方能修改
        List<String> result = permissions != null ? new ArrayList<>(permissions) : new ArrayList<>();
        if (!result.contains(SystemConstants.PERM_SUPER_ADMIN_WILDCARD)) {
            result.add(SystemConstants.PERM_SUPER_ADMIN_WILDCARD);
        }
        return result;
    }

    /**
     * 加载权限并维护带版本号的缓存 —— 全平台权限读取的唯一入口
     * <p>
     * 缓存里恒定存「已应用超管通配」的最终权限，读取方拿到即可直接用，
     * 不再出现「登录写入含通配、getCurrentUser 写入不含通配」的不一致。
     *
     * @param userId    用户ID
     * @param roleCodes 用户角色编码（用于超管通配兜底）
     * @return 权限码列表（含超管通配），永不为 null
     */
    private List<String> loadPermissionsWithCache(Long userId, List<String> roleCodes) {
        // 复用 PermissionCache：版本比对、TTL 补设、旧格式降级、版本号丢失自愈都在其中
        return permissionCache.load(userId,
                uid -> applySuperAdminWildcard(roleCodes, userMapper.selectPermissionsByUserId(uid)));
    }

    /**
     * 修改密码
     */
    @Override
    public void changePassword(Long userId, ChangePasswordDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        // 验旧密码
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.OLD_PASSWORD_ERROR);
        }
        // 加密新密码，更新数据库
        String newHash = passwordEncoder.encode(dto.getNewPassword());
        userMapper.updatePassword(userId, newHash);

        // 踢掉当前登录（密码改了，旧Token不应继续用）
        redisTemplate.delete("accessToken:" + userId);
        refreshTokenStore.remove(userId);
    }

    /**
     * 忘记密码 - 发送验证码
     */
    @Override
    public void sendResetCode(ForgotPasswordDTO dto) {
        // 1. 查租户
        Tenant tenant = tenantMapper.findTenantByCode(dto.getTenantCode());
        if (tenant == null) {
            throw new BizException(ErrorCode.TENANT_NOT_FOUND);
        }

        // 2. 查用户（通过 tenantId + email）
        User user = userMapper.selectByTenantAndEmail(tenant.getId(), dto.getEmail());
        if (user == null) {
            throw new BizException(ErrorCode.LOGIN_FAILED);
        }

        // 3. 60秒内不可重复发送
        String sendKey = SystemConstants.PWD_RESET_SEND_PREFIX + dto.getTenantCode() + ":" + dto.getEmail();
        if (Boolean.TRUE.equals(redisTemplate.hasKey(sendKey))) {
            throw new BizException(ErrorCode.VERIFY_CODE_SENT);
        }

        // 4. 生成6位验证码
        String code = String.format("%06d", new java.util.Random().nextInt(999999));

        // 5. 存入 Redis（5分钟有效）
        String codeKey = SystemConstants.PWD_RESET_CODE_PREFIX + dto.getTenantCode() + ":" + dto.getEmail();
        redisTemplate.opsForValue().set(codeKey, code, Duration.ofSeconds(300));

        // 6. 标记已发送（60秒防重）
        redisTemplate.opsForValue().set(sendKey, System.currentTimeMillis(), Duration.ofSeconds(60));

        // 7. 发邮件
        mailService.sendResetCode(dto.getEmail(), code);
    }

    /**
     * 忘记密码 - 通过验证码重置密码
     */
    @Override
    public void resetPasswordByCode(ResetPasswordDTO dto) {
        // 1. 查租户
        Tenant tenant = tenantMapper.findTenantByCode(dto.getTenantCode());
        if (tenant == null) {
            throw new BizException(ErrorCode.TENANT_NOT_FOUND);
        }

        // 2. 查用户
        User user = userMapper.selectByTenantAndEmail(tenant.getId(), dto.getEmail());
        if (user == null) {
            throw new BizException(ErrorCode.LOGIN_FAILED);
        }

        // 3. 校验验证码
        String codeKey = SystemConstants.PWD_RESET_CODE_PREFIX + dto.getTenantCode() + ":" + dto.getEmail();
        String storedCode = (String) redisTemplate.opsForValue().get(codeKey);
        if (storedCode == null) {
            throw new BizException(ErrorCode.VERIFY_CODE_EXPIRED);
        }
        if (!storedCode.equals(dto.getCode())) {
            throw new BizException(ErrorCode.VERIFY_CODE_ERROR);
        }

        // 4. 重置密码
        String newHash = passwordEncoder.encode(dto.getNewPassword());
        userMapper.updatePassword(user.getId(), newHash);

        // 5. 删除验证码
        redisTemplate.delete(codeKey);

        // 6. 踢掉所有旧登录
        redisTemplate.delete("accessToken:" + user.getId());
        refreshTokenStore.remove(user.getId());
    }

    /**
     * 更新当前用户个人信息
     */
    @Override
    public void updateProfile(Long userId, UpdateProfileDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        // 如果修改了用户名，校验租户内唯一性
        if (!user.getUsername().equals(dto.getUsername())) {
            User exist = userMapper.selectByTenantAndUsername(user.getTenantId(), dto.getUsername());
            if (exist != null && !exist.getId().equals(userId)) {
                throw new BizException(ErrorCode.USERNAME_EXISTS);
            }
        }

        user.setUsername(dto.getUsername());
        user.setRealName(dto.getRealName());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        if (dto.getAvatarKey() != null) {
            user.setAvatarKey(dto.getAvatarKey());
        }
        user.setUpdateBy(userId);
        userMapper.updateById(user);
    }

    /**
     * 权限同步（带版本号）：版本一致 → 只返回 version，版本不同 → 额外返回 permissions
     */
    @Override
    public Map<String, Object> syncPermissions(Long userId, long clientVersion) {
        long currentVersion = permissionCache.currentVersion(userId);

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("version", currentVersion); // 始终返回当前版本号

        if (currentVersion > 0 && currentVersion == clientVersion) {
            return result;  // 版本未变，只返回 version，前端不更新权限和角色
        }

        // 版本不一致 → 返回最新权限码列表（含超管通配）
        List<Role> userRoles = userMapper.selectRolesByUserId(userId);
        List<String> roleCodes = userRoles.stream().map(Role::getRoleCode).toList();
        List<String> permissions = loadPermissionsWithCache(userId, roleCodes);
        result.put("permissions", permissions);

        // 同时返回最新角色编码列表（前端路由守卫需要根据角色做页面跳转判断）
        result.put("roles", roleCodes);
        return result;
    }

    /**
     * PermissionProvider 接口实现：从数据库查询用户权限码列表（JWT 过滤器回源用）
     */
    @Override
    public List<String> getPermissions(Long userId) {
        List<String> permissions = userMapper.selectPermissionsByUserId(userId);  // 查用户所有权限码
        // 兜底：SUPER_ADMIN 恒定拥有通配权限（与 getCurrentUser 一致，防误清后锁死）
        List<Role> roles = userMapper.selectRolesByUserId(userId);
        List<String> roleCodes = roles.stream().map(Role::getRoleCode).toList();
        return applySuperAdminWildcard(roleCodes, permissions);
    }

    /**
     * 头像地址解析：按 oss_key 现签短效 URL。
     * <p>
     * 存 key 而非 URL —— 签名会过期，持久化后必然裂图；每次查询现签，前端 &lt;img&gt; 始终可用。
     *
     * @param key OSS 对象路径
     * @return 可直接访问的头像 URL；key 为空时返回 null（前端显示默认头像）
     */
    private String signAvatar(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return ossService.generateGetUrl(key);
    }
}
