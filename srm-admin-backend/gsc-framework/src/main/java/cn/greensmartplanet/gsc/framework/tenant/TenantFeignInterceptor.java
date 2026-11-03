package cn.greensmartplanet.gsc.framework.tenant;

import cn.greensmartplanet.gsc.common.constant.SystemConstants;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Feign 服务间调用租户透传拦截器 — 把当前请求的租户信息与 JWT 转发给下游服务
 * <p>
 * 租户透传链路（微服务拆分规范）：
 * 网关/上游注入 X-Internal-Secret + X-Tenant-Id → 下游 TenantFilter 校验密钥后存 ThreadLocal
 * → 本拦截器透传 X-Internal-Secret / X-Tenant-Id / X-Tenant-Code + Authorization
 * → 消费侧 TenantFilter 校验密钥通过才信任租户头。
 * <p>
 * 密钥由本拦截器自行注入（不依赖上游是否透传），保证服务间调用始终带密钥。
 * <p>
 * 注册方式：framework 自动装配（@ConditionalOnClass(RequestInterceptor.class)），
 * 引入 openfeign 依赖的服务自动获得透传能力，零配置。
 *
 * @author gsc
 */
@RequiredArgsConstructor
public class TenantFeignInterceptor implements RequestInterceptor {

    /** 内部共享密钥：Feign 调用方自行注入，下游校验通过才信任租户头 */
    private final String internalSecret;

    @Override
    public void apply(RequestTemplate template) {
        // 服务间调用必须带内部密钥，否则下游 InternalAuthFilter / TenantFilter 会拒绝
        if (StringUtils.hasText(internalSecret)) {
            template.header(SystemConstants.HEADER_INTERNAL_SECRET, internalSecret);
        }
        // 透传当前 HTTP 请求的租户头（网关已注入），无则跳过
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            HttpServletRequest request = servletAttrs.getRequest();
            if (request != null) {
                String tenantId = request.getHeader(SystemConstants.HEADER_TENANT_ID);
                if (tenantId != null) {
                    template.header(SystemConstants.HEADER_TENANT_ID, tenantId);
                }
                String tenantCode = request.getHeader(SystemConstants.HEADER_TENANT_CODE);
                if (tenantCode != null) {
                    template.header(SystemConstants.HEADER_TENANT_CODE, tenantCode);
                }
                String authorization = request.getHeader("Authorization");
                if (authorization != null) {
                    template.header("Authorization", authorization);
                }
            }
        }
    }
}
