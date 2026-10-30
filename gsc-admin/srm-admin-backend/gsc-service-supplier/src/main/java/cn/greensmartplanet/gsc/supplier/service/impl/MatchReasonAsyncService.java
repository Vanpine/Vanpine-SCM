package cn.greensmartplanet.gsc.supplier.service.impl;

import cn.greensmartplanet.gsc.api.trade.dto.RequirementMatchFieldsDTO;
import cn.greensmartplanet.gsc.common.constant.SystemConstants;
import cn.greensmartplanet.gsc.common.enums.MatchReasonStatus;
import cn.greensmartplanet.gsc.supplier.vo.SupplierRecommendVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 详情页 AI 理由后台生成。主请求只出名单，这里调 DeepSeek 后写入 :llm 缓存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchReasonAsyncService {

    private final MatchStrategy matchStrategy;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 异步写 AI 理由。失败则把模板理由标成 fallback，前端轮询可以停。
     *
     * @param requirement  已校验归属的需求字段
     * @param llmCacheKey  Redis key（带 :llm）
     * @param fallbackRows 主请求已算出的模板理由名单
     */
    @Async
    public void generateLlmReasons(RequirementMatchFieldsDTO requirement, String llmCacheKey,
                                   List<SupplierRecommendVO> fallbackRows) {
        try {
            List<SupplierRecommendVO> withAi = matchStrategy.match(requirement, true);
            writeLlmCache(llmCacheKey, withAi, MatchReasonStatus.READY.getCode());
            log.info("AI 匹配理由已缓存: requirementId={}, size={}",
                    requirement.getId(), withAi.size());
        } catch (Exception e) {
            log.warn("AI 匹配理由生成失败，回退模板理由: requirementId={}", requirement.getId(), e);
            writeLlmCache(llmCacheKey, fallbackRows, MatchReasonStatus.FALLBACK.getCode());
        } finally {
            try {
                redisTemplate.delete(llmCacheKey + ":lock");
            } catch (Exception e) {
                log.warn("释放 AI 理由锁失败, key={}", llmCacheKey, e);
            }
        }
    }

    private void writeLlmCache(String key, List<SupplierRecommendVO> rows, String status) {
        List<SupplierRecommendVO> source = rows == null ? Collections.emptyList() : rows;
        List<SupplierRecommendVO> stamped = new ArrayList<>(source.size());
        for (SupplierRecommendVO row : source) {
            SupplierRecommendVO vo = new SupplierRecommendVO();
            BeanUtils.copyProperties(row, vo);
            vo.setReasonStatus(status);
            stamped.add(vo);
        }
        redisTemplate.opsForValue().set(key, stamped,
                Duration.ofSeconds(SystemConstants.MATCH_RECOMMEND_CACHE_TTL));
    }
}
