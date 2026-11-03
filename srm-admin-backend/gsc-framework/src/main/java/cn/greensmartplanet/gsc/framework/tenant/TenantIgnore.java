package cn.greensmartplanet.gsc.framework.tenant;

import java.lang.annotation.*;

/**
 * 跳过租户隔离注解 — 打在 Mapper 方法上
 * <p>
 * 正常情况：所有SQL自动追加 AND tenant_id = ?（只查自己租户的数据）
 * 加了这个注解：临时关闭租户过滤（比如超管需要查所有租户的数据）
 * <p>
 * 用法：
 * <pre>{@code
 *   @TenantIgnore
 *   @Select("SELECT * FROM user")
 *   List<User> selectAllUsers();  // 这次查询不过滤租户
 * }</pre>
 *
 * @author gsc
 */
@Documented
@Target({ElementType.METHOD})      // 只能打在方法上
@Retention(RetentionPolicy.RUNTIME) // 运行时有效
public @interface TenantIgnore {
}
