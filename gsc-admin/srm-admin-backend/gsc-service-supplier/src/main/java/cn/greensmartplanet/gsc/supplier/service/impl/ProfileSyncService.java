package cn.greensmartplanet.gsc.supplier.service.impl;

// 实体类 — 数据库映射对象
import cn.greensmartplanet.gsc.supplier.entity.EnterpriseProfile; // 企业画像实体
// Mapper 层 — MyBatis 数据访问
import cn.greensmartplanet.gsc.supplier.mapper.EnterpriseProfileMapper; // 企业画像 Mapper
// Lombok — 消除样板代码
import lombok.RequiredArgsConstructor; // 构造器注入
import lombok.extern.slf4j.Slf4j; // 日志
// Spring 框架
import org.springframework.beans.factory.annotation.Value; // 配置项注入
import org.springframework.http.MediaType; // HTTP Content-Type 常量
import org.springframework.scheduling.annotation.Async; // 异步执行（不阻塞调用方）
import org.springframework.stereotype.Component; // Spring Bean 注解
import org.springframework.web.client.RestClient; // HTTP 客户端（调 Python 服务）

// Java 标准库
import jakarta.annotation.PostConstruct; // Bean 初始化后回调
import java.util.LinkedHashMap; // 保持插入顺序的 Map
import java.util.List; // List 集合
import java.util.Map; // Map 集合

/**
 * 供应商画像向量同步服务
 *
 * <p>职责：将 Java 侧的 EnterpriseProfile 变更同步到 Python Chroma 向量库。
 * 只有向量库中有供应商数据，AI 语义匹配（/api/match）才能召回结果。
 *
 * <p>触发时机：
 * <ol>
 *   <li>服务启动 → {@link #fullSyncOnStartup()} 全量同步现有画像（开关默认关，防止多实例重复全量同步）</li>
 *   <li>画像初始化/更新 → {@link #syncProfile(EnterpriseProfile)} 增量同步单条</li>
 * </ol>
 *
 * <p>容错设计：
 * <ul>
 *   <li>异步执行，不阻塞画像写入主流程</li>
 *   <li>单条失败只记 warn 日志，不影响下一条</li>
 *   <li>Python 服务不可用时不影响 Java 主流程</li>
 * </ul>
 *
 * @author gsc
 */
@Slf4j // Lombok：自动生成 log 对象
@Component // Spring Bean
@RequiredArgsConstructor // Lombok：为 final 字段生成构造器注入
public class ProfileSyncService {

    // ==================== 依赖注入 ====================

    private final EnterpriseProfileMapper enterpriseProfileMapper; // 供应商画像数据访问
    private final RestClient restClient; // 注入全局 RestClient（baseUrl + 超时已预设）

    /** 启动全量同步开关（默认关）——⚠️ 微服务拆分：向量库同步归供应商域，
     *  多实例部署时全量同步需由 XXL-JOB 手动触发（暂缓），避免每实例启动都全量重推 */
    @Value("${gsc.profile-sync.full-on-startup:false}")
    private boolean fullSyncOnStartup;

    // 全量同步时取的供应商数量上限（应与 AiMatchStrategy.MAX_CANDIDATES 保持一致）
    public static final int FULL_SYNC_LIMIT = 500;

    /**
     * 服务启动后异步全量同步现有供应商画像到 Python 向量库
     * <p>
     * 开关：{@code gsc.profile-sync.full-on-startup=false} 默认关闭，
     * 多实例部署时避免每个实例启动都全量重推向量库。
     * <p>
     * ⚠️ 裸线程问题：原实现用 new Thread 自建线程，服务关闭时线程
     * 可能仍在查询已关闭的 HikariPool 导致报错——现由自身 @Async
     * 代理调用（Spring 管理线程池，关闭时先等任务完成再销毁数据源）。
     */
    @PostConstruct
    public void fullSyncOnStartup() {
        if (!fullSyncOnStartup) {
            log.info("画像全量同步已关闭（gsc.profile-sync.full-on-startup=false），跳过启动全量同步");
            return;
        }
        // 经代理调用自身 @Async 方法，线程由 Spring 管理
        asyncFullSync();
    }

    /** @Async 全量同步入口（自代理调用，避免 new Thread 裸线程与关闭时 HikariPool 竞态） */
    @Async
    public void asyncFullSync() {
        try {
            Thread.sleep(5000); // 等 5 秒，确保 Python 服务就绪
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        doFullSync();
    }

    /**
     * 全量同步入口（供对账定时任务等外部调用方使用）
     * <p>
     * 与 {@link #asyncFullSync()} 的区别：本方法同步执行、不等待 Python 服务就绪，
     * 单条失败只记 warn 不外抛（与 {@link #syncProfile} 的容错语义一致），
     * 失败影响面由调用方评估——对账场景下本轮补不齐，下一轮会再触发。
     */
    public void syncAll() {
        doFullSync();
    }

    /**
     * 全量同步核心逻辑
     *
     * <p>从数据库批量拉取供应商画像 → 逐个 POST 到 Python /embed
     */
    private void doFullSync() {
        // 1. 查询供应商总数
        long total = enterpriseProfileMapper.countAllSuppliers(); // 供应商总量
        if (total == 0) { // 无供应商画像
            log.info("[画像同步] 全量同步跳过：数据库中无供应商画像"); // 正常情况（首次启动）
            return; // 无需同步
        }

        // 2. 拉取供应商画像列表（上限 500，按画像完整度排序）
        List<EnterpriseProfile> profiles = enterpriseProfileMapper
                .selectAllSuppliersWithLimit(FULL_SYNC_LIMIT); // Top N 候选池
        if (total > FULL_SYNC_LIMIT) { // 超出上限
            log.warn("[画像同步] 供应商总数{}超过同步上限{}，仅同步前{}条",
                    total, FULL_SYNC_LIMIT, FULL_SYNC_LIMIT); // 告警
        }

        // 3. 逐个同步
        int success = 0; // 成功计数
        int skipped = 0; // 跳过计数（纯买方）
        for (EnterpriseProfile profile : profiles) { // 遍历画像列表
            // 纯买方画像跳过（不参与供应匹配，无需入向量库）
            if ("buyer".equals(profile.getProfileType())) { // profileType 为 buyer
                skipped++; // 跳过计数 +1
                continue; // 下一个
            }
            if (syncOne(profile)) { // 同步单条（返回 true 表示成功）
                success++; // 成功 +1
            }
        }

        log.info("[画像同步] 全量同步完成: 成功{}, 跳过(纯买方){}, 失败{}",
                success, skipped, profiles.size() - success - skipped); // 汇总日志
    }

    // ==================== 增量同步 ====================

    /**
     * 单条画像增量同步（供 {@link ProfileLifecycleServiceImpl} 调用）
     *
     * <p>@Async 确保此方法在 Spring 线程池中异步执行，不阻塞画像写入主流程。
     * 只在画像初始化/更新后调用，不包含纯买方的全量扫描。
     *
     * @param profile 企业画像实体（刚创建或更新后的）
     */
    @Async // Spring 异步执行（@EnableAsync 由 gsc-framework 统一提供）
    public void syncProfile(EnterpriseProfile profile) {
        // 纯买方画像跳过（不参与供应匹配）
        if ("buyer".equals(profile.getProfileType())) { // profileType 为 buyer
            return; // 静默跳过
        }

        syncOne(profile); // 委托给同步方法
    }

    // ==================== 内部实现 ====================

    /**
     * 同步单条画像到 Python /api/embed 端点
     *
     * <p>幂等：Python 侧使用 upsert，重复同步无副作用。
     *
     * @param profile 企业画像实体
     * @return true 同步成功，false 同步失败
     */
    private boolean syncOne(EnterpriseProfile profile) {
        try {
            // 1. 拼接画像文本（与 Python build_profile_text 字段顺序一致）
            String text = buildProfileText(profile); // 拼接中文描述文本
            if (text.isEmpty()) { // 所有关键字段都为空
                log.debug("[画像同步] 跳过空画像, tenantId={}", profile.getTenantId());
                return false; // 不作为有效同步
            }

            // 2. 构造 JSON 请求体（对应 Python EmbedRequest）
            Map<String, Object> body = new LinkedHashMap<>(); // 保持 key 顺序
            body.put("tenant_id", profile.getTenantId()); // 租户ID（向量库唯一标识）
            body.put("text", text); // 待编码的画像文本

            // 3. 发送 POST 请求（复用 RestClient 单例）
            restClient.post() // HTTP POST
                    .uri("/api/embed") // Python 向量化端点
                    .contentType(MediaType.APPLICATION_JSON) // JSON 格式
                    .body(body) // 请求体
                    .retrieve() // 发送请求
                    .toBodilessEntity(); // 不关心响应体（成功 = 200）

            log.debug("[画像同步] 同步成功, tenantId={}, companyName={}",
                    profile.getTenantId(), profile.getCompanyName());
            return true; // 成功

        } catch (Exception e) { // 捕获所有异常（网络、序列化、HTTP 等）
            log.warn("[画像同步] 同步失败, tenantId={}, companyName={}, error={}",
                    profile.getTenantId(), profile.getCompanyName(), e.getMessage());
            return false; // 失败，不抛异常
        }
    }

    // ==================== 文本拼接 ====================

    /**
     * 将 EnterpriseProfile 拼接为语义检索文本
     *
     * <p>字段顺序和拼接格式与 Python {@code build_profile_text()} 严格一致：
     * 公司名。生产品类。主要产品。行业。企业简介
     *
     * @param profile 企业画像实体
     * @return 中文句号拼接的检索文本
     */
    private String buildProfileText(EnterpriseProfile profile) {
        StringBuilder sb = new StringBuilder(); // 文本构造器
        // 公司名称 — 标识维度
        if (profile.getCompanyName() != null && !profile.getCompanyName().isBlank()) {
            sb.append(profile.getCompanyName()); // 追加公司名
        }
        // 生产品类 — 核心匹配维度
        if (profile.getSupCategories() != null && !profile.getSupCategories().isBlank()) {
            if (sb.length() > 0) sb.append("。"); // 已有内容则加句号分隔
            sb.append(profile.getSupCategories()); // 追加品类
        }
        // 主要产品 — 业务描述
        if (profile.getMainProducts() != null && !profile.getMainProducts().isBlank()) {
            if (sb.length() > 0) sb.append("。"); // 句号分隔
            sb.append(profile.getMainProducts()); // 追加产品
        }
        // 所属行业 — 行业分类
        if (profile.getIndustry() != null && !profile.getIndustry().isBlank()) {
            if (sb.length() > 0) sb.append("。"); // 句号分隔
            sb.append(profile.getIndustry()); // 追加行业
        }
        // 企业简介 — 最丰富的自由文本语义信息
        if (profile.getDescription() != null && !profile.getDescription().isBlank()) {
            if (sb.length() > 0) sb.append("。"); // 句号分隔
            sb.append(profile.getDescription()); // 追加简介
        }
        return sb.toString(); // 返回拼接结果
    }
}
