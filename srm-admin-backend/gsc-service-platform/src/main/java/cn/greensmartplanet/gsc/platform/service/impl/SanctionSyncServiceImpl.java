package cn.greensmartplanet.gsc.platform.service.impl;

import cn.greensmartplanet.gsc.common.constant.SystemConstants;
import cn.greensmartplanet.gsc.platform.entity.SanctionEntry;
import cn.greensmartplanet.gsc.platform.mapper.SanctionEntryMapper;
import cn.greensmartplanet.gsc.platform.service.SanctionSyncService;
import cn.greensmartplanet.gsc.platform.vo.SanctionSyncStatusVO;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 制裁名单同步服务实现 — 定时/手动拉取 OpenSanctions consolidated sanctions 本地落库
 * <p>
 * 同步流程：GET index.json（拿最新 version）→ GET targets.simple.csv（约 70MB）→ 流式解析
 * → 按 entity_id 批量 upsert（500 条/批）。
 * <p>
 * 异步设计：同步耗时长（下载+解析+入库约 1-3 分钟），接口立即返回，后台经
 * {@code SanctionSyncAsyncRunner} 的 @Async 执行，前端通过 getStatus() 轮询进度
 * （running → success/failed），避免网关 10s 超时。
 * <p>
 * ⚠️ 多实例：同步锁与状态均存 Redis，跨实例共享（见 CLAUDE.md §7.2）。
 * <p>
 * 容错：同步失败仅记状态告警，不影响既有名单数据（不全量清库），下次调度/手动重试覆盖。
 *
 * @author gsc
 */
@Slf4j
@Service
public class SanctionSyncServiceImpl implements SanctionSyncService {

    /** OpenSanctions 数据源基地址（latest 路径自动重定向到最新版本） */
    @Value("${gsc.sanction.data-url:https://data.opensanctions.org/datasets/latest/sanctions}")
    private String dataUrl;

    private final SanctionEntryMapper sanctionEntryMapper;
    private final ObjectMapper objectMapper;
    private final cn.greensmartplanet.gsc.platform.support.SanctionNameIndex nameIndex;
    /** 异步执行器：@Async 必须在独立 Bean 上才能经代理生效（同类自调用会绕过代理） */
    private final cn.greensmartplanet.gsc.platform.support.SanctionSyncAsyncRunner asyncRunner;
    /** Redis 客户端：同步锁（多实例互斥） */
    private final org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;
    /** 索引刷新广播器：同步完成后通知其它实例重建本地索引 */
    private final cn.greensmartplanet.gsc.platform.mq.producer.SanctionIndexRefreshPublisher indexRefreshPublisher;

    /** 每批 upsert 条数（控制单条 SQL 体积） */
    private static final int BATCH_SIZE = 500;
    /** 字段最大长度（超出截断，防 DB 超长报错） */
    private static final int NAME_MAX = 500;
    private static final int ENTITY_ID_MAX = 64;
    private static final int TYPE_MAX = 32;
    private static final int JURISDICTION_MAX = 200;
    private static final int PROGRAM_MAX = 500;
    private static final int SOURCE_MAX = 500;

    /**
     * 同步状态 Redis key（跨实例共享）。
     * <p>原为 volatile 实例字段：多实例部署下前端轮询 getStatus() 被负载均衡轮转到别的实例
     * 会读到 "never" 或旧值，状态来回跳、重启后回退。
     */
    private static final String STATUS_KEY = "sanction:sync:status";

    /** 同步锁 key：多实例只有一个实例能执行同步（原 AtomicBoolean 只挡本 JVM，多实例失效） */
    private static final String SYNC_LOCK_KEY = "sanction:sync:lock";

    public SanctionSyncServiceImpl(SanctionEntryMapper sanctionEntryMapper, ObjectMapper objectMapper,
                                   cn.greensmartplanet.gsc.platform.support.SanctionNameIndex nameIndex,
                                   cn.greensmartplanet.gsc.platform.support.SanctionSyncAsyncRunner asyncRunner,
                                   org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate,
                                   cn.greensmartplanet.gsc.platform.mq.producer.SanctionIndexRefreshPublisher indexRefreshPublisher) {
        this.sanctionEntryMapper = sanctionEntryMapper;
        this.objectMapper = objectMapper;
        this.nameIndex = nameIndex;
        this.asyncRunner = asyncRunner;
        this.stringRedisTemplate = stringRedisTemplate;
        this.indexRefreshPublisher = indexRefreshPublisher;
    }

    @Override
    public void triggerSync() {
        // 多实例互斥：抢不到锁说明别的实例正在同步，忽略本次触发
        if (!tryLock()) {
            log.info("[制裁同步] 已有同步任务在运行，忽略本次触发");
            return;
        }
        writeStatus("running", null, null, null);
        // 交给独立 Bean 的 @Async 方法执行：同类 this 调用会绕过 Spring 代理、静默退化为同步阻塞
        asyncRunner.run(this::doSyncThenReset);
    }

    /**
     * 抢同步锁（Redis setIfAbsent + TTL），多实例互斥。
     * <p>
     * Redis 不可用时保守跳过：宁可本轮不同步（下轮定时任务会再触发），
     * 也不要多实例同时下载 70MB CSV 并写库。
     */
    private boolean tryLock() {
        try {
            Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(
                    SYNC_LOCK_KEY, "1", Duration.ofSeconds(SystemConstants.SANCTION_SYNC_LOCK_TTL));
            return Boolean.TRUE.equals(locked);
        } catch (Exception e) {
            log.warn("[制裁同步] 抢锁失败，跳过本次触发: {}", e.getMessage());
            return false;
        }
    }

    /** 释放同步锁；失败只记日志，TTL 到点会自动过期，不会永久卡死 */
    private void releaseLock() {
        try {
            stringRedisTemplate.delete(SYNC_LOCK_KEY);
        } catch (Exception e) {
            log.warn("[制裁同步] 释放锁失败（TTL 到期自动过期）: {}", e.getMessage());
        }
    }

    @Override
    public SanctionSyncStatusVO getStatus() {
        SanctionSyncStatusVO vo = new SanctionSyncStatusVO();
        vo.setTotalCount(sanctionEntryMapper.countAll());
        vo.setLastSyncedAt(sanctionEntryMapper.selectMaxSyncedAt());
        // 状态从 Redis 读（跨实例共享），未写入过时回退默认值
        Map<Object, Object> status = readStatus();
        vo.setLastSyncStatus(text(status.get("status"), "never"));
        vo.setLastSyncError(text(status.get("error"), null));
        vo.setSyncedCount(intValue(status.get("count")));
        vo.setVersion(text(status.get("version"), null));
        return vo;
    }

    /**
     * 写同步状态到 Redis（跨实例共享）。
     *
     * @param status  运行状态 running / success / failed
     * @param error   失败原因；成功或运行中传 null（写空串）
     * @param count   已入库条数；传 null 表示本次不改（如刚置 running）
     * @param version 名单版本；传 null 表示本次不改
     */
    private void writeStatus(String status, String error, Integer count, String version) {
        try {
            Map<String, String> fields = new HashMap<>();
            fields.put("status", status);
            fields.put("error", error == null ? "" : error);
            if (count != null) {
                fields.put("count", String.valueOf(count));
            }
            if (version != null) {
                fields.put("version", version);
            }
            stringRedisTemplate.opsForHash().putAll(STATUS_KEY, fields);
        } catch (Exception e) {
            // 状态是旁路展示数据，写失败不影响同步本身
            log.warn("[制裁同步] 状态写入 Redis 失败: {}", e.getMessage());
        }
    }

    /** 读同步状态；Redis 不可用或未写入时返回空 Map（调用方回退默认值） */
    private Map<Object, Object> readStatus() {
        try {
            return stringRedisTemplate.opsForHash().entries(STATUS_KEY);
        } catch (Exception e) {
            log.warn("[制裁同步] 状态读取 Redis 失败: {}", e.getMessage());
            return Map.of();
        }
    }

    /** 取字符串字段；null 或空串回退 fallback */
    private static String text(Object v, String fallback) {
        String s = v == null ? null : v.toString();
        return (s == null || s.isEmpty()) ? fallback : s;
    }

    /** 取整数字段；缺失或非法回退 0 */
    private static int intValue(Object v) {
        try {
            return v == null ? 0 : Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 后台执行同步，结束后复位 running 标志。
     * <p>由 {@link #triggerSync()} 经 {@code SanctionSyncAsyncRunner} 的 @Async 方法调用——
     * 不经本类 this 调用，否则会绕过 Spring 代理、静默退化为同步执行。
     */
    private void doSyncThenReset() {
        try {
            doSync();
        } finally {
            releaseLock();
        }
    }

    /**
     * 同步核心逻辑：下载 index.json + CSV → 解析 → 分批 upsert。
     */
    private void doSync() {
        long start = System.currentTimeMillis();
        int inserted = 0;
        String v = null;
        try {
            // 1. 拿最新版本元数据
            byte[] indexBody = download(dataUrl + "/index.json");
            JsonNode index = objectMapper.readTree(indexBody);
            v = index.path("version").asText("unknown");
            log.info("[制裁同步] 获取最新版本: version={}", v);

            // 2. 下载名单 CSV（约 70MB）
            byte[] csvBody = download(dataUrl + "/targets.simple.csv");
            log.info("[制裁同步] CSV 下载完成: {} 字节", csvBody.length);

            // 3. 流式解析 CSV + 分批 upsert
            inserted = parseAndUpsert(csvBody);

            // 4. 记录成功状态 + 重建内存索引（筛查走索引，同步后刷新）
            writeStatus("success", null, inserted, v);
            nameIndex.rebuild();                    // 本实例立即重建索引
            indexRefreshPublisher.publish();        // 广播其它实例重建（索引是每实例一份的内存 Map）
            log.info("[制裁同步] 完成: version={}, upsert={}, 耗时={}ms",
                    v, inserted, System.currentTimeMillis() - start);
        } catch (Exception e) {
            writeStatus("failed", e.getMessage(), null, null);
            log.error("[制裁同步] 失败: version={}, error={}", v, e.getMessage(), e);
        }
    }

    /**
     * 解析 CSV 并按批 upsert。返回 upsert 的总条数。
     */
    private int parseAndUpsert(byte[] csvBody) throws Exception {
        int total = 0;
        LocalDateTime syncedAt = LocalDateTime.now();
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(csvBody), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            List<SanctionEntry> batch = new ArrayList<>(BATCH_SIZE);
            for (CSVRecord r : parser) {
                SanctionEntry e = toEntry(r, syncedAt);
                if (e == null || e.getEntityId() == null || e.getEntityId().isBlank()) {
                    continue; // 无实体 ID 的记录跳过
                }
                batch.add(e);
                total++;
                if (batch.size() >= BATCH_SIZE) {
                    sanctionEntryMapper.insertBatchUpsert(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                sanctionEntryMapper.insertBatchUpsert(batch);
            }
        }
        return total;
    }

    /**
     * CSV 行 → 实体。字段取不到返回空串，超长截断。
     */
    private SanctionEntry toEntry(CSVRecord r, LocalDateTime syncedAt) {
        String name = trimTo(r.get("name"), NAME_MAX);
        if (name == null || name.isBlank()) {
            return null; // 无名主体不可用于匹配，跳过
        }
        SanctionEntry e = new SanctionEntry();
        e.setId(IdWorker.getId());
        e.setEntityId(trimTo(r.get("id"), ENTITY_ID_MAX));
        e.setName(name);
        e.setAliases(nullToEmpty(r.get("aliases")));
        e.setEntityType(trimTo(r.get("schema"), TYPE_MAX));
        e.setJurisdiction(trimTo(r.get("countries"), JURISDICTION_MAX));
        e.setProgram(trimTo(r.get("program_ids"), PROGRAM_MAX));
        e.setSourceName(trimTo(r.get("dataset"), SOURCE_MAX));
        e.setLastSyncedAt(syncedAt);
        return e;
    }

    private String trimTo(String s, int max) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String v = s.trim();
        return v.length() > max ? v.substring(0, max) : v;
    }

    private String nullToEmpty(String s) {
        return s;
    }

    /**
     * 下载远程文件为字节数组。专用 RestClient（读超时 5 分钟，覆盖 70MB 下载）。
     */
    private byte[] download(String url) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30_000);
        factory.setReadTimeout(300_000);
        RestClient client = RestClient.builder().requestFactory(factory).build();
        return client.get()
                .uri(url)
                .retrieve()
                .body(byte[].class);
    }
}
