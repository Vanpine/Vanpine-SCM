package cn.greensmartplanet.gsc.framework.security;

import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 安全相关 Bean 配置
 * 与 SecurityConfig 分离，避免循环依赖：
 * SecurityConfig 需要 PermissionProvider（实现在 system 模块）
 * → system 模块需要 PasswordEncoder（在此定义）
 * → 如果把 PasswordEncoder 放在 SecurityConfig 中，会形成 AuthServiceImpl ↔ SecurityConfig 循环
 * → 拆出来之后：SecurityConfig → PermissionProvider → AuthServiceImpl → PasswordEncoder（无循环）
 * <p>
 * 微服务拆分后由 GscFrameworkAutoConfiguration 导入注册，不再依赖组件扫描。
 *
 * @author gsc
 */
public class SecurityBeanConfig {

    /** BCrypt 密码加密器 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
