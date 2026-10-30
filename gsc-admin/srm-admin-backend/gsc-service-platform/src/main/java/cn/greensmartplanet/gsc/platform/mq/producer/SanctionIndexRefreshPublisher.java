package cn.greensmartplanet.gsc.platform.mq.producer;

import cn.greensmartplanet.gsc.platform.config.RabbitMqConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 制裁名单索引刷新发布器 — fanout 广播，各实例收到后重建本地内存索引。
 *
 * <p>背景：{@code SanctionNameIndex} 是每实例一份的 JVM 内存 Map。同步由 Redis 锁保证
 * 只有一个实例执行，若不广播，其余实例的索引永远停留在旧数据，同一供应商在不同实例
 * 筛查结果不一致（合规风险）。
 *
 * <p>发送失败仅记日志：本实例在 {@code doSync()} 里已自行 rebuild，
 * 其它实例等下次同步或重启补齐，不阻断同步主流程。
 *
 * @author gsc
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SanctionIndexRefreshPublisher {

    /** 刷新交换机（platform 域 RabbitMqConfig 声明，fanout 类型） */
    public static final String EXCHANGE = RabbitMqConfig.SANCTION_INDEX_REFRESH_EXCHANGE;

    private final RabbitTemplate rabbitTemplate;

    /** 广播索引刷新（fanout 忽略路由键，传空串） */
    public void publish() {
        try {
            rabbitTemplate.convertAndSend(EXCHANGE, "", "rebuild");
            log.info("[制裁索引] 已广播刷新");
        } catch (Exception e) {
            log.error("[制裁索引] 广播刷新失败（本实例已自行重建，其它实例待下次同步补齐）", e);
        }
    }
}
