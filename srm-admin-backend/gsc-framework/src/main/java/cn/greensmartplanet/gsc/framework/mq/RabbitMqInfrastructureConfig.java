package cn.greensmartplanet.gsc.framework.mq;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 基础设施配置 — 全局唯一，各服务共享
 * <p>
 * 职责：只管"怎么发消息"（序列化器 + RabbitTemplate + 发送确认回调），
 * 不管"发什么消息"（交换机/队列/绑定由各业务域自己的 RabbitMqConfig 声明）。
 * <p>
 * 条件装配：仅当类路径存在 RabbitTemplate（服务引入了 spring-boot-starter-amqp）时激活。
 * 网关等不需要 MQ 的服务不会加载此类。
 *
 * @author gsc
 */
@Configuration
@ConditionalOnClass(RabbitTemplate.class)
public class RabbitMqInfrastructureConfig {

    /**
     * JSON 转换器 — payload 自动序列化/反序列化为契约类
     * <p>
     * 替换 RabbitTemplate 默认的 SimpleMessageConverter（Java 序列化），
     * 保证生产端和消费端 JSON 契约一致。
     */
    @Bean
    @ConditionalOnMissingBean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitTemplate — 注入 JSON 转换器 + 开启发送确认回调
     * <p>
     * publisher confirm（yml 配置 publisher-confirm-type: correlated）：
     * ack=false 表示交换机不存在/落盘失败，仅告警不抛——旁路事件不阻断业务。
     */
    @Bean
    @ConditionalOnMissingBean(RabbitTemplate.class)
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter jacksonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jacksonMessageConverter);
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                org.slf4j.LoggerFactory.getLogger(RabbitMqInfrastructureConfig.class)
                        .warn("RabbitMQ 发送未确认: cause={}", cause);
            }
        });
        // mandatory=true：消息到达交换机但没有队列接收时，broker 会退回消息（触发下方 ReturnsCallback），而不是静默丢弃。
        template.setMandatory(true);
        template.setReturnsCallback(returned -> org.slf4j.LoggerFactory.getLogger(RabbitMqInfrastructureConfig.class)
                .warn("RabbitMQ 消息未路由到队列: exchange={}, routingKey={}, replyCode={}, replyText={}",
                        returned.getExchange(), returned.getRoutingKey(), returned.getReplyCode(), returned.getReplyText()));
        return template;
    }
}
