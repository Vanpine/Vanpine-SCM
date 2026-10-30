package cn.greensmartplanet.gsc.delivery.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 业务拓扑 — delivery 域（消费合同确认建单 + 发送站内消息/审计）
 * <p>
 * 基础设施（Converter + RabbitTemplate）由 gsc-framework 的 RabbitMqInfrastructureConfig 统一提供，
 * 本类只声明 delivery 域消费的交换机/队列/绑定。
 * <p>
 * 本域声明的拓扑：
 * <ul>
 *   <li>交换机 gsc-contract-confirmed（topic）→ 队列 gsc-contract-confirmed.delivery
 *       ——合同确认事件消费队列（trade 域发布），消费失败重试耗尽进 .dlq 死信</li>
 * </ul>
 * 站内消息/审计交换机由 platform 域声明（本域只发送不声明拓扑）。
 * 幂等：合同建单由 contract_id 查重（createFromContract 内部）；通知/审计由 messageId 唯一索引。
 *
 * @author gsc
 */
@Configuration
public class RabbitMqConfig {

    /** 合同确认交换机（trade 域以路由键 contract 发送） */
    public static final String CONTRACT_CONFIRMED_EXCHANGE = "gsc-contract-confirmed";
    /** 死信交换机（消费失败重试耗尽后投递，各域共用名） */
    public static final String DEAD_LETTER_EXCHANGE = "gsc-dlx";

    // ==================== 交换机（消息先到交换机，按路由键分发给绑定的队列） ====================

    /**
     * 合同确认交换机（topic 类型）
     * <p>
     * 参数含义：name=交换机名，durable=true（broker 重启后交换机仍存在），
     * autoDelete=false（无队列绑定时不自动删除，防止 trade 域未启动时拓扑被清理）。
     * trade 域的 ContractConfirmedPublisher 以路由键 contract 发到这里。
     */
    @Bean
    public TopicExchange contractConfirmedExchange() {
        return new TopicExchange(CONTRACT_CONFIRMED_EXCHANGE, true, false);
    }

    /**
     * 死信交换机（topic 类型，全局同名 gsc-dlx）
     * <p>
     * 作用：消费队列通过 x-dead-letter-exchange 指向它——消息消费失败重试耗尽后，
     * 由 broker 自动转投到这里（失败消息不凭空消失，先进死信待排查）。
     * 各域都声明同名 gsc-dlx，broker 端合并为同一个交换机。
     */
    @Bean
    public Exchange deadLetterExchange() {
        return new TopicExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    // ==================== 队列（消息暂存管道，消费失败自动转死信） ====================

    /**
     * 合同确认消费队列（delivery 专属，trade 只发不消费）
     * <p>
     * durable=true：队列持久化，broker 重启队列与消息都在。
     * x-dead-letter-exchange=gsc-dlx：消息消费失败（重试耗尽）后自动转投死信交换机；
     * x-dead-letter-routing-key=gsc-contract-confirmed.delivery.dlq：转投时携带的路由键，
     * 死信交换机按此键路由到对应 .dlq 队列。
     * 消费入口：ContractConfirmedConsumer（@RabbitListener 监听本队列）。
     */
    @Bean
    public Queue contractConfirmedQueue() {
        return QueueBuilder.durable("gsc-contract-confirmed.delivery")
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", "gsc-contract-confirmed.delivery.dlq")
                .build();
    }

    /**
     * 合同确认死信队列（.dlq 后缀）
     * <p>
     * 存放合同确认消息中"重试 3 次仍失败"的消息（Feign 重查合同失败、建单报错等），
     * 普通消费者不监听，靠运维在管理台人工排查/重放。无死信参数——它本身就是终点。
     */
    @Bean
    public Queue contractConfirmedDlq() {
        return QueueBuilder.durable("gsc-contract-confirmed.delivery.dlq").build();
    }

    // ==================== 绑定（建立"交换机 → 队列"的路由关系） ====================

    /**
     * 合同确认交换机 → 消费队列（路由键 contract）
     * <p>
     * 语义：路由键为 contract 的消息才投递到本队列；trade 域发送用同一路由键
     * （见 ContractConfirmedPublisher.ROUTING_KEY），两侧必须一致，否则消息无人消费。
     */
    @Bean
    public Binding contractConfirmedBinding(Queue contractConfirmedQueue, TopicExchange contractConfirmedExchange) {
        return BindingBuilder.bind(contractConfirmedQueue).to(contractConfirmedExchange).with("contract");
    }

    /**
     * 死信交换机 → 合同确认死信队列（路由键 gsc-contract-confirmed.delivery.dlq）
     * <p>
     * 路由键与队列声明的 x-dead-letter-routing-key 一致，死信消息按此键路由回本条 DLQ。
     * noargs()：绑定不带额外参数（死信投递无需 TTL 等特性）。
     */
    @Bean
    public Binding contractConfirmedDlqBinding(Queue contractConfirmedDlq, Exchange deadLetterExchange) {
        return BindingBuilder.bind(contractConfirmedDlq).to(deadLetterExchange)
                .with("gsc-contract-confirmed.delivery.dlq").noargs();
    }

    public static final String WEATHER_ADVICE_EXCHANGE = "gsc-weather-advice";

    @Bean
    public TopicExchange weatherAdviceExchange() {
        return new TopicExchange(WEATHER_ADVICE_EXCHANGE, true, false);
    }

    @Bean
    public Queue weatherAdviceQueue() {
        return QueueBuilder.durable("gsc-weather-advice.delivery")
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", "gsc-weather-advice.delivery.dlq")
                .build();
    }

    @Bean
    public Queue weatherAdviceDlq() {
        return QueueBuilder.durable("gsc-weather-advice.delivery.dlq").build();
    }

    @Bean
    public Binding weatherAdviceBinding(Queue weatherAdviceQueue, TopicExchange weatherAdviceExchange) {
        return BindingBuilder.bind(weatherAdviceQueue).to(weatherAdviceExchange).with("weather.advice");
    }

    @Bean
    public Binding weatherAdviceDlqBinding(Queue weatherAdviceDlq, Exchange deadLetterExchange) {
        return BindingBuilder.bind(weatherAdviceDlq).to(deadLetterExchange)
                .with("gsc-weather-advice.delivery.dlq").noargs();
    }
}
