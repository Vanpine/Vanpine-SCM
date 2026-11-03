package cn.greensmartplanet.gsc.platform.config;

import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 业务拓扑 — platform 域消费侧
 * <p>
 * 基础设施（Converter + RabbitTemplate）由 gsc-framework 的 RabbitMqInfrastructureConfig 统一提供，
 * 本类只声明 platform 域消费的交换机/队列/绑定。
 * <p>
 * 拓扑：
 * <ul>
 *   <li>交换机 gsc-notification（topic）→ 队列 gsc-notification.platform（绑定键 notify）
 *       ——通知事件唯一落库队列，消费失败重试耗尽进 .dlq 死信</li>
 *   <li>交换机 gsc-audit-log（topic）→ 队列 gsc-audit-log.platform
 *       ——审计事件唯一落库队列，同上死信策略</li>
 * </ul>
 * 幂等：消费端将消息 messageId 落库到 event_id 唯一索引（见 Consumer）。
 *
 * @author gsc
 */
@Configuration
public class RabbitMqConfig {

    /** 通知交换机（业务域以路由键 notify 发送） */
    public static final String NOTIFICATION_EXCHANGE = "gsc-notification";
    /** 审计交换机（业务域以路由键 audit 发送） */
    public static final String AUDIT_LOG_EXCHANGE = "gsc-audit-log";

    /**
     * 制裁名单索引刷新交换机（fanout 广播，本域自产自销）
     * <p>
     * 制裁名单索引是每实例一份的 JVM 内存 Map（{@code SanctionNameIndex}）。
     * 同步完成后必须广播，让**每个实例**都重建自己的索引——否则只有跑同步的那个实例
     * 拿到新名单，其余实例仍用旧数据，同一供应商在不同实例的筛查结果会不一致。
     * <p>
     * ⚠️ 必须 fanout：topic + 固定队列只有一个实例消费，等于没修。
     */
    public static final String SANCTION_INDEX_REFRESH_EXCHANGE = "gsc-sanction-index-refresh";

    /** 通知死信交换机（消费失败重试耗尽后投递） */
    public static final String DEAD_LETTER_EXCHANGE = "gsc-dlx";

    // ==================== 交换机（消息先到交换机，按路由键分发给绑定的队列） ====================

    /**
     * 通知交换机（topic 类型）
     * <p>
     * 参数含义：name=交换机名，durable=true（broker 重启后交换机仍存在），
     * autoDelete=false（无队列绑定时不自动删除，防止业务域未启动时拓扑被清理）。
     * 业务域（trade/delivery/supplier）的 NotificationPublisher 以路由键 notify 发到这里。
     */
    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(NOTIFICATION_EXCHANGE, true, false);
    }

    /**
     * 审计交换机（topic 类型）
     * <p>
     * durable=true / autoDelete=false 同通知交换机；
     * 业务域的 MqAuditLogSink（framework 自动装配）以路由键 audit 发到这里。
     */
    @Bean
    public TopicExchange auditLogExchange() {
        return new TopicExchange(AUDIT_LOG_EXCHANGE, true, false);
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
     * 通知消费队列（platform 专属，业务域只发不消费）
     * <p>
     * durable=true：队列持久化，broker 重启队列与消息都在。
     * x-dead-letter-exchange=gsc-dlx：消息消费失败（重试耗尽）后自动转投死信交换机；
     * x-dead-letter-routing-key=gsc-notification.platform.dlq：转投时携带的路由键，
     * 死信交换机按此键路由到对应 .dlq 队列。
     */
    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable("gsc-notification.platform")
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", "gsc-notification.platform.dlq")
                .build();
    }

    /**
     * 审计消费队列（platform 专属，业务域只发不消费）
     * <p>
     * 死信参数同通知队列：消费失败重试耗尽后转投 gsc-dlx，路由到 gsc-audit-log.platform.dlq。
     */
    @Bean
    public Queue auditLogQueue() {
        return QueueBuilder.durable("gsc-audit-log.platform")
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", "gsc-audit-log.platform.dlq")
                .build();
    }

    /**
     * 通知死信队列（.dlq 后缀）
     * <p>
     * 存放通知消息中"重试 3 次仍失败"的消息（落库报错、反序列化失败等），
     * 普通消费者不监听，靠运维在管理台人工排查/重放。无死信参数——它本身就是终点。
     */
    @Bean
    public Queue notificationDlq() {
        return QueueBuilder.durable("gsc-notification.platform.dlq").build();
    }

    /**
     * 审计死信队列（.dlq 后缀）
     * <p>
     * 同通知 DLQ：审计消息重试耗尽后的终点，人工排查入口。
     */
    @Bean
    public Queue auditLogDlq() {
        return QueueBuilder.durable("gsc-audit-log.platform.dlq").build();
    }

    // ==================== 绑定（建立"交换机 → 队列"的路由关系） ====================

    /**
     * 通知交换机 → 通知队列（路由键 notify）
     * <p>
     * 语义：路由键为 notify 的消息才投递到本队列；业务域发送用同一路由键
     * （见 NotificationPublisher.ROUTING_KEY），两侧必须一致，否则消息无人消费。
     */
    @Bean
    public Binding notificationBinding(Queue notificationQueue, TopicExchange notificationExchange) {
        return BindingBuilder.bind(notificationQueue).to(notificationExchange).with("notify");
    }

    /**
     * 审计交换机 → 审计队列（路由键 audit）
     * <p>
     * 与 MqAuditLogSink.ROUTING_KEY 保持一致（audit）。
     */
    @Bean
    public Binding auditLogBinding(Queue auditLogQueue, TopicExchange auditLogExchange) {
        return BindingBuilder.bind(auditLogQueue).to(auditLogExchange).with("audit");
    }

    /**
     * 死信交换机 → 通知死信队列（路由键 gsc-notification.platform.dlq）
     * <p>
     * 路由键与队列声明的 x-dead-letter-routing-key 一致，死信消息按此键路由回本条 DLQ。
     * noargs()：绑定不带额外参数（死信投递无需 TTL 等特性）。
     */
    @Bean
    public Binding notificationDlqBinding(Queue notificationDlq, Exchange deadLetterExchange) {
        return BindingBuilder.bind(notificationDlq).to(deadLetterExchange).with("gsc-notification.platform.dlq").noargs();
    }

    /**
     * 死信交换机 → 审计死信队列（路由键 gsc-audit-log.platform.dlq）
     * <p>
     * 同通知 DLQ 绑定：路由键与 x-dead-letter-routing-key 一致。
     */
    @Bean
    public Binding auditLogDlqBinding(Queue auditLogDlq, Exchange deadLetterExchange) {
        return BindingBuilder.bind(auditLogDlq).to(deadLetterExchange).with("gsc-audit-log.platform.dlq").noargs();
    }

    // ==================== 制裁名单索引刷新（fanout 广播，本域自产自销） ====================

    /**
     * 索引刷新交换机（fanout 类型）
     * <p>
     * fanout 忽略路由键，把消息投给所有绑定的队列——正是「广播给每个实例」需要的语义。
     * durable=true：broker 重启后交换机仍在。
     */
    @Bean
    public FanoutExchange sanctionIndexRefreshExchange() {
        return new FanoutExchange(SANCTION_INDEX_REFRESH_EXCHANGE, true, false);
    }

    /**
     * 索引刷新队列 — {@link AnonymousQueue}：每个实例启动时生成一个随机名、非持久、
     * 连接断开自动删除的独占队列。fanout 交换机把刷新消息复制到每个实例各自的队列，
     * 保证所有实例都收到并重建本地索引。
     * <p>
     * 用匿名队列而非固定队列名，是因为固定名会让 N 个实例抢同一个队列的消息
     * （只有 1 个实例能消费到），达不到广播效果。
     */
    @Bean
    public Queue sanctionIndexRefreshQueue() {
        return new AnonymousQueue();
    }

    /** fanout 交换机 → 本实例的匿名队列（fanout 无需路由键） */
    @Bean
    public Binding sanctionIndexRefreshBinding(Queue sanctionIndexRefreshQueue,
                                               FanoutExchange sanctionIndexRefreshExchange) {
        return BindingBuilder.bind(sanctionIndexRefreshQueue).to(sanctionIndexRefreshExchange);
    }
}
