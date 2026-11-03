package cn.greensmartplanet.gsc.gateway.filter;

import cn.greensmartplanet.gsc.common.result.R;
import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 网关限流过滤器 — Sentinel 入口流量控制
 * <p>
 * 用 sentinel-core 直连（BOM 管版本），不用旧的 gateway adapter：
 * adapter 与 Gateway 4.x 存在兼容风险，直连 SphU 入口最稳妥。
 * <p>
 * 默认规则：
 * <ul>
 *   <li>全量 /api/** QPS 1000 兜底（可配置 gsc.limit.qps）</li>
 *   <li>登录接口按客户端 IP 独立限流（可配置 gsc.limit.login-qps），防密码爆破</li>
 * </ul>
 * 触发限流时返回 429 JSON，前端拦截器可感知（code 429 走通用错误弹窗）。
 * 后续接入 Sentinel 控制台后可动态下发规则，此处兜底规则仍保留。
 *
 * @author gsc
 */
@Slf4j
@Component
public class GatewayRateLimitFilter implements GlobalFilter, Ordered {

    /** 限流资源名（全量 /api/**） */
    private static final String RESOURCE_API = "gsc-gateway-api";
    /** 登录接口限流资源名 — 单独资源，按来源 IP 独立计数 */
    private static final String RESOURCE_LOGIN = "gsc-gateway-login";
    /** 登录接口路径 */
    private static final String LOGIN_PATH = "/api/auth/login";

    private final ObjectMapper objectMapper;

    @Value("${gsc.limit.qps:1000}")
    private int limitQps;

    /** 登录接口每 IP 每秒允许的请求数（防密码爆破） */
    @Value("${gsc.limit.login-qps:5}")
    private int loginQps;

    public GatewayRateLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 启动时加载兜底流控规则（QPS 阈值，非预热模式） */
    @PostConstruct
    public void initFlowRules() {
        FlowRule rule = new FlowRule();
        rule.setResource(RESOURCE_API);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(limitQps);
        // 登录规则：limitApp = "other" 表示按来源（origin）分别统计，
        // 配合 filter 里传入的客户端 IP，即「每个 IP 独立限流」，
        // 避免单个 IP 爆破拖垮全体用户，也避免全局阈值被一个攻击者打满
        FlowRule loginRule = new FlowRule();
        loginRule.setResource(RESOURCE_LOGIN);
        loginRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        loginRule.setCount(loginQps);
        loginRule.setLimitApp("other");
        FlowRuleManager.loadRules(List.of(rule, loginRule));
        log.info("Sentinel 网关限流规则已加载: {} qps={}, {} qps={}/IP",
                RESOURCE_API, limitQps, RESOURCE_LOGIN, loginQps);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 只对业务接口限流，健康检查/文档不限
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/api/")) {
            return chain.filter(exchange);
        }
        boolean isLogin = LOGIN_PATH.equals(path);
        String resource = isLogin ? RESOURCE_LOGIN : RESOURCE_API;
        Entry entry = null;
        try {
            // 登录接口带 origin（客户端 IP）：配合 limitApp="other" 实现按 IP 独立计数
            entry = isLogin
                    ? SphU.entry(resource, EntryType.IN, 1, clientIp(exchange))
                    : SphU.entry(resource, EntryType.IN);
            return chain.filter(exchange);
        } catch (BlockException e) {
            log.warn("网关限流触发: path={}, resource={}", path, resource);
            return reject(exchange, isLogin);
        } finally {
            if (entry != null) {
                entry.exit();
            }
        }
    }

    /**
     * 提取客户端真实 IP — 经反向代理时 RemoteAddress 是代理地址，需优先读转发头
     */
    private String clientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String realIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        var remote = exchange.getRequest().getRemoteAddress();
        return remote != null ? remote.getAddress().getHostAddress() : "unknown";
    }

    /** 429 限流响应（JSON 格式与统一响应一致） */
    private Mono<Void> reject(ServerWebExchange exchange, boolean isLogin) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatusCode.valueOf(429));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String message = isLogin ? "登录尝试过于频繁，请稍后再试" : "请求过于频繁，请稍后再试";
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(R.fail(429, message));
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            log.error("限流响应序列化失败", e);
            return response.setComplete();
        }
    }

    @Override
    public int getOrder() {
        // 鉴权通过后限流（9000 < 9100）
        return 9100;
    }
}
