package cn.greensmartplanet.gsc.platform.support;

import cn.greensmartplanet.gsc.common.constant.SystemConstants;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.framework.security.JwtUserDetails;
import cn.greensmartplanet.gsc.framework.security.PermissionCache;
import cn.greensmartplanet.gsc.framework.security.RefreshTokenStore;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * 租户归属守卫 — 平台管理接口的越权防线
 * <p>
 * 收敛三类散落逻辑：
 * <ul>
 *   <li>超管判定（原多处写 {@code tenantId == 1}，Long 拆箱比较不可靠；改用角色，语义是真实身份）</li>
 *   <li>租户归属校验（原按 id 操作的接口完全不校验，可水平越权）</li>
 *   <li>强制下线（原三份重复实现，且都漏了权限缓存与版本号）</li>
 * </ul>
 *
 * @author gsc
 */
public final class TenantGuard {

    private TenantGuard() {
    }

    /**
     * 是否超管：看角色而非租户ID —— 平台租户 id=1 只是约定，角色才是真实身份
     */
    public static boolean isSuperAdmin(JwtUserDetails user) {
        return user != null && user.getRoles() != null
                && user.getRoles().contains(SystemConstants.ROLE_SUPER_ADMIN);
    }

    /**
     * 校验当前用户是否有权操作目标租户的数据：超管可跨租户，其余必须同租户
     *
     * @param user           当前登录用户
     * @param targetTenantId 目标数据所属租户
     */
    public static void assertSameTenant(JwtUserDetails user, Long targetTenantId) {
        if (isSuperAdmin(user)) {
            return;  // 超管可跨租户
        }
        if (user == null || user.getTenantId() == null
                || !user.getTenantId().equals(targetTenantId)) {
            throw new BizException(ErrorCode.FORBIDDEN);  // 跨租户越权
        }
    }

    /**
     * 强制用户下线：清 Token 白名单 + 清权限缓存 + 递增权限版本号
     * <p>
     * 版本号用于让「缓存仍在但已陈旧」的窗口失效，并驱动前端权限同步；
     * 真正让旧 Token 立即失效的是删除 accessToken。
     */
    public static void forceLogout(RedisTemplate<String, Object> redisTemplate,
                                   PermissionCache permissionCache,
                                   RefreshTokenStore refreshTokenStore, Long userId) {
        redisTemplate.delete(SystemConstants.ACCESS_TOKEN_PREFIX + userId);
        refreshTokenStore.remove(userId);
        permissionCache.invalidate(userId);  // 删缓存 + 递增版本号（带 TTL）
    }
}
