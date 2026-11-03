package cn.greensmartplanet.gsc.platform.support;

import cn.greensmartplanet.gsc.platform.entity.SanctionEntry;
import cn.greensmartplanet.gsc.platform.mapper.SanctionEntryMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 制裁名单内存索引 — 规范化名 → 名单条目，筛查 O(1) 匹配
 * <p>
 * 7.2 万条 target（name + 别名约 15 万个 key）量级小，可全量常驻内存（约几十 MB）。
 * 筛查时不用再全表查 DB + 逐条正则，直接 HashMap 精确命中，毫秒级。
 * <p>
 * 构建时机：
 * <ol>
 *   <li>服务启动（{@link #init()}）——失败仅告警不阻断启动，等同步后重建</li>
 *   <li>每次同步完成（{@link #rebuild()}）——名单更新后刷新索引</li>
 * </ol>
 * <p>
 * 注意：索引是每实例一份的 JVM 内存 Map。同步由 Redis 锁保证只有一个实例执行，
 * 其余实例靠 {@code gsc-sanction-index-refresh} 的 fanout 广播重建（见 {@link #onRefreshBroadcast}），
 * 否则会停留在旧名单，同一供应商在不同实例的筛查结果不一致（合规风险）。
 *
 * @author gsc
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SanctionNameIndex {

    private final SanctionEntryMapper sanctionEntryMapper;

    /** 规范化名 → 名单条目（不可变快照，重建时整体替换） */
    private volatile Map<String, SanctionEntry> index = Map.of();

    @PostConstruct
    public void init() {
        try {
            rebuild();
        } catch (Exception e) {
            log.warn("制裁名单索引启动构建失败（可能表未建或数据未同步）: {}", e.getMessage());
        }
    }

    /**
     * 收到索引刷新广播 → 重建本地索引。
     *
     * <p>队列是 {@code RabbitMqConfig} 声明的 {@code AnonymousQueue}（每实例一个随机名、非持久），
     * fanout 交换机会把刷新消息复制到每个实例各自的队列，保证所有实例都重建。
     *
     * <p>失败只记日志不抛：重建是幂等的旁路刷新，本轮失败下一轮同步/重启会再建；
     * 抛异常会让匿名队列走重试并投递到无 DLX 的死信，徒增噪音。
     */
    @RabbitListener(queues = "#{sanctionIndexRefreshQueue.name}")
    public void onRefreshBroadcast(String ignored) {
        log.info("[制裁索引] 收到刷新广播，重建本地索引");
        try {
            rebuild();
        } catch (Exception e) {
            log.error("[制裁索引] 广播触发的重建失败（本轮沿用旧索引，下次同步/重启会再建）", e);
        }
    }

    /**
     * 重建索引：全表加载 → 规范化 name + aliases → 装 HashMap。
     * 一次性耗时约数秒（7.2 万条 × 正则），仅在启动/同步后触发，不在筛查路径。
     */
    public void rebuild() {
        Map<String, SanctionEntry> map = new HashMap<>();
        for (SanctionEntry e : sanctionEntryMapper.selectList(null)) {
            String normName = normalize(e.getName());
            if (normName != null) {
                map.putIfAbsent(normName, e);
            }
            for (String alias : splitAliases(e.getAliases())) {
                String na = normalize(alias);
                if (na != null) {
                    map.putIfAbsent(na, e);
                }
            }
        }
        this.index = map;
        log.info("制裁名单索引已重建: {} 个匹配键", map.size());
    }

    /**
     * 规范化后精确查（O(1)）。未命中返回 null。
     */
    public SanctionEntry lookup(String rawName) {
        if (rawName == null) {
            return null;
        }
        String norm = normalize(rawName);
        return norm == null ? null : index.get(norm);
    }

    /**
     * 索引是否为空（等价于名单库为空）。
     */
    public boolean isEmpty() {
        return index.isEmpty();
    }

    /**
     * 名称规范化：小写 + 去空白 + 去常见标点（公司后缀差异如 Co./Ltd. 归一）。
     */
    private String normalize(String s) {
        if (s == null) {
            return null;
        }
        String v = s.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "")
                .replaceAll("[.,，。;；:：'\"()（）\\[\\]【】&]", "");
        return v.isBlank() ? null : v;
    }

    /**
     * 别名按分号拆分（aliases 存的是分号分隔的多语种变体）。
     */
    private List<String> splitAliases(String aliases) {
        if (aliases == null || aliases.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String a : aliases.split(";")) {
            if (!a.isBlank()) {
                result.add(a);
            }
        }
        return result;
    }
}
