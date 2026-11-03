package cn.greensmartplanet.gsc.supplier.service.impl;

import cn.greensmartplanet.gsc.api.trade.RequirementClient;
import cn.greensmartplanet.gsc.api.trade.dto.RequirementMatchFieldsDTO;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.common.result.PageResult;
import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.supplier.entity.EnterpriseProfile;
import cn.greensmartplanet.gsc.supplier.mapper.ConnectionMapper;
import cn.greensmartplanet.gsc.supplier.mapper.EnterpriseProfileMapper;
import cn.greensmartplanet.gsc.supplier.vo.RequirementRecommendVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 找需求推荐：锁住供应商身份、缓存命中不重算、关键词分页。
 */
@ExtendWith(MockitoExtension.class)
class RequirementPlazaRecommendServiceTest {

    @Mock
    private EnterpriseProfileMapper enterpriseProfileMapper;
    @Mock
    private RequirementClient requirementClient;
    @Mock
    private ConnectionMapper connectionMapper;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private RestClient restClient;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ValueOperations<String, Object> valueOps;

    @InjectMocks
    private RequirementPlazaRecommendService service;

    @BeforeEach
    void stubRedis() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("纯采购商画像不能找需求推荐")
    void recommend_buyerProfile_throws() {
        when(enterpriseProfileMapper.selectByTenantId(99L)).thenReturn(
                EnterpriseProfile.builder().tenantId(99L).profileType("buyer").build());

        assertThatThrownBy(() -> service.recommend(99L, null, 1, 10))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.MATCH_INVALID_STATE.getCode());
        verify(requirementClient, never()).recallOpenForMatch(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("缓存命中不再调交易域/Python，当前页回填对接")
    void recommend_cacheHit_skipsRecall() {
        when(enterpriseProfileMapper.selectByTenantId(88L)).thenReturn(supplier());
        RequirementRecommendVO a = row(1L, 201L, "铜缆采购", "线缆", "90");
        RequirementRecommendVO b = row(2L, 202L, "包装箱", "包装", "70");
        when(valueOps.get(anyString())).thenReturn(new ArrayList<>(List.of(a, b)));
        when(connectionMapper.countAcceptedByRequirementIds(anyList())).thenReturn(List.of());
        when(connectionMapper.batchSelectByRequirementIds(anyLong(), anyList())).thenReturn(List.of());
        when(enterpriseProfileMapper.selectByTenantIds(anyList())).thenReturn(List.of());

        PageResult<RequirementRecommendVO> page = service.recommend(88L, null, 1, 10);

        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getRecords()).hasSize(2);
        assertThat(page.getRecords().get(0).getMatchScore()).isEqualByComparingTo("90");
        verify(requirementClient, never()).recallOpenForMatch(org.mockito.ArgumentMatchers.any());
        verify(restClient, never()).post();
    }

    @Test
    @DisplayName("关键词只过滤已排序结果，分页从过滤后切")
    void recommend_keywordAndPaging() {
        when(enterpriseProfileMapper.selectByTenantId(88L)).thenReturn(supplier());
        RequirementRecommendVO a = row(1L, 201L, "铜缆采购", "线缆", "90");
        a.setBuyerCompanyName("华南采购");
        RequirementRecommendVO b = row(2L, 202L, "包装箱", "包装", "70");
        b.setBuyerCompanyName("华东贸易");
        when(valueOps.get(anyString())).thenReturn(new ArrayList<>(List.of(a, b)));
        when(connectionMapper.countAcceptedByRequirementIds(anyList())).thenReturn(List.of());
        when(connectionMapper.batchSelectByRequirementIds(anyLong(), anyList())).thenReturn(List.of());
        when(enterpriseProfileMapper.selectByTenantIds(anyList())).thenReturn(List.of());

        PageResult<RequirementRecommendVO> page = service.recommend(88L, "线缆", 1, 10);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).extracting(RequirementRecommendVO::getTitle).containsExactly("铜缆采购");
        verify(restClient, never()).post();
    }

    @Test
    @DisplayName("Python 精排失败时回退本地打分，匹配度不能空")
    void recommend_pythonDown_stillHasScore() {
        when(enterpriseProfileMapper.selectByTenantId(88L)).thenReturn(supplier());
        when(valueOps.get(anyString())).thenReturn(null);
        RequirementMatchFieldsDTO req = RequirementMatchFieldsDTO.builder()
                .id(1L)
                .tenantId(201L)
                .title("铜缆采购")
                .category("线缆")
                .status("open")
                .isPublic(true)
                .build();
        when(requirementClient.recallOpenForMatch(org.mockito.ArgumentMatchers.any()))
                .thenReturn(R.ok(List.of(req)));
        when(restClient.post()).thenThrow(new RuntimeException("AI down"));
        when(connectionMapper.countAcceptedByRequirementIds(anyList())).thenReturn(List.of());
        when(connectionMapper.batchSelectByRequirementIds(anyLong(), anyList())).thenReturn(List.of());
        when(enterpriseProfileMapper.selectByTenantIds(anyList())).thenReturn(List.of());

        PageResult<RequirementRecommendVO> page = service.recommend(88L, null, 1, 10);

        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).getMatchScore()).isNotNull();
        assertThat(page.getRecords().get(0).getMatchScore()).isPositive();
    }

    private static EnterpriseProfile supplier() {
        return EnterpriseProfile.builder()
                .tenantId(88L)
                .profileType("supplier")
                .supCategories("线缆")
                .province("广东")
                .build();
    }

    private static RequirementRecommendVO row(Long id, Long buyerTenantId, String title, String category, String score) {
        RequirementRecommendVO vo = new RequirementRecommendVO();
        vo.setId(id);
        vo.setTenantId(buyerTenantId);
        vo.setTitle(title);
        vo.setCategory(category);
        vo.setMatchScore(new BigDecimal(score));
        return vo;
    }
}
