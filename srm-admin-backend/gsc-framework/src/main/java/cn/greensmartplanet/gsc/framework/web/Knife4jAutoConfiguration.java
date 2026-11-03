package cn.greensmartplanet.gsc.framework.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Knife4j / OpenAPI3 公共配置 — 各服务无需自建 Knife4jConfig
 * <p>
 * 仅当类路径存在 knife4j（io.swagger.v3.oas.models.OpenAPI）时注册；
 * 服务侧如需自定义文档信息，自建 OpenAPI Bean 覆盖（@ConditionalOnMissingBean）。
 *
 * @author gsc
 */
@AutoConfiguration
@ConditionalOnClass(OpenAPI.class)
public class Knife4jAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info().title("Global SCM 后台管理系统").version("1.0.0"))
                .components(new Components()
                        .addSecuritySchemes("bearer", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
