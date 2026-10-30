package cn.greensmartplanet.gsc.ai.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * AI 域 MVC 配置 — 给 /api/admin/ai/** 挂角色拦截。
 *
 * @author gsc
 */
@Configuration
@RequiredArgsConstructor
public class AiWebMvcConfig implements WebMvcConfigurer {

    private final AiAccessInterceptor aiAccessInterceptor;

    /**
     * 注册 AI 访问拦截器。
     *
     * @param registry 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(aiAccessInterceptor).addPathPatterns("/api/admin/ai/**");
    }
}
