package cn.greensmartplanet.gsc.platform.support;

import cn.greensmartplanet.gsc.common.constant.SystemConstants;
import cn.greensmartplanet.gsc.platform.vo.MenuTreeVO;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 用户菜单树缓存 — 带全局版本号的菜单树快照
 * <p>
 * 缓存值结构：{@code { version: 7, menus: [...] }}
 * <p>
 * 为什么值得缓存：{@code MenuMapper.selectByUserId} 是双层嵌套子查询 + UNION + DISTINCT，
 * 且每次登录、每次 F5 刷新都要执行；菜单树结果稳定，只在「改角色菜单」或「菜单增删改」时变化。
 * <p>
 * 两级失效：
 * <ul>
 *   <li><b>改角色菜单</b> → 按 userId 精确删（该角色下所有用户），由调用方传入受影响用户</li>
 *   <li><b>菜单增删改</b> → 递增全局 {@code menuVersion}，所有用户缓存自然失效（菜单是全局共享资源）</li>
 * </ul>
 * <p>
 * 版本号用 StringRedisTemplate 读写：主模板的 Jackson 序列化会写类型头，
 * 而 INCR 是 Redis 原生命令、不走序列化器，两者混用会报
 * {@code ERR value is not an integer or out of range}（见 PermissionCache 同类问题）。
 *
 * @author gsc
 */
@Component
public class MenuTreeCache {

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    public MenuTreeCache(RedisTemplate<String, Object> redisTemplate,
                         StringRedisTemplate stringRedisTemplate) {
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 读菜单树：版本一致则用缓存，否则回源并写回
     *
     * @param userId   用户ID
     * @param provider 回源实现（查库 + 构树）
     * @return 菜单树，永不为 null
     */
    public List<MenuTreeVO> load(Long userId, Supplier<List<MenuTreeVO>> provider) {
        long version = currentVersion();
        Object cached = redisTemplate.opsForValue().get(SystemConstants.MENU_TREE_CACHE_PREFIX + userId);
        if (cached instanceof MenuSnapshot snapshot && snapshot.getVersion() == version) {
            return snapshot.getMenus();
        }
        List<MenuTreeVO> menus = provider.get();
        if (menus == null) {
            menus = new ArrayList<>();
        }
        save(userId, menus, version);
        return menus;
    }

    /** 按用户删除菜单树缓存（改角色菜单时，精确失效该角色下用户） */
    public void evict(Long userId) {
        redisTemplate.delete(SystemConstants.MENU_TREE_CACHE_PREFIX + userId);
    }

    /**
     * 菜单增删改后调用：递增全局版本号，使所有用户的菜单树缓存失效
     * <p>
     * 菜单是全局共享资源，改动影响所有租户所有用户，逐个删除不现实，故用全局版本号整体失效。
     */
    public void bumpGlobalVersion() {
        stringRedisTemplate.opsForValue().increment(SystemConstants.MENU_VERSION_KEY);
        stringRedisTemplate.expire(SystemConstants.MENU_VERSION_KEY,
                Duration.ofSeconds(SystemConstants.JWT_EXPIRATION));
    }

    /** 写回菜单树缓存，绑定当前全局版本号 */
    private void save(Long userId, List<MenuTreeVO> menus, long version) {
        redisTemplate.opsForValue().set(SystemConstants.MENU_TREE_CACHE_PREFIX + userId,
                (Serializable) new MenuSnapshot(version, menus),
                Duration.ofSeconds(SystemConstants.JWT_EXPIRATION));
    }

    /** 读当前全局版本号（缺失视为 0） */
    private long currentVersion() {
        String v = stringRedisTemplate.opsForValue().get(SystemConstants.MENU_VERSION_KEY);
        if (v == null) {
            return 0L;
        }
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return 0L;  // 脏数据按缺失处理，下次写入覆盖
        }
    }

    /**
     * 菜单树快照 — 全局版本号 + 菜单树
     */
    public static class MenuSnapshot implements Serializable {

        private static final long serialVersionUID = 1L;

        private final long version;
        private final List<MenuTreeVO> menus;

        public MenuSnapshot(long version, List<MenuTreeVO> menus) {
            this.version = version;
            this.menus = menus;
        }

        public long getVersion() {
            return version;
        }

        public List<MenuTreeVO> getMenus() {
            return menus;
        }
    }
}
