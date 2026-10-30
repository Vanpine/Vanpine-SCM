package cn.greensmartplanet.gsc.framework.security;

import cn.greensmartplanet.gsc.common.result.R;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;

/**
 * Spring Security 配置类 — 整个系统的"门禁系统"
 * <p>
 * 这里配置了：
 *   1. 哪些接口不需要登录就能访问（登录接口、文档页面）
 *   2. 哪些接口必须登录才能访问（其他所有接口）
 *   3. 用什么方式鉴权（JWT 无状态方式，不用 Session）
 *   4. 没有权限时返回什么（401或403的JSON）
 * <p>
 * 微服务拆分：由 GscFrameworkAutoConfiguration 导入注册；
 * 放行路径通过 gsc.security.permit-paths 配置化，各服务按需裁剪
 * （交易/交付域不需要 passport/application 放行）。
 * PermissionProvider 使用 ObjectProvider 延迟解析，无实现时降级为空权限，
 * 避免无权限实现的业务服务启动失败。
 *
 * @author gsc
 */
@Configuration
@EnableWebSecurity        // 开启 Spring Security
@EnableMethodSecurity     // 开启方法级权限控制（可以给单个方法加 @PreAuthorize）
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectProvider<PermissionProvider> permissionProvider;
    private final PermissionCache permissionCache;

    public SecurityConfig(JwtTokenProvider jwtTokenProvider,
                          ObjectMapper objectMapper,
                          RedisTemplate<String, Object> redisTemplate,
                          ObjectProvider<PermissionProvider> permissionProvider,
                          PermissionCache permissionCache) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.permissionProvider = permissionProvider;
        this.permissionCache = permissionCache;
    }

    /**
     * 安全过滤链 — 定义所有安全规则
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           @Value("${gsc.security.permit-paths:}") String[] permitPaths) throws Exception {
        var authorize = http
            // 开启 CORS：使用 CorsConfig 提供的 CorsConfigurationSource Bean，
            // Spring Security 会把 CorsFilter 插到鉴权过滤器之前，保证预检请求（OPTIONS）不被鉴权拦截
            .cors(Customizer.withDefaults())
            // 关闭 CSRF（前后端分离项目，不需要跨站请求伪造保护）
            .csrf(csrf -> csrf.disable())
            // 设置无状态方式（不用 Session，完全靠 JWT 鉴权）
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 配置哪些接口需要什么权限
            .authorizeHttpRequests(auth -> {
                // 放行所有预检请求（OPTIONS），交给 CORS 处理，不参与鉴权
                auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // ASYNC/ERROR 派发放行：SSE（SseEmitter）完成时会触发 ASYNC 派发重走一遍过滤器链，
                // 此时 SecurityContextHolder 已随原线程清理，再鉴权会误判为未认证而抛 AccessDenied；
                // 请求的 REQUEST 派发阶段已完成鉴权，此处不构成绕过。
                .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                // 这些接口不用登录就能访问（基础放行，所有服务共用）
                .requestMatchers("/api/auth/login", "/api/auth/logout", "/api/auth/refresh-token", "/api/auth/forgot-password", "/api/auth/reset-password").permitAll()
                // 支付中台回调：收款方无登录态，不能依赖 Nacos permit-paths
                .requestMatchers("/api/pub/payment/**").permitAll()
                // Knife4j 接口文档页面
                .requestMatchers("/doc.html", "/swagger-ui/**", "/v3/api-docs/**", "/webjars/**").permitAll()
                // Spring Boot 健康检查
                .requestMatchers("/actuator/health").permitAll()
                // 内部接口：服务间 Feign 调用，网关已用 InternalBlockWebFilter 拦截外部访问
                .requestMatchers("/internal/**").permitAll();
                // 配置化放行路径（如平台服务的 /api/passport/**、POST /api/applications）
                for (String path : permitPaths) {
                    if (path != null && !path.isBlank()) {
                        if (path.startsWith("POST ")) {
                            auth.requestMatchers(HttpMethod.POST, path.substring(5)).permitAll();
                        } else {
                            auth.requestMatchers(path).permitAll();
                        }
                    }
                }
                // 剩下的所有接口都必须登录后才能访问
                auth.anyRequest().authenticated();
            })
            // 把 JWT 过滤器加到 Spring Security 过滤器链中（在用户名密码过滤器之前）
            .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, redisTemplate, permissionProvider, permissionCache),
                             UsernamePasswordAuthenticationFilter.class)
            // 异常处理：没登录、没权限时返回 JSON 而不是默认的跳转页面
            .exceptionHandling(ex -> ex
                // 未登录 → 401
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.getWriter().write(
                        objectMapper.writeValueAsString(R.fail(401, "未登录或Token已过期"))
                    );
                })
                // 已登录但没权限 → 403
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.getWriter().write(
                        objectMapper.writeValueAsString(R.fail(403, "无权限访问"))
                    );
                })
            );

        return http.build();
    }

}
