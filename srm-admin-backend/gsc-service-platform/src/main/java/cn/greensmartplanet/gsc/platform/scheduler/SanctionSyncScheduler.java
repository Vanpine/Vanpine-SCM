package cn.greensmartplanet.gsc.platform.scheduler;

import cn.greensmartplanet.gsc.platform.service.SanctionSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 制裁名单同步调度器 — 每日凌晨拉取 OpenSanctions 最新名单入库
 * <p>
 * 与交付风险扫描（@Scheduled）同款原生调度，框架层 @EnableScheduling 已全局开启。
 * 手动同步另有接口，供超管在名单库页即时触发。
 *
 * @author gsc
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SanctionSyncScheduler {

    private final SanctionSyncService sanctionSyncService;

    /**
     * 每日凌晨 3 点同步（避开业务高峰；OpenSanctions 每日发布，非实时流）
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void syncDaily() {
        log.info("[制裁同步] 定时任务触发");
        try {
            sanctionSyncService.triggerSync();
        } catch (Exception e) {
            log.error("[制裁同步] 定时任务异常", e);
        }
    }
}
