package cn.greensmartplanet.gsc.framework.security;

import java.util.List;

/**
 * 权限提供者接口 — framework 层只定义接口，system 层注入 DB 实现
 * JWT 不再存储权限列表，改为从 Redis 缓存读取，miss 时通过此接口回源 DB
 *
 * @author gsc
 */
public interface PermissionProvider {

    /** 根据 userId 查询权限码列表 */
    List<String> getPermissions(Long userId);
}
