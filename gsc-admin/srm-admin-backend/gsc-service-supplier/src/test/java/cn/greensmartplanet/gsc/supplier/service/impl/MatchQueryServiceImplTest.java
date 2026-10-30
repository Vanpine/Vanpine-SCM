package cn.greensmartplanet.gsc.supplier.service.impl;

import cn.greensmartplanet.gsc.api.trade.InquiryClient;
import cn.greensmartplanet.gsc.api.trade.RequirementClient;
import cn.greensmartplanet.gsc.api.trade.dto.RequirementMatchFieldsDTO;
import cn.greensmartplanet.gsc.common.enums.MatchReasonStatus;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.common.result.PageResult;
import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.supplier.mapper.ConnectionMapper;
import cn.greensmartplanet.gsc.supplier.projection.SupplierConnectionStatusRow;
import cn.greensmartplanet.gsc.supplier.vo.SupplierRecommendVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 广场智能推荐：锁住归属校验、仅 open 可推、缓存命中不重算、分页与 Redis 降级。
 */
@ExtendWith(MockitoExtension.class)
class MatchQueryServiceImplTest {

    @Mock
    private MatchStrategy matchStrategy;
    @Mock
    private RequirementClient requirementClient;
    @Mock
    private InquiryClient inquiryClient;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ConnectionMapper connectionMapper;
    @Mock
    private ValueOperations<String, Object> valueOps;
    @Mock
    private MatchReasonAsyncService matchReasonAsyncService;

    @InjectMocks
    private MatchQueryServiceImpl service;

    @BeforeEach
    void stubRedis() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("非发布方不能看别人需求的推荐")
    void recommend_notOwner_throws() {
        when(requirementClient.getMatchFields(10L)).thenReturn(R.ok(openReq(10L, 88L)));

        assertThatThrownBy(() -> service.recommendForPlaza(10L, 99L, null, 1, 10))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.MATCH_NOT_AUTHORIZED.getCode());
        verify(matchStrategy, never()).match(any(), anyBoolean());
    }

    @Test
    @DisplayName("需求不存在（Feign data=null）按不存在处理")
    void recommend_missingRequirement_throws() {
        when(requirementClient.getMatchFields(10L)).thenReturn(R.ok(null));

        assertThatThrownBy(() -> service.recommendForPlaza(10L, 88L, null, 1, 10))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.MATCH_REQUIREMENT_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("已关闭需求不能广场推荐")
    void recommend_closedRequirement_throws() {
        RequirementMatchFieldsDTO closed = openReq(10L, 88L);
        closed.setStatus("closed");
        when(requirementClient.getMatchFields(10L)).thenReturn(R.ok(closed));

        assertThatThrownBy(() -> service.recommendForPlaza(10L, 88L, null, 1, 10))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.MATCH_INVALID_STATE.getCode());
        verify(matchStrategy, never()).match(any(), anyBoolean());
    }

    @Test
    @DisplayName("缓存命中不再跑匹配策略，当前页回填对接状态")
    void recommend_cacheHit_skipsStrategy() {
        SupplierRecommendVO a = row(201L, "甲工厂", "线缆", "90");
        SupplierRecommendVO b = row(202L, "乙工厂", "包装", "80");
        when(requirementClient.getMatchFields(10L)).thenReturn(R.ok(openReq(10L, 88L)));
        when(valueOps.get(anyString())).thenReturn(new ArrayList<>(List.of(a, b)));
        when(connectionMapper.selectStatusBySupplierIds(anyLong(), anyList()))
                .thenReturn(List.of(statusRow(201L, "pending", 0)));

        PageResult<SupplierRecommendVO> page = service.recommendForPlaza(10L, 88L, null, 1, 10);

        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getRecords()).hasSize(2);
        assertThat(page.getRecords().get(0).getMyConnectionStatus()).isEqualTo("pending");
        assertThat(page.getRecords().get(0).getTenantId()).isEqualTo(201L);
        verify(matchStrategy, never()).match(any(), anyBoolean());
        verify(matchReasonAsyncService, never()).generateLlmReasons(any(), anyString(), anyList());
    }

    @Test
    @DisplayName("详情页命中 :llm 缓存则立刻返回，不调模型")
    void match_llmCacheHit_skipsAsync() {
        SupplierRecommendVO cached = row(201L, "甲工厂", "线缆", "90");
        cached.setReasonStatus(MatchReasonStatus.READY.getCode());
        cached.setMatchReasons(List.of("品类重合"));
        when(requirementClient.getMatchFields(10L)).thenReturn(R.ok(openReq(10L, 88L)));
        when(valueOps.get(contains(":llm"))).thenReturn(new ArrayList<>(List.of(cached)));

        List<SupplierRecommendVO> result = service.getMatchedSuppliers(10L, 88L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getReasonStatus()).isEqualTo(MatchReasonStatus.READY.getCode());
        assertThat(result.get(0).getMatchReasons()).containsExactly("品类重合");
        verify(matchStrategy, never()).match(any(), anyBoolean());
        verify(matchReasonAsyncService, never()).generateLlmReasons(any(), anyString(), anyList());
    }

    @Test
    @DisplayName("详情页无 :llm 时先出名单并异步写理由")
    void match_miss_returnsGeneratingAndTriggersAsync() {
        when(requirementClient.getMatchFields(10L)).thenReturn(R.ok(openReq(10L, 88L)));
        when(valueOps.get(anyString())).thenReturn(null);
        when(matchStrategy.match(any(), eq(false))).thenReturn(List.of(row(201L, "甲工厂", "线缆", "90")));
        when(valueOps.setIfAbsent(contains(":lock"), any(), any(Duration.class))).thenReturn(true);

        List<SupplierRecommendVO> result = service.getMatchedSuppliers(10L, 88L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getReasonStatus()).isEqualTo(MatchReasonStatus.GENERATING.getCode());
        verify(matchStrategy).match(any(), eq(false));
        verify(matchStrategy, never()).match(any(), eq(true));
        verify(matchReasonAsyncService).generateLlmReasons(any(), contains(":llm"), anyList());
    }

    @Test
    @DisplayName("理由锁已被占用时不再丢第二个 DeepSeek 任务")
    void match_lockHeld_skipsAsync() {
        when(requirementClient.getMatchFields(10L)).thenReturn(R.ok(openReq(10L, 88L)));
        when(valueOps.get(anyString())).thenReturn(null);
        when(matchStrategy.match(any(), eq(false))).thenReturn(List.of(row(201L, "甲工厂", "线缆", "90")));
        when(valueOps.setIfAbsent(contains(":lock"), any(), any(Duration.class))).thenReturn(false);

        List<SupplierRecommendVO> result = service.getMatchedSuppliers(10L, 88L);

        assertThat(result.get(0).getReasonStatus()).isEqualTo(MatchReasonStatus.GENERATING.getCode());
        verify(matchReasonAsyncService, never()).generateLlmReasons(any(), anyString(), anyList());
    }

    @Test
    @DisplayName("关键词只过滤已排序结果，分页从过滤后切")
    void recommend_keywordAndPaging() {
        when(requirementClient.getMatchFields(10L)).thenReturn(R.ok(openReq(10L, 88L)));
        when(valueOps.get(anyString())).thenReturn(null);
        when(matchStrategy.match(any(), anyBoolean())).thenReturn(List.of(
                row(201L, "上海线缆厂", "线缆", "90"),
                row(202L, "杭州包装厂", "包装", "80"),
                row(203L, "苏州线缆厂", "线缆", "70")));
        when(connectionMapper.selectStatusBySupplierIds(anyLong(), anyList())).thenReturn(List.of());

        PageResult<SupplierRecommendVO> page = service.recommendForPlaza(10L, 88L, "线缆", 2, 1);

        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getPage()).isEqualTo(2);
        assertThat(page.getPageSize()).isEqualTo(1);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).getCompanyName()).isEqualTo("苏州线缆厂");
    }

    @Test
    @DisplayName("Redis 读取失败降级为现场匹配，不阻断推荐")
    void recommend_redisDown_fallsBackToStrategy() {
        when(requirementClient.getMatchFields(10L)).thenReturn(R.ok(openReq(10L, 88L)));
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("redis down"));
        when(matchStrategy.match(any(), anyBoolean())).thenReturn(List.of(row(201L, "甲工厂", "线缆", "90")));
        when(connectionMapper.selectStatusBySupplierIds(anyLong(), anyList())).thenReturn(List.of());

        PageResult<SupplierRecommendVO> page = service.recommendForPlaza(10L, 88L, null, 1, 10);

        assertThat(page.getRecords()).hasSize(1);
        verify(matchStrategy).match(any(), anyBoolean());
    }

    @Test
    @DisplayName("超管可以替任意租户的需求做推荐")
    void recommend_platformAdmin_passes() {
        when(requirementClient.getMatchFields(10L)).thenReturn(R.ok(openReq(10L, 88L)));
        when(valueOps.get(anyString())).thenReturn(List.of());

        PageResult<SupplierRecommendVO> page = service.recommendForPlaza(10L, 1L, null, 1, 10);

        assertThat(page.getTotal()).isZero();
        assertThat(page.getRecords()).isEmpty();
    }

    private static RequirementMatchFieldsDTO openReq(Long id, Long tenantId) {
        return RequirementMatchFieldsDTO.builder()
                .id(id)
                .tenantId(tenantId)
                .title("采购线缆")
                .category("线缆")
                .status("open")
                .build();
    }

    private static SupplierRecommendVO row(Long tenantId, String name, String category, String score) {
        SupplierRecommendVO vo = new SupplierRecommendVO();
        vo.setTenantId(tenantId);
        vo.setCompanyName(name);
        vo.setSupCategories(category);
        vo.setMatchScore(new BigDecimal(score));
        return vo;
    }

    private static SupplierConnectionStatusRow statusRow(Long supplierId, String status, int count) {
        SupplierConnectionStatusRow row = new SupplierConnectionStatusRow();
        row.setSupplierTenantId(supplierId);
        row.setStatus(status);
        row.setConnectionCount(count);
        return row;
    }
}
