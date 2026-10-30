package cn.greensmartplanet.gsc.ai.support;

import cn.greensmartplanet.gsc.ai.dto.AiToolToken;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * AI 工具令牌辅助 — 签发/校验「Python 回调 Java」用的短效 JWT
 * <p>
 * 为什么不用登录 Token：登录 Token 依赖 Redis 白名单（JwtAuthenticationFilter.isValidToken），
 * 且 Python 不持有用户登录态。工具令牌是独立签发的短效 JWT（5 分钟），只承载
 * assistantMessageId/tenantId/userId，专供一次 tool loop 内多次工具调用复用。
 * <p>
 * 安全要点：令牌只由 Java 生成、Python 透传，验签 + 过期 + token_type 三重校验，
 * 即便 Python 服务被攻破也无法伪造租户/用户身份（无签名密钥）。
 *
 * @author gsc
 */
@Slf4j
@Component
public class AiToolTokenService {

    /** 工具令牌有效期：5 分钟，覆盖一次 tool loop 的多次工具调用 */
    private static final long TTL_SECONDS = 300L;
    /** 令牌类型标记：防止拿登录 Token / 刷新 Token 冒用 */
    private static final String TOKEN_TYPE_AI_TOOL = "ai-tool";

    /** 签名密钥（与登录 JWT 同源 gsc.jwt.secret，但令牌结构独立，不共用白名单） */
    private final SecretKey key;

    public AiToolTokenService(@Value("${gsc.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 签发一次性工具令牌。
     *
     * @param assistantMessageId 本轮 assistant 消息ID（写工具提案据此定位占位气泡，确认/取消后覆盖）
     * @param tenantId           租户ID（租户隔离）
     * @param userId             用户ID（审计/归属）
     * @return 短效 JWT 字符串
     */
    public String generateToken(Long assistantMessageId, Long tenantId, Long userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + TTL_SECONDS * 1000);
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim("tenant_id", tenantId)
                .claim("user_id", userId)
                .claim("assistant_message_id", assistantMessageId)
                .claim("token_type", TOKEN_TYPE_AI_TOOL)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /**
     * 从 Authorization: Bearer xxx 头解析并校验工具令牌。
     * <p>
     * 验签失败 / 过期 / 类型不对 / 缺头一律抛 AI_TOOL_TOKEN_INVALID（不泄露细节）。
     *
     * @param authorization Bearer 头原始值
     * @return 解析出的身份上下文
     */
    public AiToolToken parseBearer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BizException(ErrorCode.AI_TOOL_TOKEN_INVALID);
        }
        return parse(authorization.substring(7).trim());
    }

    /**
     * 解析并校验令牌（验签 + token_type + 提取三段身份）。
     */
    private AiToolToken parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            // token_type 必须匹配，防拿登录 Token 冒用工具接口
            if (!TOKEN_TYPE_AI_TOOL.equals(claims.get("token_type", String.class))) {
                throw new BizException(ErrorCode.AI_TOOL_TOKEN_INVALID);
            }
            return new AiToolToken(
                    toLong(claims.get("tenant_id")),
                    toLong(claims.get("user_id")),
                    toLong(claims.get("assistant_message_id")));
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("AI 工具令牌校验失败: {}", e.getMessage());
            throw new BizException(ErrorCode.AI_TOOL_TOKEN_INVALID);
        }
    }

    /**
     * JWT claim 里的数字可能是 Integer 或 Long，统一转 Long（与 JwtTokenProvider 兼容逻辑一致）。
     */
    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof Integer i ? i.longValue() : (Long) value;
    }
}
