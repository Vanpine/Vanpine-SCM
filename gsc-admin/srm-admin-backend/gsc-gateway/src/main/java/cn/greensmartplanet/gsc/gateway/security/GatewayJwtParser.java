package cn.greensmartplanet.gsc.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * 网关 JWT 解析器 — 轻量验签 + 提取租户信息
 * <p>
 * 与 framework 的 JwtTokenProvider 职责差异：网关只做验签和读取 claims，
 * 不做生成、不做 Redis 白名单校验（那是下游服务的完整鉴权）。
 * WebFlux 栈不能引 servlet 栈的 gsc-framework，此处 ~40 行解析是栈边界隔离的合理重复。
 * <p>
 * 密钥必须与全栈一致（Nacos 共享配置 gsc-shared.yaml 的 gsc.jwt.secret）。
 *
 * @author gsc
 */

@Slf4j
@Component
public class GatewayJwtParser {

    private SecretKey key;

    @Value("${gsc.jwt.secret}")
    private String secret;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 解析并验签 Token，非法/过期返回 null（不抛异常，由调用方决定放行还是拒绝）
     */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.debug("网关JWT解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 claims 提取租户ID（JSON 数字兼容 Integer/Long）
     */
    public Long tenantIdOf(Claims claims) {
        Object val = claims.get("tenant_id");
        if (val == null) return null;
        return val instanceof Integer ? ((Integer) val).longValue() : (Long) val;
    }

    /**
     * 从 claims 提取租户编码
     */
    public String tenantCodeOf(Claims claims) {
        return (String) claims.get("tenant_code");
    }
}
