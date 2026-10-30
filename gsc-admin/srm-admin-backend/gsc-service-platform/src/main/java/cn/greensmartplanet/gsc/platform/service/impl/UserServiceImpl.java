package cn.greensmartplanet.gsc.platform.service.impl;

import cn.greensmartplanet.gsc.common.constant.SystemConstants;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.framework.security.JwtUserDetails;
import cn.greensmartplanet.gsc.framework.security.PermissionCache;
import cn.greensmartplanet.gsc.framework.security.RefreshTokenStore;
import cn.greensmartplanet.gsc.platform.dto.CreateUserDTO;
import cn.greensmartplanet.gsc.platform.dto.UpdateUserDTO;
import cn.greensmartplanet.gsc.platform.entity.Role;
import cn.greensmartplanet.gsc.platform.entity.User;
import cn.greensmartplanet.gsc.common.enums.UserStatus;
import cn.greensmartplanet.gsc.platform.mapper.RoleMapper;
import cn.greensmartplanet.gsc.platform.mapper.UserMapper;
import cn.greensmartplanet.gsc.platform.service.UserService;
import cn.greensmartplanet.gsc.platform.support.TenantGuard;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;
    private final PermissionCache permissionCache;
    private final RefreshTokenStore refreshTokenStore;

    /** 分页查用户列表（租户隔离，支持用户名/姓名/状态筛选，填充角色名） */
    @Override
    public IPage<User> listUsers(int pageNum, int pageSize, Long tenantId,
                                 String username, String realName, String status) {
        var wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getTenantId, tenantId)
                .like(StringUtils.hasText(username), User::getUsername, username)
                .like(StringUtils.hasText(realName), User::getRealName, realName)
                .eq(StringUtils.hasText(status), User::getStatus, status)
                .orderByAsc(User::getId);
        IPage<User> page = userMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        // 为每个用户填充角色名称
        page.getRecords().forEach(user -> {
            List<Role> roles = userMapper.selectRolesByUserId(user.getId());
            List<String> roleNames = roles.stream().map(Role::getRoleName).collect(Collectors.toList());
            user.setRoleNames(roleNames);
        });
        return page;
    }

    /**
     * 用户详情（含roleIds），校验目标用户归属当前租户
     */
    @Override
    public Map<String, Object> getDetail(Long userId, JwtUserDetails currentUser) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        TenantGuard.assertSameTenant(currentUser, user.getTenantId());  // 跨租户越权拦截
        Map<String, Object> result = new HashMap<>();
        result.put("id", user.getId());
        result.put("tenantId", user.getTenantId());
        result.put("username", user.getUsername());
        result.put("realName", user.getRealName());
        result.put("email", user.getEmail());
        result.put("phone", user.getPhone());
        result.put("status", user.getStatus());
        List<Long> roleIds = userMapper.selectRoleIdsByUserId(userId);
        result.put("isAdmin", roleIds != null && (roleIds.contains(2L) || roleIds.contains(1L)));
        result.put("roleIds", roleIds);
        return result;
    }

    /**
     * 新增用户：校验用户名租户内唯一→加密密码→入库
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public User create(Long tenantId, Long createBy, CreateUserDTO dto) {
        User exist = userMapper.selectByTenantAndUsername(tenantId, dto.getUsername());
        if (exist != null) {
            throw new BizException(ErrorCode.USERNAME_EXISTS);  // 用户名已被占用
        }
        User user = User.builder()
                .tenantId(tenantId)
                .username(dto.getUsername())
                .passwordHash(passwordEncoder.encode(dto.getPassword()))
                .realName(dto.getRealName())
                .email(dto.getEmail())
                .phone(dto.getPhone())
                .status(UserStatus.ACTIVE.getCode())
                .createBy(createBy)
                .build();
        userMapper.insert(user);
        return user;
    }

    /**
     * 编辑用户（校验归属；用户名修改时校验租户内唯一）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long userId, UpdateUserDTO dto, JwtUserDetails currentUser) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        TenantGuard.assertSameTenant(currentUser, user.getTenantId());  // 跨租户越权拦截
        if (!user.getUsername().equals(dto.getUsername())) {
            User exist = userMapper.selectByTenantAndUsername(user.getTenantId(), dto.getUsername());
            if (exist != null && !exist.getId().equals(userId)) {
                throw new BizException(ErrorCode.USERNAME_EXISTS);  // 用户名已被占用
            }
        }
        user.setUsername(dto.getUsername());
        user.setRealName(dto.getRealName());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        userMapper.updateById(user);
    }

    /**
     * 逻辑删除用户（校验归属），删除后强制该用户下线
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, JwtUserDetails currentUser) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        TenantGuard.assertSameTenant(currentUser, user.getTenantId());  // 跨租户越权拦截
        userMapper.deleteById(userId);
        TenantGuard.forceLogout(redisTemplate, permissionCache, refreshTokenStore, userId);  // 已删除用户不得再用旧 Token 访问
    }

    /**
     * 分配角色（先删后插，全量替换），校验归属与 roleIds 合法性，变更后强制该用户重新登录
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<Long> roleIds, JwtUserDetails currentUser) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        TenantGuard.assertSameTenant(currentUser, user.getTenantId());  // 跨租户越权拦截
        assertRoleIdsAssignable(roleIds, currentUser);  // 不信任前端传入的 roleId
        userMapper.deleteUserRoles(userId);
        if (roleIds != null && !roleIds.isEmpty()) {
            userMapper.insertUserRoles(userId, roleIds);
        }
        // 强制该用户重新登录，重新登录后拿到最新角色权限（当前操作者除外，避免保存后自己被登出）
        if (!userId.equals(currentUser.getUserId())) {
            TenantGuard.forceLogout(redisTemplate, permissionCache, refreshTokenStore, userId);
        } else {
            // 操作者改自己的角色：不能踢自己，但权限缓存必须失效，下次请求回源 DB 拿新权限
            permissionCache.invalidate(userId);
        }
    }

    /**
     * 校验传入的 roleIds 是否都可分配：必须属于当前租户，或平台系统内置角色
     * <p>
     * 复用 selectByTenant（返回「本租户 + 平台系统角色」），避免前端伪造任意 roleId 越权提权。
     */
    private void assertRoleIdsAssignable(List<Long> roleIds, JwtUserDetails currentUser) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;  // 清空角色无需校验
        }
        Set<Long> assignable = roleMapper.selectByTenant(currentUser.getTenantId(), null)
                .stream().map(Role::getId).collect(Collectors.toSet());
        if (!assignable.containsAll(roleIds)) {
            throw new BizException(ErrorCode.FORBIDDEN);  // 含他租户或不可分配的角色
        }
    }

    /**
     * 重置密码（管理员强制设置新密码），校验归属，重置后强制该用户下线
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long userId, String password, JwtUserDetails currentUser) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        TenantGuard.assertSameTenant(currentUser, user.getTenantId());  // 跨租户越权拦截
        userMapper.updatePassword(userId, passwordEncoder.encode(password));
        TenantGuard.forceLogout(redisTemplate, permissionCache, refreshTokenStore, userId);  // 旧 Token 立即失效
    }

    /** 状态切换（active/disabled/locked），校验归属；禁用/锁定时删除 Token 使其立即失效 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long userId, String status, JwtUserDetails currentUser) {
        User user = userMapper.selectById(userId);  // 查用户是否存在
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);  // 用户不存在
        }
        TenantGuard.assertSameTenant(currentUser, user.getTenantId());  // 跨租户越权拦截
        user.setStatus(status);  // 设置新状态
        userMapper.updateById(user);  // 更新数据库
        // 禁用或锁定 → 清 Token 白名单与权限缓存，并写入踢出原因供前端提示
        if (UserStatus.DISABLED.getCode().equals(status) || UserStatus.LOCKED.getCode().equals(status)) {
            String reason = UserStatus.LOCKED.getCode().equals(status)
                    ? "账号已被锁定，请15分钟后重试" : "账号已被禁用，请联系管理员";
            TenantGuard.forceLogout(redisTemplate, permissionCache, refreshTokenStore, userId);
            redisTemplate.opsForValue().set(SystemConstants.KICK_REASON_PREFIX + userId,
                    reason, java.time.Duration.ofMinutes(5));  // 踢出原因，只提示一次
        }
    }
}
