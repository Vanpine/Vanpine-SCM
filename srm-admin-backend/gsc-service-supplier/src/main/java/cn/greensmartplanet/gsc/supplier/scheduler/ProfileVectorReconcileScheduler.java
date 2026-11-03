package cn.greensmartplanet.gsc.supplier.scheduler;

import cn.greensmartplanet.gsc.common.constant.SystemConstants;
import cn.greensmartplanet.gsc.supplier.mapper.EnterpriseProfileMapper;
import cn.greensmartplanet.gsc.supplier.service.impl.ProfileSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * 供应商画像向量对账调度器 — 定期比对 MySQL 画像数与 Milvus 向量数，缺了就全量回填。
 *
 * <p>存在意义：画像 → 向量的同步走 {@link ProfileSyncService#syncProfile} 的旁路，
 * 失败只记 warn、不阻断主流程。没有对账时，同步失败会「静默丢数据」——
 * 表现为 MySQL 有画像、Milvus 为空，AI 语义匹配静默召回不到。
 * 本调度器是那条旁路的兜底补偿。
 *
 * <p>为什么不做成启动时全量同步：多实例部署时每个实例启动都会全量重推；
 * 且服务运行期间丢的数据要等到下次重启才修。定时对账不依赖重启，运行中即可自愈。
 *
 * <p>多实例：用 Redis 锁（{@code setIfAbsent} + TTL）保证同一轮只有一个实例执行。
 * 长期可迁到 XXL-JOB（调度中心天然单机执行），当前 XXL-JOB 暂缓故先用锁兜住。
 *
 * @author gsc
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileVectorReconcileScheduler {

    /** AI 服务健康检查端点：返回 {"status":"ok","supplier_count":N} */
    private static final String HEALTH_URI = "/api/health";
    /** 对账锁 key：多实例只有一个能跑本轮对账 */
    private static final String LOCK_KEY = "profile:reconcile:lock";

    private final EnterpriseProfileMapper enterpriseProfileMapper;
    private final ProfileSyncService profileSyncService;
    private final RestClient restClient;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 每 6 小时对账一次（错开整点，避开业务高峰）
     * <p>
     * 只比对数量：能覆盖「整表被删」「部分画像没同步进去」这类主要场景；
     * 「数量相同但内容不一致」检测不到，需要时再升级为 tenant_id 集合比对。
     */
    @Scheduled(cron = "0 30 */6 * * *")
    public void reconcileSupplierVectors() {
        // 多实例互斥：抢不到锁说明别的实例正在对账，本轮直接跳过（下一轮再试）
        if (!tryLock()) {
            return;
        }
        try {
            doReconcile();
        } finally {
            // 释放锁，让下一轮（或故障转移后的其它实例）能跑
            releaseLock();
        }
    }

    /** 对账主流程：两侧取数 → 比对 → 缺了就全量回填 */
    private void doReconcile() {
        try {
            // 1. 两侧取数：MySQL 画像数 vs Milvus 向量数
            long dbCount = enterpriseProfileMapper.countAllSuppliers();
            // 超出全量同步上限时永远补不齐，跳过并告警（避免每轮都白跑一遍）
            if (dbCount > ProfileSyncService.FULL_SYNC_LIMIT) {
                log.warn("[画像对账] MySQL 画像数 {} 超过全量同步上限 {}，无法自动对账，需人工处理",
                        dbCount, ProfileSyncService.FULL_SYNC_LIMIT);
                return;
            }
            Integer vectorCount = fetchVectorCount();
            if (vectorCount == null) {
                log.warn("[画像对账] 取 Milvus 向量数失败，本次跳过");
                return;
            }

            // 2. 比对：Milvus 少于 MySQL 说明有画像没同步进去，触发全量回填（upsert 幂等）
            if (dbCount > vectorCount) {
                log.warn("[画像对账] 不一致: MySQL={} Milvus={}，缺失 {} 条，触发全量回填",
                        dbCount, vectorCount, dbCount - vectorCount);
                profileSyncService.syncAll();
            } else {
                log.info("[画像对账] 一致: MySQL={} Milvus={}", dbCount, vectorCount);
            }
        } catch (Exception e) {
            // 对账是旁路任务：异常必须自己兜住，不能外抛影响同线程池的其它调度
            log.error("[画像对账] 执行异常", e);
        }
    }

    /**
     * 抢对账锁（多实例互斥）
     *
     * <p>与 {@code MatchQueryServiceImpl} / {@code WeatherAdviceServiceImpl} 的
     * 「抢锁失败继续执行」不同：那两处是用户触发的按需生成，抢不到锁也得保证功能可用；
     * 本任务是后台兜底，抢不到（或 Redis 不可用）直接跳过本轮更划算——
     * 下一轮（6 小时后）会再补，而重复执行会把全量 HTTP 同时打到 AI 服务。
     *
     * @return true=抢到锁，可执行；false=跳过本轮
     */
    private boolean tryLock() {
        try {
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                    LOCK_KEY, "1", Duration.ofSeconds(SystemConstants.PROFILE_RECONCILE_LOCK_TTL));
            if (Boolean.TRUE.equals(locked)) {
                return true;
            }
            log.info("[画像对账] 已有实例在对账中，本轮跳过");
            return false;
        } catch (Exception e) {
            // Redis 不可用时保守跳过：宁可晚一轮补，也不要多实例同时全量打 AI 服务
            log.warn("[画像对账] 抢锁失败，本轮跳过: {}", e.getMessage());
            return false;
        }
    }

    /** 释放对账锁；失败只记日志，TTL 到点会自动过期，不会永久卡死 */
    private void releaseLock() {
        try {
            redisTemplate.delete(LOCK_KEY);
        } catch (Exception e) {
            log.warn("[画像对账] 释放锁失败（TTL 到期自动过期）: {}", e.getMessage());
        }
    }

    /**
     * 读 AI 服务 /api/health 的 supplier_count
     *
     * @return 向量库供应商数；AI 服务不可用或响应缺字段时返回 null（调用方跳过本次对账）
     */
    private Integer fetchVectorCount() {
        try {
            Map<?, ?> body = restClient.get()
                    .uri(HEALTH_URI)
                    .retrieve()
                    .body(Map.class);
            Object count = body == null ? null : body.get("supplier_count");
            return count instanceof Number ? ((Number) count).intValue() : null;
        } catch (Exception e) {
            // AI 服务挂了属于可恢复故障，本轮跳过即可，等下轮对账
            log.warn("[画像对账] 调用 AI 服务失败: {}", e.getMessage());
            return null;
        }
    }
}
