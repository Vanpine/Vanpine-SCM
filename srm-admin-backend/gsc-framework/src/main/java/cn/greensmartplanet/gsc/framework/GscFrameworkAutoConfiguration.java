package cn.greensmartplanet.gsc.framework;

import cn.greensmartplanet.gsc.framework.audit.AuditLogAspect;
import cn.greensmartplanet.gsc.framework.audit.AuditLogSink;
import cn.greensmartplanet.gsc.framework.audit.MqAuditLogSink;
import cn.greensmartplanet.gsc.framework.async.AsyncExecutorConfig;
import cn.greensmartplanet.gsc.framework.auth.PassportClient;
import cn.greensmartplanet.gsc.framework.cache.RedisConfig;
import cn.greensmartplanet.gsc.framework.mail.MailService;
import cn.greensmartplanet.gsc.framework.mq.RabbitMqInfrastructureConfig;
import cn.greensmartplanet.gsc.framework.oss.OssService;
import cn.greensmartplanet.gsc.framework.security.InternalAuthFilter;
import cn.greensmartplanet.gsc.framework.security.JdbcPermissionProvider;
import cn.greensmartplanet.gsc.framework.security.JwtTokenProvider;
import cn.greensmartplanet.gsc.framework.security.PermissionProvider;
import cn.greensmartplanet.gsc.framework.security.SecurityBeanConfig;
import cn.greensmartplanet.gsc.framework.security.SecurityConfig;
import cn.greensmartplanet.gsc.framework.tenant.TenantFeignInterceptor;
import cn.greensmartplanet.gsc.framework.tenant.TenantFilter;
import cn.greensmartplanet.gsc.framework.weather.OpenMeteoWeatherClient;
import cn.greensmartplanet.gsc.framework.weather.WeatherClient;
import cn.greensmartplanet.gsc.framework.web.CorsConfig;
import cn.greensmartplanet.gsc.framework.web.GlobalExceptionHandler;
import cn.greensmartplanet.gsc.framework.web.RestClientConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * gsc-framework 自动装配入口 — 微服务拆分后，各服务不再需要 scanBasePackages 扫框架包
 * <p>
 * 设计要点：
 * <ul>
 *   <li>通过 META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports 自动加载</li>
 *   <li>所有 Bean 带 {@code @ConditionalOnMissingBean}，业务服务可覆盖（如平台服务用自己的 PermissionProvider）</li>
 *   <li>Mail/Oss/Passport 客户端按配置属性存在才注册（{@code @ConditionalOnProperty}），
 *       交易/交付等无相关配置的服务不会因缺属性启动失败</li>
 *   <li>审计切面与权限默认实现随框架自动生效，业务服务零额外配置</li>
 * </ul>
 *
 * @author gsc
 */
@AutoConfiguration
@EnableAspectJAutoProxy
@EnableAsync
@EnableScheduling
// 必须排在 Boot 默认自动配置之前：先注册自定义 redisTemplate/SecurityFilterChain/CorsConfigurationSource，
// 让 RedisAutoConfiguration / SecurityAutoConfiguration / WebMvcAutoConfiguration 的
// @ConditionalOnMissingBean 跳过默认实现（复刻原组件扫描的注册时序）
@AutoConfigureBefore({RedisAutoConfiguration.class, SecurityAutoConfiguration.class, WebMvcAutoConfiguration.class})
@Import({RabbitMqInfrastructureConfig.class, SecurityConfig.class, SecurityBeanConfig.class, RedisConfig.class,
        RestClientConfig.class, CorsConfig.class, GlobalExceptionHandler.class, AsyncExecutorConfig.class})
public class GscFrameworkAutoConfiguration {

    /**
     * JWT 工具 — 所有服务共用（密钥走 Nacos 共享配置 gsc-shared.yaml）
     */
    @Bean
    @ConditionalOnMissingBean
    public JwtTokenProvider jwtTokenProvider(
            @Value("${gsc.jwt.secret}") String secret,
            @Value("${gsc.jwt.expiration}") long expiration,
            @Value("${gsc.jwt.refresh-expiration}") long refreshExpiration) {
        return new JwtTokenProvider(secret, expiration, refreshExpiration);
    }

    /**
     * 密码加密器 — 与单体一致的 BCrypt（若 SecurityBeanConfig 已提供则跳过）
     */
    @Bean
    @ConditionalOnMissingBean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 权限提供者默认实现 — 共享单库下用 JdbcTemplate 直查权限表
     * 平台服务可用自己的实现覆盖（复用 AuthServiceImpl 逻辑）
     */
    @Bean
    @ConditionalOnMissingBean(PermissionProvider.class)
    @ConditionalOnClass(JdbcTemplate.class)
    public PermissionProvider jdbcPermissionProvider(JdbcTemplate jdbcTemplate) {
        return new JdbcPermissionProvider(jdbcTemplate);
    }

    /**
     * 审计切面 — 拦截带 @AuditLog 注解的方法，经 AuditLogSink 落库
     * 无 sink 实现时仅告警不阻断业务
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(ObjectMapper.class)
    public AuditLogAspect auditLogAspect(ObjectProvider<AuditLogSink> sinkProvider,
                                         ObjectMapper objectMapper) {
        return new AuditLogAspect(sinkProvider, objectMapper);
    }

    /**
     * 审计落库 MQ 实现 — RabbitTemplate 发 gsc-audit-log 事件（业务域唯一落库通道）
     * 仅当类路径存在 RabbitTemplate（服务引入了 starter-amqp 依赖）时注册；
     * 平台服务用自己的 DbAuditLogSink 覆盖（唯一直写落库方）。
     */
    @Bean
    @ConditionalOnMissingBean(AuditLogSink.class)
    @ConditionalOnClass(RabbitTemplate.class)
    public AuditLogSink mqAuditLogSink(RabbitTemplate rabbitTemplate) {
        return new MqAuditLogSink(rabbitTemplate);
    }

    /**
     * 租户过滤器 — 内部密钥校验通过才信任租户头，否则从 JWT 解析（servlet 栈才注册）
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(OncePerRequestFilter.class)
    public TenantFilter tenantFilter(JwtTokenProvider jwtTokenProvider,
                                     @Value("${gsc.internal.secret:}") String internalSecret) {
        return new TenantFilter(jwtTokenProvider, internalSecret);
    }

    /**
     * 内部接口鉴权过滤器 — /internal/** 必须携带正确 X-Internal-Secret，否则 403
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(OncePerRequestFilter.class)
    public InternalAuthFilter internalAuthFilter(ObjectMapper objectMapper,
                                                 @Value("${gsc.internal.secret:}") String internalSecret) {
        return new InternalAuthFilter(objectMapper, internalSecret);
    }

    /**
     * Feign 租户透传拦截器 — 服务间调用透传 X-Tenant-Id / Authorization
     * 仅当类路径存在 openfeign 时注册（业务服务引入 openfeign 依赖才激活）
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(feign.RequestInterceptor.class)
    public TenantFeignInterceptor tenantFeignInterceptor(
            @Value("${gsc.internal.secret:}") String internalSecret) {
        return new TenantFeignInterceptor(internalSecret);
    }

    /**
     * AI 护照身份校验客户端 — 配置了 gsc.passport.client-secret 才注册
     */
    @Bean
    @RefreshScope
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "gsc.passport.client-secret")
    public PassportClient passportClient(ObjectMapper objectMapper,
                                         RestClient.Builder restClientBuilder) {
        return new PassportClient(objectMapper, restClientBuilder);
    }

    /**
     * 阿里云邮件服务 — 配置了 spring.mail.aliyun.access-key-id 才注册
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "spring.mail.aliyun.access-key-id")
    public MailService mailService() {
        return new MailService();
    }

    /**
     * 阿里云 OSS 上传服务 — 配置了 gsc.oss.endpoint 才注册
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "gsc.oss.endpoint")
    public OssService ossService() {
        return new OssService();
    }

    /**
     * 天气数据客户端 — Open-Meteo（全球覆盖、免 key），所有服务可注入使用
     * <p>
     * 无密钥，默认即可用；换商业数据源（含官方灾害预警）时新增 WeatherClient 实现并覆盖本 Bean，
     * 业务侧（交付风险/预警/AI 工具）零改动。
     */
    @Bean
    @ConditionalOnMissingBean
    public WeatherClient weatherClient(ObjectMapper objectMapper, RestClient.Builder restClientBuilder,
                                       RedisTemplate<String, Object> redisTemplate) {
        return new OpenMeteoWeatherClient(objectMapper, restClientBuilder, redisTemplate);
    }
}
