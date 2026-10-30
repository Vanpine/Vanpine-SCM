package cn.greensmartplanet.gsc.framework.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS 跨域配置 — 允许前端（不同端口/域名）调用后端接口
 * <p>
 * 为什么需要？前端开发时跑在 localhost:5173，后端跑在 localhost:8081，
 * 浏览器会认为这是"跨域请求"直接拦截，需要后端声明"我允许你跨域访问我"。
 * <p>
 * 关键：这里暴露的是 {@link CorsConfigurationSource} Bean，由 SecurityConfig 的
 * http.cors(...) 接管。这样 Spring Security 会把 CorsFilter 插到鉴权过滤器之前，
 * 保证预检请求（OPTIONS，浏览器不带 Token）不会被鉴权拦截而丢失 CORS 响应头。
 * <p>
 * 安全约定：生产环境禁止使用 "*" 通配来源，必须在 application-prod.yml 中
 * 通过 gsc.cors.allowed-origins 指定具体域名，配合 allowCredentials(true) 才安全。
 *
 * @author gsc
 */
@Configuration
public class CorsConfig {

    /** 允许的前端来源（按环境从 application-{profile}.yml 的 gsc.cors.allowed-origins 读取，多个用逗号分隔；默认本地开发来源） */
    @Value("${gsc.cors.allowed-origins:http://localhost:5173,http://localhost:5174}")
    private List<String> allowedOrigins;

    @Bean
    @ConditionalOnMissingBean(CorsConfigurationSource.class)
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // 指定具体的前端来源（生产禁用 "*"，避免与 allowCredentials 同时开启造成安全风险）
        config.setAllowedOrigins(allowedOrigins);
        // 允许这几种HTTP方法
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        // 允许前端携带的请求头（含 Authorization，用于 Token 鉴权）
        config.setAllowedHeaders(Arrays.asList(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, "X-Requested-With"));
        // 允许携带 Cookie/Token
        config.setAllowCredentials(true);
        // 预检请求的缓存时间（3600秒 = 1小时）
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config); // 所有接口都生效
        return source;
    }
}
