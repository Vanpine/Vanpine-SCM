package cn.greensmartplanet.gsc.gateway.filter;

import cn.greensmartplanet.gsc.common.constant.SystemConstants;
import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.gateway.security.GatewayJwtParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * 网关鉴权过滤器 — 轻量入口校验
 * <p>
 * 职责边界：
 * <ul>
 *   <li>放行：登录/护照/入驻公开接口、OPTIONS 预检、文档、健康检查（与下游 SecurityConfig 放行一致）</li>
 *   <li>带 Token：验签通过 → 注入 X-Tenant-Id / X-Tenant-Code 头 → 透传下游</li>
 *   <li>无 Token / 验签失败：拒绝 401 JSON（格式与下游一致，前端无感）</li>
 * </ul>
 * 完整鉴权（Redis 白名单、权限）仍由下游服务执行——网关只挡明显非法请求，不重复实现白名单。
 * <p>
 * 顺序：必须早于 RouteToRequestUrlFilter（10000）执行。
 *
 * @author gsc
 */
@Slf4j
@Component
public class GatewayAuthFilter implements GlobalFilter, Ordered {

    /** 公开路径（与各服务 SecurityConfig 放行规则保持一致） */
    private static final List<String> PERMIT_PATHS = List.of(
            "/api/auth/login", "/api/auth/logout", "/api/auth/refresh-token",
            "/api/auth/forgot-password", "/api/auth/reset-password",
            "/api/passport", "/api/applications", "/api/pub/payment",
            "/doc.html", "/swagger-ui", "/v3/api-docs", "/webjars",
            "/actuator/health");

    private final GatewayJwtParser jwtParser;
    private final ObjectMapper objectMapper;
    /** 内部共享密钥：注入 X-Internal-Secret，下游校验通过才信任租户头 */
    private final String internalSecret;

    public GatewayAuthFilter(GatewayJwtParser jwtParser, ObjectMapper objectMapper,
                             @Value("${gsc.internal.secret:}") String internalSecret) {
        this.jwtParser = jwtParser;
        this.objectMapper = objectMapper;
        this.internalSecret = internalSecret;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // 1. OPTIONS 预检放行
        if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }
        // 2. 公开路径放行（护照/入驻用前缀匹配：/api/passport/**、/api/applications/**）
        if (PERMIT_PATHS.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        // 3. 提取并验签 Token
        String token = extractToken(exchange);
        if (!StringUtils.hasText(token)) {
            return reject(exchange, 401, "未登录或Token已过期");
        }
        Claims claims = jwtParser.parse(token);
        if (claims == null) {
            return reject(exchange, 401, "未登录或Token已过期");
        }

        // 5. 验签通过 → 注入租户头 + 内部密钥，下游 TenantFilter 校验密钥通过才信任租户头
        Long tenantId = jwtParser.tenantIdOf(claims);
        String tenantCode = jwtParser.tenantCodeOf(claims);
        var mutated = exchange.getRequest().mutate();
        if (StringUtils.hasText(internalSecret)) {
            mutated.header(SystemConstants.HEADER_INTERNAL_SECRET, internalSecret);
        }
        if (tenantId != null) {
            mutated.header(SystemConstants.HEADER_TENANT_ID, String.valueOf(tenantId));
        }
        if (tenantCode != null) {
            mutated.header(SystemConstants.HEADER_TENANT_CODE, tenantCode);
        }
        return chain.filter(exchange.mutate().request(mutated.build()).build());
    }

    /** 拒绝请求：JSON 格式与下游 GlobalExceptionHandler 一致 */
    private Mono<Void> reject(ServerWebExchange exchange, int code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(org.springframework.http.HttpStatusCode.valueOf(code == 401 ? 401 : 403));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        R<Void> body = R.fail(code, message);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            log.error("网关拒绝响应序列化失败", e);
            return response.setComplete();
        }
    }

    /** 从 Authorization 头提取 Bearer Token */
    private String extractToken(ServerWebExchange exchange) {
        String bearer = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }

    @Override
    public int getOrder() {
        // 早于路由（RouteToRequestUrlFilter=10000）执行
        return 9000;
    }
}
