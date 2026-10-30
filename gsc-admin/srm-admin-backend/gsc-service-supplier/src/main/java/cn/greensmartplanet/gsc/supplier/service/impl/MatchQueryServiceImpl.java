package cn.greensmartplanet.gsc.supplier.service.impl;

import cn.greensmartplanet.gsc.api.trade.InquiryClient;
import cn.greensmartplanet.gsc.api.trade.RequirementClient;
import cn.greensmartplanet.gsc.api.trade.dto.RequirementInfoDTO;
import cn.greensmartplanet.gsc.api.trade.dto.RequirementMatchFieldsDTO;
import cn.greensmartplanet.gsc.common.constant.SystemConstants;
import cn.greensmartplanet.gsc.common.enums.MatchReasonStatus;
import cn.greensmartplanet.gsc.common.enums.RequirementStatus;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.common.result.PageResult;
import cn.greensmartplanet.gsc.supplier.mapper.ConnectionMapper;
import cn.greensmartplanet.gsc.supplier.projection.SupplierConnectionStatusRow;
import cn.greensmartplanet.gsc.supplier.service.MatchQueryService;
import cn.greensmartplanet.gsc.supplier.vo.SupplierRecommendVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 智能匹配查询服务实现 — 匹配候选 / 询盘拒绝标注 / 广场智能推荐
 * <p>
 * 需求表/询盘邀请表归交易域，Feign 查询（匹配入参/归属校验/拒绝标注）；
 * 匹配策略仍在本域（MatchStrategy 策略族）。归属权限校验在此层完成，Controller 薄壳。
 * <p>
 * 匹配结果走 Redis（TTL 10 分钟）。广场与详情共用召回精排缓存（无 :llm）；
 * 详情 AI 理由另写 :llm，主请求不调 DeepSeek。对接状态每次现查，不进缓存。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchQueryServiceImpl implements MatchQueryService {

    private final MatchStrategy matchStrategy;
    // 需求表归交易域，Feign 查询（匹配入参/归属校验）
    private final RequirementClient requirementClient;
    // 询盘邀请表归交易域，Feign 查询（拒绝标注）
    private final InquiryClient inquiryClient;
    // 匹配结果缓存：JSON 序列化，key 见 SystemConstants.MATCH_RECOMMEND_CACHE_PREFIX
    private final RedisTemplate<String, Object> redisTemplate;
    // 广场推荐回填对接状态（本域 connection 表，批量查消 N+1）
    private final ConnectionMapper connectionMapper;
    // Redis 反序列化偶发 LinkedHashMap，转回 VO 用
    private final ObjectMapper objectMapper;
    // 详情页 AI 理由后台写 :llm，不挡主请求
    private final MatchReasonAsyncService matchReasonAsyncService;

    /** 智能匹配候选供应商：名单同步返回，AI 理由后台补 */
    @Override
    public List<SupplierRecommendVO> getMatchedSuppliers(Long requirementId, Long tenantId) {
        // 1. 取需求匹配字段并校验归属（智能匹配页允许看已关闭需求的历史结果）
        RequirementMatchFieldsDTO requirement = loadOwnedRequirement(requirementId, tenantId);
        String llmKey = buildCacheKey(requirement, true);

        // 2. 已有 AI / 兜底理由：直接给详情页，不再打模型
        List<SupplierRecommendVO> llmCached = readCache(llmKey);
        if (llmCached != null) {
            return copyRows(llmCached, MatchReasonStatus.READY.getCode(), false);
        }

        // 3. 先走无 LLM 精排（可复用广场缓存），立刻出名单
        List<SupplierRecommendVO> ranked = matchWithCache(requirement, false);
        if (ranked.isEmpty()) {
            return List.of();
        }

        // 4. 抢锁后异步写 :llm；锁已存在说明上一轮还在生成
        tryTriggerLlmReasons(requirement, llmKey, ranked);
        return copyRows(ranked, MatchReasonStatus.GENERATING.getCode(), true);
    }

    /** 查拒绝过该需求相关询盘的供应商（智能匹配页"已拒绝该询盘"标注）——询盘表归交易域，Feign 查询 */
    @Override
    public List<Long> getDeclinedSupplierIds(Long requirementId, Long tenantId) {
        // 1. 需求归交易域：Feign 校验存在与归属（降级 null 与直查为 null 语义一致）
        RequirementInfoDTO requirement = requirementClient.getById(requirementId).getData();
        if (requirement == null) {
            throw new BizException(ErrorCode.MATCH_REQUIREMENT_NOT_FOUND);
        }
        // 2. 权限校验：只有需求发布方或超管可调用
        assertOwner(requirement.getTenantId(), tenantId);
        // 3. 询盘归交易域，Feign 查拒绝标注
        return inquiryClient.getDeclinedSuppliers(requirementId, tenantId).getData();
    }

    /**
     * 需求广场智能推荐：校验进行中需求 → 缓存匹配 → 关键词过滤 → 分页 → 回填对接状态
     */
    @Override
    public PageResult<SupplierRecommendVO> recommendForPlaza(Long requirementId, Long tenantId,
                                                             String keyword, int pageNum, int pageSize) {
        // 1. 分页参数钳制：页码至少 1，每页 1～100，避免负 offset 或一次拉全量
        int safePage = Math.max(pageNum, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), SystemConstants.MATCH_RECOMMEND_MAX_PAGE_SIZE);

        // 2. 取需求并校验归属；广场只给进行中的需求做推荐
        RequirementMatchFieldsDTO requirement = loadOwnedRequirement(requirementId, tenantId);
        if (!RequirementStatus.OPEN.getCode().equals(requirement.getStatus())) {
            throw new BizException(ErrorCode.MATCH_INVALID_STATE, "仅进行中的需求可智能推荐");
        }

        // 3. 匹配（Redis 命中则不再扫画像 / 调 AI）
        List<SupplierRecommendVO> ranked = matchWithCache(requirement, false);

        // 4. 关键词只做二次过滤，不改变排序——用户可在推荐结果里再收窄
        List<SupplierRecommendVO> filtered = filterByKeyword(ranked, keyword);
        int total = filtered.size();

        // 5. 内存分页：匹配已是全量排序结果；越界页返回空列表而不是报错
        int from = (safePage - 1) * safeSize;
        if (total == 0 || from >= total) {
            return PageResult.of(Collections.emptyList(), total, safePage, safeSize);
        }
        int to = Math.min(from + safeSize, total);

        // 6. 拷贝当前页：回填对接状态；广场只要分，理由留给「智能匹配」详情页
        List<SupplierRecommendVO> page = filtered.subList(from, to).stream()
                .map(this::copyPlazaRow)
                .toList();
        fillConnectionStatus(page, tenantId);

        log.info("广场智能推荐完成: requirementId={}, keyword={}, total={}, page={}/{}",
                requirementId, keyword, total, safePage, safeSize);
        return PageResult.of(page, total, safePage, safeSize);
    }

    // ==================== 匹配缓存 ====================

    /**
     * 先读 Redis，miss 再跑策略并回写。
     * Redis 故障降级为直接匹配，不能因为缓存把推荐整页打挂。
     */
    private List<SupplierRecommendVO> matchWithCache(RequirementMatchFieldsDTO requirement, boolean needLlmReasons) {
        String cacheKey = buildCacheKey(requirement, needLlmReasons);
        List<SupplierRecommendVO> cached = readCache(cacheKey);
        if (cached != null) {
            log.info("智能匹配命中缓存: requirementId={}, llm={}, size={}",
                    requirement.getId(), needLlmReasons, cached.size());
            return cached;
        }
        List<SupplierRecommendVO> fresh = matchStrategy.match(requirement, needLlmReasons);
        writeCache(cacheKey, fresh);
        return fresh;
    }

    /**
     * 缓存 key = 前缀 + 需求ID + 字段指纹 + 是否含 LLM 理由。
     * 广场和详情页不能共用：一个没有理由、一个有 AI 理由。
     */
    private String buildCacheKey(RequirementMatchFieldsDTO req, boolean needLlmReasons) {
        String fingerprint = String.join("|",
                Objects.toString(req.getCategory(), ""),
                Objects.toString(req.getRegion(), ""),
                Objects.toString(req.getCertRequire(), ""),
                Objects.toString(req.getDescription(), ""));
        String key = SystemConstants.MATCH_RECOMMEND_CACHE_PREFIX + req.getId() + ":" + fingerprint.hashCode();
        return needLlmReasons ? key + ":llm" : key;
    }

    /**
     * 同一需求只丢一个 DeepSeek 任务。锁 90 秒，任务结束会删锁。
     */
    private void tryTriggerLlmReasons(RequirementMatchFieldsDTO requirement, String llmKey,
                                      List<SupplierRecommendVO> fallbackRows) {
        String lockKey = llmKey + ":lock";
        try {
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                    lockKey, "1", Duration.ofSeconds(SystemConstants.MATCH_REASON_LOCK_TTL));
            if (Boolean.FALSE.equals(locked)) {
                log.info("AI 理由已在生成: requirementId={}", requirement.getId());
                return;
            }
        } catch (Exception e) {
            log.warn("抢 AI 理由锁失败, requirementId={}", requirement.getId(), e);
        }
        matchReasonAsyncService.generateLlmReasons(requirement, llmKey, fallbackRows);
    }

    /**
     * 拷贝后再盖理由状态，避免污染广场那份缓存对象。
     *
     * @param force true=一律写成 status（生成中）；false=行上已有状态则保留（ready/fallback）
     */
    private List<SupplierRecommendVO> copyRows(List<SupplierRecommendVO> src, String status, boolean force) {
        List<SupplierRecommendVO> out = new ArrayList<>(src.size());
        for (SupplierRecommendVO row : src) {
            SupplierRecommendVO vo = new SupplierRecommendVO();
            BeanUtils.copyProperties(row, vo);
            if (force || !StringUtils.hasText(vo.getReasonStatus())) {
                vo.setReasonStatus(status);
            }
            out.add(vo);
        }
        return out;
    }

    /**
     * 读缓存：只接受 List&lt;SupplierRecommendVO&gt;。
     * Jackson 反序列化成别的类型时整份作废，避免脏数据进列表。
     */
    private List<SupplierRecommendVO> readCache(String key) {
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (!(cached instanceof List<?> raw)) {
                return null;
            }
            if (raw.isEmpty()) {
                return List.of();
            }
            List<SupplierRecommendVO> result = new ArrayList<>(raw.size());
            for (Object item : raw) {
                SupplierRecommendVO vo = toRecommendVo(item);
                if (vo == null) {
                    log.warn("匹配缓存元素类型异常, key={}, type={}",
                            key, item == null ? "null" : item.getClass().getName());
                    return null;
                }
                result.add(vo);
            }
            return result;
        } catch (Exception e) {
            log.warn("读取匹配缓存失败, key={}", key, e);
            return null;
        }
    }

    /**
     * Redis Jackson 带类型头时是 VO；偶发只有 Map，转回 VO 才算命中。
     */
    private SupplierRecommendVO toRecommendVo(Object item) {
        if (item instanceof SupplierRecommendVO vo) {
            return vo;
        }
        if (item instanceof Map<?, ?>) {
            return objectMapper.convertValue(item, SupplierRecommendVO.class);
        }
        return null;
    }

    /** 写缓存：失败只打日志，不影响本次返回 */
    private void writeCache(String key, List<SupplierRecommendVO> list) {
        try {
            redisTemplate.opsForValue().set(key, new ArrayList<>(list),
                    Duration.ofSeconds(SystemConstants.MATCH_RECOMMEND_CACHE_TTL));
        } catch (Exception e) {
            log.warn("写入匹配缓存失败, key={}", key, e);
        }
    }

    // ==================== 广场组装 ====================

    /**
     * 对已排序结果按关键词过滤企业名/品类/行业/省/市。
     * 空关键词原样返回，保持匹配分顺序。
     */
    private List<SupplierRecommendVO> filterByKeyword(List<SupplierRecommendVO> ranked, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return ranked;
        }
        String kw = keyword.trim().toLowerCase();
        return ranked.stream()
                .filter(vo -> containsIgnoreCase(vo.getCompanyName(), kw)
                        || containsIgnoreCase(vo.getSupCategories(), kw)
                        || containsIgnoreCase(vo.getIndustry(), kw)
                        || containsIgnoreCase(vo.getProvince(), kw)
                        || containsIgnoreCase(vo.getCity(), kw))
                .toList();
    }

    /** 批量回填当前采购方与本页供应商的对接状态/已接受数（投影 DTO，禁止 Map 强转） */
    private void fillConnectionStatus(List<SupplierRecommendVO> page, Long buyerTenantId) {
        if (page.isEmpty() || buyerTenantId == null) {
            return;
        }
        List<Long> supplierIds = page.stream().map(SupplierRecommendVO::getTenantId).toList();
        List<SupplierConnectionStatusRow> rows = connectionMapper.selectStatusBySupplierIds(buyerTenantId, supplierIds);
        Map<Long, SupplierConnectionStatusRow> statusMap = new HashMap<>();
        for (SupplierConnectionStatusRow row : rows) {
            statusMap.put(row.getSupplierTenantId(), row);
        }
        for (SupplierRecommendVO vo : page) {
            SupplierConnectionStatusRow row = statusMap.get(vo.getTenantId());
            if (row != null) {
                vo.setMyConnectionStatus(row.getStatus());
                vo.setMyConnectionCount(row.getConnectionCount() != null ? row.getConnectionCount() : 0);
            }
        }
    }

    /** 浅拷贝一行给广场：去掉理由，对接状态现填，避免污染缓存对象 */
    private SupplierRecommendVO copyPlazaRow(SupplierRecommendVO src) {
        SupplierRecommendVO vo = new SupplierRecommendVO();
        BeanUtils.copyProperties(src, vo);
        vo.setMatchReasons(null);
        vo.setReasonStatus(null);
        vo.setMyConnectionStatus(null);
        vo.setMyConnectionCount(null);
        return vo;
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    // ==================== 权限 ====================

    /**
     * Feign 取匹配字段 + 归属校验。
     * 降级 null 按需求不存在处理，不能把空需求丢进匹配引擎。
     */
    private RequirementMatchFieldsDTO loadOwnedRequirement(Long requirementId, Long tenantId) {
        RequirementMatchFieldsDTO requirement = requirementClient.getMatchFields(requirementId).getData();
        if (requirement == null) {
            throw new BizException(ErrorCode.MATCH_REQUIREMENT_NOT_FOUND);
        }
        assertOwner(requirement.getTenantId(), tenantId);
        return requirement;
    }

    /** 归属权限校验：仅需求发布方或超管可访问 */
    private void assertOwner(Long requirementOwnerTenantId, Long currentTenantId) {
        if (currentTenantId != null && currentTenantId == 1L) {
            return; // 超管放行
        }
        if (currentTenantId == null || !currentTenantId.equals(requirementOwnerTenantId)) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED, "无权访问该需求的匹配结果");
        }
    }
}
