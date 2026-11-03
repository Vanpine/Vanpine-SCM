package cn.greensmartplanet.gsc.framework.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * JWT Token 工具类 — 负责 Token 的生成、解析、校验
 * <p>
 * JWT 就像一个"电子身份证"：
 * 用户登录成功 → 后端生成一个 Token 发给前端
 * 前端每次请求带上 Token → 后端解析 Token 就知道"你是谁、属于哪个租户、有什么权限"
 * <p>
 * Token 结构（payload 部分）：
 * subject = 用户ID
 * tenant_id = 租户ID
 * tenant_code = 租户编码
 * username = 用户名
 * roles = ["TENANT_ADMIN", "BUYER"]
 * 权限不存 JWT，改为从 Redis 缓存读取（变更实时生效）
 *
 * @author gsc
 */
@Slf4j
public class JwtTokenProvider {

    /**
     * 登录Token类型标记（payload 里的 token_type claim）
     */
    public static final String TOKEN_TYPE_ACCESS = "access";
    /**
     * 刷新Token类型标记
     */
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    /**
     * 用于签名和验签的密钥（从配置文件读取，有默认值）
     */
    private final SecretKey key;
    /**
     * 登录Token有效期（秒），默认2小时
     */
    private final long expiration;
    /**
     * 刷新Token有效期（秒），默认7天
     */
    private final long refreshExpiration;

    public JwtTokenProvider(
            @Value("${gsc.jwt.secret}") String secret,
            @Value("${gsc.jwt.expiration}") long expiration,
            @Value("${gsc.jwt.refresh-expiration}") long refreshExpiration) {
        // 把密钥字符串转成 Java 加密用的 SecretKey 对象
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
        this.refreshExpiration = refreshExpiration;
    }

    /**
     * 生成"登录Token"（AccessToken）
     * 用户登录成功后调用——权限不存 JWT，改存 Redis 缓存，变更实时生效
     *
     * @param userId     用户ID
     * @param tenantId   租户ID
     * @param tenantCode 租户编码
     * @param username   用户名
     * @param roles      角色列表 ["TENANT_ADMIN"]
     * @return 签好的 JWT 字符串
     */
    public String generateToken(Long userId, Long tenantId, String tenantCode,
                                String username, List<String> roles, Long firmId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration * 1000);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim("tenant_id", tenantId)
                .claim("tenant_code", tenantCode)
                .claim("username", username)
                .claim("roles", roles)
                .claim("firm_id", firmId)
                .claim("token_type", TOKEN_TYPE_ACCESS)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }


    /**
     * 生成"刷新Token"（RefreshToken）
     * 登录Token过期后，用这个Token换一个新的，避免用户频繁重新登录
     */
    public String generateRefreshToken(Long userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshExpiration * 1000);
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim("token_type", TOKEN_TYPE_REFRESH)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    /**
     * 解析 Token — 把 Token 字符串拆开，取出里面存的所有字段
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)       // 用密钥验签
                .build()
                .parseSignedClaims(token)  // 解析
                .getPayload();             // 取 payload
    }

    /**
     * 从 Token 中取出用户ID
     */
    public Long getUserIdFromToken(String token) {
        return Long.valueOf(parseToken(token).getSubject());
    }

    /**
     * 从 Token 中取出租户ID（注意：JSON 里的数字可能是 Integer 或 Long，都要兼容）
     */
    public Long getTenantIdFromToken(String token) {
        Object val = parseToken(token).get("tenant_id");
        if (val == null) return null;
        return val instanceof Integer ? ((Integer) val).longValue() : (Long) val;
    }

    /**
     * 从 Token 中取出租户编码
     */
    public String getTenantCodeFromToken(String token) {
        return (String) parseToken(token).get("tenant_code");
    }

    /**
     * 从 Token 中取出用户名
     */
    public String getUsernameFromToken(String token) {
        return (String) parseToken(token).get("username");
    }

    /**
     * 从 Token 中取出事务所ID
     */
    public Long getFirmIdFromToken(String token) {
        Object val = parseToken(token).get("firm_id");
        if (val == null) return null;
        return val instanceof Integer ? ((Integer) val).longValue() : (Long) val;
    }

    /**
     * 从 Token 中取出角色列表
     */
    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        Object roles = parseToken(token).get("roles");
        if (roles instanceof List) return (List<String>) roles;
        return Collections.emptyList(); // 没角色就返回空列表
    }

    /**
     * 从 Token 中取出类型标记（access / refresh）
     */
    public String getTokenType(String token) {
        return (String) parseToken(token).get("token_type");
    }

    /**
     * 验证 Token 是否有效
     * 有效 → true，过期/被篡改/格式不对 → false
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token); // 能解析成功说明Token没问题
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT验证失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 拿到 Token 的过期时间（用于判断还有多久过期）
     */
    public Date getExpirationDate(String token) {
        return parseToken(token).getExpiration();
    }
}
