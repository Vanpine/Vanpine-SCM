package cn.greensmartplanet.gsc.framework.security;

import cn.greensmartplanet.gsc.common.constant.SystemConstants;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户权限缓存 — 带版本号的权限快照
 * <p>
 * 缓存值结构：{@code { version: 6, permissions: ["user", "role", "*:*:*"] }}
 * <p>
 * 为什么需要版本号：旧实现靠「删缓存」触发回源，正确性依赖副作用而非显式契约。
 * 带版本后，权限变更只需递增 {@code permsVersion:{userId}}，读取方比对版本即可判定失效，
 * 无需强删缓存，也不会出现「缓存还在但已陈旧」的窗口。
 * <p>
 * 约定：缓存里恒定存「已应用超管通配」的最终权限 —— 任何读取方拿到即可直接使用，
 * 避免各调用方对「是否含 *:*:*」的理解不一致（旧实现正是因此导致超管权限时有时无）。
 *
 * @author gsc
 */
public class PermissionCache {

    /** 权限缓存 key 前缀，与 SystemConstants.PERMS_CACHE_PREFIX 一致 */
    private static final String CACHE_PREFIX = SystemConstants.PERMS_CACHE_PREFIX;
    /** 权限版本号 key 前缀 */
    private static final String VERSION_PREFIX = SystemConstants.PERMS_VERSION_PREFIX;

    private final RedisTemplate<String, Object> redisTemplate;
    /** 版本号专用：String 序列化，与 Redis 原生 INCR/SET 兼容（见 bumpVersion 注释） */
    private final StringRedisTemplate stringRedisTemplate;

    public PermissionCache(RedisTemplate<String, Object> redisTemplate,
                           StringRedisTemplate stringRedisTemplate) {
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 读取权限：版本一致则直接用缓存，否则回源 DB 并写回
     * <p>
     * 缓存存在即为权威 —— 所有权限变更路径都调用 {@link #invalidate()} 删缓存，
     * 因此「缓存还在但权限已陈旧」不会发生，无需在此比对版本号。
     * <p>
     * 旧格式（裸 List，无版本号）无法识别为 PermSnapshot，自然走回源重建，无需手动清 Redis。
     *
     * @param userId   用户ID
     * @param provider 回源实现（无实现时降级为空权限）
     * @return 权限码列表，永不为 null
     */
    public List<String> load(Long userId, PermissionProvider provider) {
        Object cached = redisTemplate.opsForValue().get(CACHE_PREFIX + userId);
        if (cached instanceof PermSnapshot snapshot) {
            // 版本号 key 被淘汰时以快照自带版本补回：它比权限缓存更小、更易丢失，
            // 补回后前端同步接口才能正常比对版本（缺失会被当成 0，导致每次都全量推送）
            if (rawVersion(userId) == null) {
                bumpVersion(userId, snapshot.getVersion());
            }
            return snapshot.getPermissions();
        }
        // miss → 回源 DB 重建
        List<String> permissions = provider != null ? provider.getPermissions(userId) : null;
        if (permissions == null) {
            permissions = new ArrayList<>();
        }
        save(userId, permissions, bumpVersion(userId, null));
        return permissions;
    }

    /**
     * 写回权限缓存，绑定版本号
     */
    public void save(Long userId, List<String> permissions, long version) {
        redisTemplate.opsForValue().set(CACHE_PREFIX + userId,
                (Serializable) new PermSnapshot(version, permissions != null ? permissions : new ArrayList<>()),
                Duration.ofSeconds(SystemConstants.JWT_EXPIRATION));
    }

    /**
     * 递增权限版本号并同步 TTL —— 权限变更的唯一入口
     * <p>
     * 版本号 key 必须带 TTL：它比权限缓存更小更易被淘汰，一旦丢失且无 TTL 兜底，
     * 快照版本就永远匹配不上，该用户所有请求将持续穿透 DB。
     *
     * @return 递增后的版本号
     */
    public long bumpVersion(Long userId) {
        return bumpVersion(userId, null);
    }

    /**
     * 递增权限版本号（或显式重置为指定值），并同步 TTL
     * <p>
     * 版本号必须用 StringRedisTemplate 读写：主 RedisTemplate 的 Jackson 序列化器会写入
     * 类型头（如 {@code ["java.lang.Long",489]}），而 INCR 是 Redis 原生命令、不走序列化器，
     * 拿到该 JSON 会直接报 {@code ERR value is not an integer}，导致该用户此后无法登录。
     *
     * @param preset 非 null 时直接设为该值（用于版本号 key 丢失后的补回），否则自增
     */
    private long bumpVersion(Long userId, Long preset) {
        String key = VERSION_PREFIX + userId;
        if (preset != null) {
            stringRedisTemplate.opsForValue().set(key, String.valueOf(preset));
        } else {
            stringRedisTemplate.opsForValue().increment(key);
        }
        // INCR 不重置 TTL：首次创建时补设，已存在则续期，避免 key 永久驻留
        stringRedisTemplate.expire(key, Duration.ofSeconds(SystemConstants.JWT_EXPIRATION));
        return currentVersion(userId);
    }

    /**
     * 使权限缓存失效：删缓存 + 递增版本号
     * <p>
     * 删缓存负责让本进程立刻回源，递增版本号负责让「缓存仍在但已陈旧」的窗口失效。
     */
    public void invalidate(Long userId) {
        redisTemplate.delete(CACHE_PREFIX + userId);
        bumpVersion(userId);
    }

    /**
     * 读当前权限版本号（缺失返回 0，仅用于对外展示/同步判断）
     */
    public long currentVersion(Long userId) {
        Long v = rawVersion(userId);
        return v != null ? v : 0L;
    }

    /** 读原始版本号，缺失返回 null（用于区分「0」与「不存在」） */
    private Long rawVersion(Long userId) {
        String v = stringRedisTemplate.opsForValue().get(VERSION_PREFIX + userId);
        if (v == null) {
            return null;
        }
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            // 历史脏数据（曾被 Jackson 写入带类型头的值）：当作缺失，下次写入会覆盖为纯数字
            return null;
        }
    }

    /**
     * 权限快照 — 版本号 + 权限码列表
     * <p>
     * 静态内部类 + Serializable：RedisTemplate 默认 JDK 序列化，需保证可序列化。
     */
    public static class PermSnapshot implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 写入时的权限版本号 */
        private final long version;
        /** 权限码列表（已应用超管通配） */
        private final List<String> permissions;

        public PermSnapshot(long version, List<String> permissions) {
            this.version = version;
            this.permissions = permissions;
        }

        public long getVersion() {
            return version;
        }

        public List<String> getPermissions() {
            return permissions;
        }
    }
}
