package cn.greensmartplanet.gsc.framework.cache;

import cn.greensmartplanet.gsc.framework.security.PermissionCache;
import cn.greensmartplanet.gsc.framework.security.RefreshTokenStore;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis 配置 — 配置 Redis 的序列化方式
 * <p>
 * 默认的 RedisTemplate 存数据用的是 JDK 序列化（二进制，人看不懂），
 * 这里改成 JSON 序列化（存到 Redis 里是可读的 JSON 字符串），方便排查问题。
 * <p>
 * 主要用途：
 * - Token 黑名单（用户登出后把 Token 放进 Redis，防止被重复使用）
 * - 权限缓存（减少每次请求都查数据库）
 * - 登录验证码（5分钟过期）
 *
 * @author gsc
 */
@Configuration
public class RedisConfig {


    @Bean
    @ConditionalOnMissingBean(name = "redisTemplate")
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory, ObjectMapper objectMapper) {
        // 复制一个 ObjectMapper 专门给 Redis 用，不影响 Web 层的 JSON 序列化
        ObjectMapper redisMapper = objectMapper.copy();
        redisMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        redisMapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL);

        // 用 Jackson 序列化器（把 Java 对象转成 JSON 存 Redis）
        Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(redisMapper, Object.class);

        // 构造 RedisTemplate
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);                  // Redis 连接工厂
        template.setKeySerializer(new StringRedisSerializer());  // Key 用字符串（可读）
        template.setValueSerializer(serializer);                 // Value 用 JSON（可读）
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();                           // 初始化完成
        return template;
    }

    /**
     * 权限缓存组件 — 需要两个模板：JSON 模板存权限快照，String 模板存版本号计数器
     * <p>
     * 版本号必须走 String 序列化：Jackson 模板写入的值带类型头，而 INCR 是原生命令，
     * 会因无法解析而报错（详见 PermissionCache.bumpVersion 注释）。
     */
    @Bean
    @ConditionalOnMissingBean
    public PermissionCache permissionCache(RedisTemplate<String, Object> redisTemplate,
                                           StringRedisTemplate stringRedisTemplate) {
        return new PermissionCache(redisTemplate, stringRedisTemplate);
    }

    /**
     * 刷新令牌存储 — 原子轮换 refreshToken，防并发刷新互相覆盖
     * <p>
     * 只用 StringRedisTemplate：refreshToken 是纯字符串，且 Lua 脚本在 Redis 端
     * 只比对原始字节，Jackson 序列化的引号与类型头会让比对失效。
     */
    @Bean
    @ConditionalOnMissingBean
    public RefreshTokenStore refreshTokenStore(StringRedisTemplate stringRedisTemplate) {
        return new RefreshTokenStore(stringRedisTemplate);
    }

    /**
     * Lettuce 客户端加固 —— 应对「机器睡眠 / 空闲超时」导致的连接假死。
     * <p>
     * 现象：笔记本睡眠或长时间空闲后，池里的 TCP 已被服务端/NAT 关闭，客户端仍持有它；
     * 下一个请求拿到死连接发命令，等到 Connection reset 才失败，表现为一次 500
     * （日志为 RedisSystemException → SocketException: Connection reset）。
     * <p>
     * 三项设置对应三个失败点：
     * <ul>
     *   <li>keepAlive：内核定期发探测包，空闲连接不会静默死掉</li>
     *   <li>connectTimeout：重连不必等 Lettuce 默认的 10s</li>
     *   <li>REJECT_COMMANDS：断线期间的命令立即失败，而不是排队等重连——
     *       默认行为会把请求线程挂住，配合缺失的命令超时就是几十秒卡顿</li>
     * </ul>
     * 命令超时本身走 Nacos 的 {@code spring.data.redis.timeout}，不在此处设置。
     */
    @Bean
    @ConditionalOnMissingBean
    public LettuceClientConfigurationBuilderCustomizer lettuceClientCustomizer() {
        return builder -> builder.clientOptions(ClientOptions.builder()
                .socketOptions(SocketOptions.builder()
                        .keepAlive(true)
                        .connectTimeout(Duration.ofSeconds(3))
                        .build())
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .build());
    }
}
