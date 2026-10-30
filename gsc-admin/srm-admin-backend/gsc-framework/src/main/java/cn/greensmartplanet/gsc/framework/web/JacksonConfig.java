package cn.greensmartplanet.gsc.framework.web;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;

/**
 * Jackson 全局 Long→String 序列化配置 — 雪花主键的精度兜底
 * <p>
 * 雪花 ID 是 64 位 Long（最大约 1.8×10^18），超过 JavaScript Number 安全整数上限
 * （2^53-1 ≈ 9×10^15）。若以 JSON number 返回前端，id 会被静默舍入导致数据错乱
 * （如 1802345678901234567 变成 1802345678901234500），且不报任何错。
 * <p>
 * 因此所有 Long 字段（含 Long.TYPE 基本类型）统一序列化为字符串，前端拿到的
 * id/total/timestamp 都是 string；这是阿里规约建议的做法，微信/支付宝 API 同款。
 * 该定制器随 gsc-framework 自动装配，各业务服务零配置生效；
 * 网关是 WebFlux 纯转发不序列化业务对象，无需处理。
 *
 * @author gsc
 */
@AutoConfiguration
public class JacksonConfig {

    /**
     * 注册 Long→String 序列化定制器
     * <p>
     * Jackson2ObjectMapperBuilderCustomizer 是 Spring Boot 官方扩展点，
     * 与默认定制器（JavaTimeModule 等）叠加生效，业务服务的自定义 ObjectMapper 也一并覆盖。
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> {
            builder.serializerByType(Long.class, ToStringSerializer.instance);
            builder.serializerByType(Long.TYPE, ToStringSerializer.instance);
        };
    }
}
