package cn.greensmartplanet.gsc.framework.security;

import cn.greensmartplanet.gsc.framework.tenant.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT 认证过滤器 — 每次请求进来时，从Token中解析用户身份，从Redis中读取权限
 * <p>
 * 这是整个安全体系的"守门员"：
 * 1. 从请求头拿 Token
 * 2. 解析 Token → 得到 userId、tenantId、用户名
 * 3. 从 Redis 读取权限缓存（miss 时回源 DB）
 * 4. 把租户ID放入 TenantContextHolder
 * 5. 把用户信息告诉 Spring Security（这样后续 @PreAuthorize 注解才能生效）
 * <p>
 * 权限不存 JWT → 管理员修改角色权限后，清除 Redis 缓存即可实时生效，无需用户重新登录。
 * <p>
 * 微服务拆分：PermissionProvider 改用 ObjectProvider 延迟解析，
 * 无权限实现的服务降级为空权限列表（只认证不授权）。
 *
 * @author gsc
 */
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectProvider<PermissionProvider> permissionProvider;
    private final PermissionCache permissionCache;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                   RedisTemplate<String, Object> redisTemplate,
                                   ObjectProvider<PermissionProvider> permissionProvider,
                                   PermissionCache permissionCache) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.redisTemplate = redisTemplate;
        this.permissionProvider = permissionProvider;
        this.permissionCache = permissionCache;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 第1步：从请求头提取Token
        String token = extractToken(request);

        // 第2步：Token存在且有效，类型为 access，且在Redis白名单中，才解析。
        // 整段包在 try 里：Redis 连接假死时 isValidToken / loadPermissions 都会抛异常，
        // 不捕获会变成 500 堆栈（机器睡眠/空闲超时后 NAT 回收 TCP 是常见诱因）。
        try {
            if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)
                    && isAccessToken(token) && isValidToken(token, response)) {
                // 第3步：从Token中解析出用户信息
                Long userId = jwtTokenProvider.getUserIdFromToken(token);
                Long tenantId = jwtTokenProvider.getTenantIdFromToken(token);
                String tenantCode = jwtTokenProvider.getTenantCodeFromToken(token);
                String username = jwtTokenProvider.getUsernameFromToken(token);
                Long firmId = jwtTokenProvider.getFirmIdFromToken(token);
                List<String> roles = jwtTokenProvider.getRolesFromToken(token);

                // 第4步：从 Redis 读取权限缓存，miss 时回源 DB 并写入缓存
                List<String> permissions = loadPermissions(userId);

                // 第5步：把租户信息放入 ThreadLocal
                if (tenantId != null) TenantContextHolder.setTenantId(tenantId);
                if (tenantCode != null) TenantContextHolder.setTenantCode(tenantCode);

                // 第6步：权限列表转 Spring Security 格式
                List<SimpleGrantedAuthority> authorities = permissions.stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

                // 第7步：构建 JwtUserDetails 并设置认证
                JwtUserDetails userDetails = JwtUserDetails.builder()
                        .userId(userId).tenantId(tenantId).tenantCode(tenantCode)
                        .username(username).firmId(firmId).roles(roles).authorities(authorities).build();
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            // Redis 不可用 → 白名单校验做不了，绝不能放行（登出/踢人都依赖白名单，放行等于绕过）。
            // 返回 503 而不是 500：这是依赖不可用，不是代码缺陷，前端可提示稍后重试。
            log.warn("Redis 不可用，拒绝本次鉴权: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":503,\"message\":\"服务暂时不可用，请稍后重试\"}");
            return;
        }

        // 第9步：无论如何都继续往下走（没Token也放行，由SecurityConfig决定是否拦截）
        filterChain.doFilter(request, response);
    }

    /**
     * 加载权限：版本一致则用缓存，否则回源 DB 并回写
     * <p>
     * 版本比对交给 PermissionCache —— 权限变更时递增 permsVersion 即可判定失效，
     * 不依赖「删缓存」这一副作用，避免缓存仍在但权限已陈旧的窗口。
     */
    private List<String> loadPermissions(Long userId) {
        return permissionCache.load(userId, permissionProvider.getIfAvailable());
    }

    /**
     * 类型校验：鉴权过滤器只认 access token，refresh token 不能当登录凭证用
     */
    private boolean isAccessToken(String token) {
        return JwtTokenProvider.TOKEN_TYPE_ACCESS.equals(jwtTokenProvider.getTokenType(token));
    }

    /**
     * 白名单校验：Token 必须在 Redis 中存在且一致才有效
     * 无效时检查是否有踢出原因，有则返回具体错误消息给前端
     */
    private boolean isValidToken(String token, HttpServletResponse response) throws IOException {
        Long userId = jwtTokenProvider.getUserIdFromToken(token);
        String storedToken = (String) redisTemplate.opsForValue().get("accessToken:" + userId);
        if (token.equals(storedToken)) return true;  // Token 有效
        // Token 无效 → 检查是否有踢出原因
        String reason = (String) redisTemplate.opsForValue().get("kickReason:" + userId);
        if (reason != null) {
            redisTemplate.delete("kickReason:" + userId);  // 只提示一次
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"" + reason + "\"}");
            return false;
        }
        return false;  // 普通过期，无特殊原因
    }

    /**
     * 从请求头提取 Bearer Token
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
