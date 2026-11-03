package cn.greensmartplanet.gsc.gateway.filter;

import cn.greensmartplanet.gsc.common.result.R;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 内部接口阻断过滤器 — /internal/** 一律拒绝
 * <p>
 * 为什么用 WebFilter 而不是 GlobalFilter：GlobalFilter 只在路由匹配成功后执行，
 * 而 /internal/** 不匹配任何路由（路由只认 /api/**），请求会在 HandlerMapping 层直接 404。
 * WebFilter 位于 Spring WebFlux 过滤器链，先于 HandlerMapping，才能稳定拦截。
 * <p>
 * 内部接口只允许服务间经内网服务发现（Feign）调用，从网关入口进来的请求一律视为非法。
 *
 * @author gsc
 */
@Slf4j
@Component
public class InternalBlockWebFilter implements WebFilter, Ordered {

    private static final String INTERNAL_PREFIX = "/internal/";

    private final ObjectMapper objectMapper;

    public InternalBlockWebFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (exchange.getRequest().getPath().value().startsWith(INTERNAL_PREFIX)) {
            log.warn("拒绝内部接口外部访问: {}", exchange.getRequest().getPath().value());
            return reject(exchange);
        }
        return chain.filter(exchange);
    }

    /**
     * 403 JSON（与全局响应格式一致）
     */
    private Mono<Void> reject(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatusCode.valueOf(403));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(R.fail(403, "无权限访问"));
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            log.error("内部接口阻断响应序列化失败", e);
            return response.setComplete();
        }
    }

    @Override
    public int getOrder() {
        // WebFilter 最高优先级，先于 HandlerMapping（-1）执行
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
