package cn.greensmartplanet.gsc.framework.security;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * 权限提供者默认实现 — 用 JdbcTemplate 直查共享单库的权限表
 * <p>
 * SQL 与原单体 AuthServiceImpl.getPermissions → UserMapper.selectPermissionsByUserId 完全一致，
 * 复刻确保行为不变。通过 {@code @ConditionalOnMissingBean(PermissionProvider.class)} 注册，
 * 平台服务可用自己的实现覆盖（复用 AuthServiceImpl 逻辑）。
 * <p>
 * 使用条件：业务服务只引 gsc-framework 且连共享单库时，无需写任何权限查询代码。
 *
 * @author gsc
 */
public class JdbcPermissionProvider implements PermissionProvider {

    private final JdbcTemplate jdbcTemplate;

    public JdbcPermissionProvider(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 根据 userId 查询权限码列表（join role 排除已删角色，与原单体 UserMapper.selectPermissionsByUserId SQL 一致） */
    @Override
    public List<String> getPermissions(Long userId) {
        String sql = """
                SELECT DISTINCT p.perm_code
                FROM permission p
                INNER JOIN role_permission rp ON rp.perm_id = p.id
                INNER JOIN user_role ur ON ur.role_id = rp.role_id
                INNER JOIN role r ON r.id = ur.role_id
                WHERE ur.user_id = ?
                  AND p.is_deleted = 0
                  AND r.is_deleted = 0
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("perm_code"), userId);
    }
}
