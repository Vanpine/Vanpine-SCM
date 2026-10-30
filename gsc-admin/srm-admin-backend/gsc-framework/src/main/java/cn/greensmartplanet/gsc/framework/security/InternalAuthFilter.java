package cn.greensmartplanet.gsc.framework.security;

import cn.greensmartplanet.gsc.common.constant.SystemConstants;
import cn.greensmartplanet.gsc.common.result.R;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

/**
 * 内部接口鉴权过滤器 — 保护 /internal/** 不被外部直连调用
 * <p>
 * 背景：/internal/** 是服务间 Feign 专用接口（常查全量、跨租户），
 * SecurityConfig 里为 permitAll（无用户 token 的定时任务/MQ 消费也要能调）。
 * 若服务端口对外可达，攻击者可直连绕过网关调用内部接口。
 * <p>
 * 机制：只有携带正确 X-Internal-Secret 的请求才放行（网关与 Feign 调用方注入），
 * 其余一律 403。密钥泄露面极小（只存在于环境变量，不进代码/配置仓库）。
 * <p>
 * 例外：{@link #SECRET_EXEMPT_PATHS} 中的接口已有独立凭证鉴权，不走共享密钥。
 *
 * @author gsc
 */
@Slf4j
@RequiredArgsConstructor
public class InternalAuthFilter extends OncePerRequestFilter {

    private static final String INTERNAL_PREFIX = "/internal/";

    /**
     * 豁免内部密钥的路径 — 这些接口已由独立凭证鉴权，不依赖内部共享密钥。
     * <p>
     * /internal/ai/tool 的调用方是 Python AI 服务：它不是 Spring 应用，拿不到共享密钥；
     * 且该密钥一旦下发到 Python，等于把「可调用全部 /internal/**」的万能钥匙复制到第二个服务，
     * 失败爆炸半径远大于本接口本身。其身份改由 Java 签发的一次性工具令牌保证
     * （AiToolTokenService：验签 + 过期 + token_type 三重校验）。
     * <p>
     * 白名单硬编码而不放配置：豁免项属于安全边界，必须走代码评审，不能被配置中心动态放开。
     */
    private static final Set<String> SECRET_EXEMPT_PATHS = Set.of("/internal/ai/tool", "/internal/ai/parents");

    private final ObjectMapper objectMapper;
    /** 内部共享密钥（环境变量注入） */
    private final String internalSecret;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 非 /internal/** 请求、或已豁免密钥的路径，直接放行不干扰正常业务
        String uri = request.getRequestURI();
        if (!uri.startsWith(INTERNAL_PREFIX) || SECRET_EXEMPT_PATHS.contains(uri)) {
            filterChain.doFilter(request, response);
            return;
        }
        // 密钥校验不通过 → 403
        if (!hasValidSecret(request)) {
            log.warn("拒绝内部接口非法访问: uri={}, ip={}", request.getRequestURI(), request.getRemoteAddr());
            reject(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** 常量时间比较密钥，防时序侧信道 */
    private boolean hasValidSecret(HttpServletRequest request) {
        String provided = request.getHeader(SystemConstants.HEADER_INTERNAL_SECRET);
        if (!StringUtils.hasText(provided) || !StringUtils.hasText(internalSecret)) {
            return false;
        }
        return MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                internalSecret.getBytes(StandardCharsets.UTF_8));
    }

    /** 403 JSON（与全局响应格式一致） */
    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                objectMapper.writeValueAsString(R.fail(403, "无权限访问")));
    }
}
