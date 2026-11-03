package cn.greensmartplanet.gsc.delivery.scheduler;

import cn.greensmartplanet.gsc.common.constant.SystemConstants;
import cn.greensmartplanet.gsc.delivery.service.DeliveryRiskLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 交付风险扫描调度器 — 每 5 分钟扫描未闭环交付单，按合同规则识别/升级风险
 * <p>
 * ⚠️ 微服务拆分：XXL-JOB 管理台 3.x 协议联调暂缓（用户指示），
 * 定时任务先回退 Spring 原生 @Scheduled（每 5 分钟，与原单体一致）。
 * <p>
 * ⚠️ 多实例：本任务在每个实例各触发一次。风险按「一单一风险」建模，scanRisks 以
 * delivery_id 定位唯一风险行（selectByDeliveryId 判空后 insert），并发扫描同一交付单
 * 存在先查后插竞态，需靠分布式锁 + delivery_risk 唯一索引兜底（见 CLAUDE.md §7.2）。
 *
 * @author gsc
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryRiskScheduler {

    /** 扫描锁 key：多实例只有一个能跑本轮扫描 */
    private static final String LOCK_KEY = "delivery:risk:scan:lock";

    private final DeliveryRiskLifecycleService lifecycleService;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 每 5 分钟扫描一次（与原单体一致，稳定后可调回小时级）
     */
    @Scheduled(cron = "0 */5 * * * *")
    public void scanDeliveryRisk() {
        // 多实例互斥：抢不到锁说明别的实例正在扫描，跳过本轮
        if (!tryLock()) {
            return;
        }
        try {
            lifecycleService.scanRisks();
        } catch (Exception e) {
            log.error("交付风险扫描异常", e);
        } finally {
            releaseLock();
        }
    }

    /**
     * 抢扫描锁（Redis setIfAbsent + TTL），多实例互斥。
     * <p>
     * Redis 不可用时保守跳过：本轮不扫不影响正确性（下一轮 5 分钟后会补），
     * 而重复扫描会让 N 个实例同时打交易域 Feign + 天气 API。
     */
    private boolean tryLock() {
        try {
            Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(
                    LOCK_KEY, "1", Duration.ofSeconds(SystemConstants.DELIVERY_RISK_SCAN_LOCK_TTL));
            if (Boolean.TRUE.equals(locked)) {
                return true;
            }
            log.info("交付风险扫描：已有实例在扫描中，本轮跳过");
            return false;
        } catch (Exception e) {
            log.warn("交付风险扫描：抢锁失败，本轮跳过: {}", e.getMessage());
            return false;
        }
    }

    /** 释放扫描锁；失败只记日志，TTL 到点会自动过期 */
    private void releaseLock() {
        try {
            stringRedisTemplate.delete(LOCK_KEY);
        } catch (Exception e) {
            log.warn("交付风险扫描：释放锁失败（TTL 到期自动过期）: {}", e.getMessage());
        }
    }
}
