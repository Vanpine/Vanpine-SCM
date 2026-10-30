package cn.greensmartplanet.gsc.framework.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * RestClient 全局配置 — 项目中唯一的 HTTP 客户端
 *
 * <p>两层设计：
 * <ol>
 *   <li>{@link RestClientCustomizer} — 全局超时，所有 {@code RestClient.Builder} 自动继承</li>
 *   <li>{@code aiRestClient} Bean — AI 服务专用，预设 baseUrl，供共享同一 URL 的类注入</li>
 * </ol>
 *
 * <p>多态扩展：换 {@code HttpComponentsClientHttpRequestFactory}（连接池）只需改 customizer。
 *
 * @author gsc
 */
@Configuration
public class RestClientConfig {

    private static final int CONNECT_TIMEOUT = 5_000;
    private static final int READ_TIMEOUT = 120_000;

    /** 全局超时 — 所有 RestClient.Builder 都生效 */
    @Bean
    public RestClientCustomizer restClientCustomizer() {
        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(CONNECT_TIMEOUT);
            factory.setReadTimeout(READ_TIMEOUT);
            builder.requestFactory(factory);
        };
    }

    /** AI 服务专用 — 共享同一 baseUrl，供 AiMatchStrategy / ProfileSyncService 注入 */
    @Bean
    public RestClient aiRestClient(RestClient.Builder builder,
                                   @Value("${gsc.ai.service.url:http://localhost:8100}") String aiServiceUrl,
                                   @Value("${gsc.ai.service.token:}") String aiServiceToken) {
        // 统一带 X-API-Key 鉴权头：Python 侧所有非 /health 端点强制校验，空 token 时头为空会被拒绝
        return builder
                .baseUrl(aiServiceUrl)
                .defaultHeader("X-API-Key", aiServiceToken)
                .build();
    }
}
