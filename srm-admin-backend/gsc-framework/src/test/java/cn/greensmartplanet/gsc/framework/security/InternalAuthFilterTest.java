package cn.greensmartplanet.gsc.framework.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * InternalAuthFilter 单元测试 — 锁住内部接口的密钥校验边界。
 * <p>
 * 回归重点：/internal/ai/tool 是 Python AI 服务的回调入口，调用方不是 Spring 应用、
 * 不持有 X-Internal-Secret，必须豁免；其余 /internal/** 仍须密钥，否则即为放行漏洞。
 *
 * @author gsc
 */
class InternalAuthFilterTest {

    /** 测试用内部密钥 */
    private static final String SECRET = "test-internal-secret";

    /** 被测过滤器（ObjectMapper 仅用于 403 JSON 序列化） */
    private final InternalAuthFilter filter = new InternalAuthFilter(new ObjectMapper(), SECRET);

    @Test
    @DisplayName("AI 工具回调无密钥放行 —— 回归：曾因密钥校验返回 403 导致工具调用全部失效")
    void aiToolCallback_withoutSecret_passes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/ai/tool");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).as("应放行到下游过滤器链").isNotNull();
    }

    @Test
    @DisplayName("其他内部接口无密钥仍返回 403")
    void otherInternalPath_withoutSecret_isForbidden() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/ai/delivery/list");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).as("不应放行到下游").isNull();
    }

    @Test
    @DisplayName("其他内部接口带正确密钥放行")
    void otherInternalPath_withValidSecret_passes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/ai/delivery/list");
        request.addHeader("X-Internal-Secret", SECRET);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("其他内部接口带错误密钥返回 403")
    void otherInternalPath_withWrongSecret_isForbidden() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/ai/delivery/list");
        request.addHeader("X-Internal-Secret", "wrong-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("非 /internal/** 请求不参与密钥校验")
    void nonInternalPath_passes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("密钥未配置时内部接口一律 403，不回退为放行")
    void blankConfiguredSecret_isForbidden() throws Exception {
        InternalAuthFilter filterWithoutSecret = new InternalAuthFilter(new ObjectMapper(), "");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/ai/delivery/list");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filterWithoutSecret.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }
}
