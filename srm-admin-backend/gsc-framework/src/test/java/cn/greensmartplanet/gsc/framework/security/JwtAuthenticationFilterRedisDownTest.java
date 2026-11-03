package cn.greensmartplanet.gsc.framework.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * JwtAuthenticationFilter 在 Redis 不可用时的边界测试。
 * <p>
 * 回归背景：笔记本睡眠 / 长时间空闲后，池里的 TCP 被服务端或 NAT 关闭，
 * 下一次请求拿到死连接触发 {@code Connection reset}，过滤器未捕获，整条请求变成 500 堆栈。
 * <p>
 * 这里锁住两条语义：
 * <ul>
 *   <li>返回 503（依赖不可用），不是 500（代码缺陷）</li>
 *   <li><b>不放行</b> —— 白名单校验是登出/踢人的依据，Redis 挂了也不能跳过</li>
 * </ul>
 *
 * @author gsc
 */
class JwtAuthenticationFilterRedisDownTest {

    /** 测试用 access token（内容不参与解析，签名校验已 mock 掉） */
    private static final String TOKEN = "test-access-token";

    @Test
    @DisplayName("Redis 不可用 → 503 且不放行到下游")
    void redisDown_returns503AndStopsChain() throws Exception {
        // 1. Token 本身是合法的 access token（故障点在 Redis，不在 JWT）
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        when(jwtTokenProvider.validateToken(TOKEN)).thenReturn(true);
        when(jwtTokenProvider.getTokenType(TOKEN)).thenReturn(JwtTokenProvider.TOKEN_TYPE_ACCESS);

        // 2. 白名单读取直接抛连接异常，模拟连接假死
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenThrow(new RedisConnectionFailureException("redis down"));

        @SuppressWarnings("unchecked")
        ObjectProvider<PermissionProvider> permissionProvider = mock(ObjectProvider.class);

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                jwtTokenProvider, redisTemplate, permissionProvider, mock(PermissionCache.class));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/contracts");
        request.addHeader("Authorization", "Bearer " + TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus())
                .as("Redis 不可用应返回 503，不能是 500")
                .isEqualTo(503);
        assertThat(chain.getRequest())
                .as("白名单校验失败时不得放行到下游过滤器链")
                .isNull();
        assertThat(response.getContentAsString()).contains("503");
    }
}
