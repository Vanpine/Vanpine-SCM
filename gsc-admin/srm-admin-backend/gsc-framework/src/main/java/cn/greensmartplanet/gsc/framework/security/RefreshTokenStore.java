package cn.greensmartplanet.gsc.framework.security;

import cn.greensmartplanet.gsc.common.constant.SystemConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

/**
 * 刷新令牌存储 — 带原子轮换的 refreshToken 白名单
 * <p>
 * 为什么需要原子性：轮换是「读旧值 → 比对 → 写新值」，若分三步执行，
 * 两个并发刷新请求会同时读到旧值、同时通过校验，各自写入新值互相覆盖，
 * 导致其中一个客户端持有的新 token 在 Redis 里不存在，下次刷新即被判为
 * 「旧 token 复用」而清空登录态 —— 正常用户被误伤。
 * <p>
 * 用 Lua 脚本在 Redis 端一次完成比对与替换，杜绝该窗口。
 * <p>
 * 用 StringRedisTemplate 而非主 RedisTemplate：refreshToken 是纯字符串，
 * 主模板的 Jackson 序列化会写入类型头与引号，而 Lua 在 Redis 端只看原始字节，
 * 混用会让脚本里的比对失效。
 * <p>
 * Bean 由 RedisConfig 声明（本包不在业务服务的 @ComponentScan 范围内，标 @Component 不会被注册）。
 *
 * @author gsc
 */
public class RefreshTokenStore {

    /** 比对并替换：仅当当前值等于传入的旧值时才写入新值，返回 1 成功 / 0 不匹配 */
    private static final RedisScript<Long> CAS_ROTATE = new DefaultRedisScript<>(
            """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])
                return 1
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    public RefreshTokenStore(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 原子轮换：旧令牌必须与 Redis 当前值一致才替换为新令牌
     *
     * @param userId         用户ID
     * @param oldRefreshToken 客户端持有的刷新令牌
     * @param newRefreshToken 新的刷新令牌
     * @return true=轮换成功；false=不匹配（并发刷新或旧令牌已失效）
     */
    public boolean rotate(Long userId, String oldRefreshToken, String newRefreshToken) {
        Long result = stringRedisTemplate.execute(CAS_ROTATE,
                List.of(key(userId)),
                oldRefreshToken,
                newRefreshToken,
                String.valueOf(SystemConstants.JWT_REFRESH_EXPIRATION));
        return result != null && result == 1L;
    }

    /** 写入刷新令牌白名单（登录/轮换成功后调用） */
    public void save(Long userId, String refreshToken) {
        stringRedisTemplate.opsForValue().set(key(userId), refreshToken,
                Duration.ofSeconds(SystemConstants.JWT_REFRESH_EXPIRATION));
    }

    /** 读取当前有效刷新令牌，无则返回 null */
    public String get(Long userId) {
        return stringRedisTemplate.opsForValue().get(key(userId));
    }

    /** 删除刷新令牌（登出、改密、踢下线时调用） */
    public void remove(Long userId) {
        stringRedisTemplate.delete(key(userId));
    }

    private String key(Long userId) {
        return SystemConstants.REFRESH_TOKEN_PREFIX + userId;
    }
}
