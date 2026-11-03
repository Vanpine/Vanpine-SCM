package cn.greensmartplanet.gsc.framework.tenant;

import cn.greensmartplanet.gsc.common.constant.SystemConstants;
import cn.greensmartplanet.gsc.framework.security.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 租户过滤器 — 每次HTTP请求进来时最先执行，负责解析租户信息存入 ThreadLocal
 * <p>
 * 信任模型（防伪造租户头越权）：
 *   1. 请求带 X-Internal-Secret 且与配置密钥一致 → 信任 X-Tenant-Id / X-Tenant-Code 头
 *      （只有网关和 Feign 服务间调用持有该密钥）
 *   2. 否则 → 忽略租户头，改为从 JWT 解析（JWT 有签名，不可伪造）
 * <p>
 * 这样直连服务端口、自带 X-Tenant-Id 的请求无法伪造租户身份。
 * <p>
 * 继承 OncePerRequestFilter 保证每个请求只执行一次
 * <p>
 * 微服务拆分：由 GscFrameworkAutoConfiguration 注册，不再依赖组件扫描。
 *
 * @author gsc
 */
@RequiredArgsConstructor
public class TenantFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    /** 内部共享密钥：网关与 Feign 注入 X-Internal-Secret，值一致才信任租户头 */
    private final String internalSecret;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 第1步：仅当内部密钥校验通过，才信任网关注入的租户头；否则回退从 JWT 解析
        if (hasValidInternalSecret(request)) {
            setFromHeader(request);
        } else {
            setFromJwt(request);
        }

        try {
            // 第4步：继续执行后续的过滤器和Controller
            filterChain.doFilter(request, response);
        } finally {
            // 第5步：finally 保证不管成功还是异常，都会清空 ThreadLocal
            TenantContextHolder.clear();
        }
    }

    /** 校验内部共享密钥（常量时间比较，防时序侧信道） */
    private boolean hasValidInternalSecret(HttpServletRequest request) {
        String provided = request.getHeader(SystemConstants.HEADER_INTERNAL_SECRET);
        if (!StringUtils.hasText(provided) || !StringUtils.hasText(internalSecret)) {
            return false;
        }
        return java.security.MessageDigest.isEqual(
                provided.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                internalSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** 从网关/Feign 注入的头读取租户信息（密钥已校验通过） */
    private void setFromHeader(HttpServletRequest request) {
        String headerTenantId = request.getHeader(SystemConstants.HEADER_TENANT_ID);
        if (StringUtils.hasText(headerTenantId)) {
            try {
                TenantContextHolder.setTenantId(Long.valueOf(headerTenantId));
            } catch (NumberFormatException ignored) {
                // 非法值：留空，后续按无租户处理
            }
        }
        String tenantCode = request.getHeader(SystemConstants.HEADER_TENANT_CODE);
        if (StringUtils.hasText(tenantCode)) {
            TenantContextHolder.setTenantCode(tenantCode);
        }
    }

    /** 直连单体时从 JWT 解析租户信息（原有逻辑） */
    private void setFromJwt(HttpServletRequest request) {
        // 从请求头里把 Token 取出来
        String token = extractToken(request);
        if (StringUtils.hasText(token)) {
            try {
                // 解析Token，把租户ID和租户编码取出来
                Long tenantId = jwtTokenProvider.getTenantIdFromToken(token);
                String tenantCode = jwtTokenProvider.getTenantCodeFromToken(token);
                // 放进 ThreadLocal 盒子，后面处理业务时可以随时取出
                if (tenantId != null) {
                    TenantContextHolder.setTenantId(tenantId);
                }
                if (tenantCode != null) {
                    TenantContextHolder.setTenantCode(tenantCode);
                }
            } catch (Exception ignored) {
                // Token解析失败也不报错，放行让后面的 Security 拦截器去处理403
            }
        }
    }

    /**
     * 从HTTP请求头中提取 Bearer Token
     * 请求头格式: Authorization: Bearer eyJhbGciOiJI...
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7); // 去掉"Bearer "前缀（7个字符）
        }
        return null;
    }
}
