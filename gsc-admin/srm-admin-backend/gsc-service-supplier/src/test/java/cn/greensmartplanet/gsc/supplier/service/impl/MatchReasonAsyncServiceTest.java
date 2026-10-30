package cn.greensmartplanet.gsc.supplier.service.impl;

import cn.greensmartplanet.gsc.api.trade.dto.RequirementMatchFieldsDTO;
import cn.greensmartplanet.gsc.common.enums.MatchReasonStatus;
import cn.greensmartplanet.gsc.supplier.vo.SupplierRecommendVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 异步理由：成功写 ready，失败写 fallback，最后都要删锁。
 */
@ExtendWith(MockitoExtension.class)
class MatchReasonAsyncServiceTest {

    @Mock
    private MatchStrategy matchStrategy;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOps;

    @InjectMocks
    private MatchReasonAsyncService service;

    @BeforeEach
    void stubRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("DeepSeek 成功则 :llm 写入 ready")
    void generate_success_writesReady() {
        SupplierRecommendVO ai = row(201L);
        ai.setMatchReasons(List.of("AI 理由"));
        when(matchStrategy.match(any(), eq(true))).thenReturn(List.of(ai));

        service.generateLlmReasons(req(), "match:recommend:10:1:llm", List.of(row(201L)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SupplierRecommendVO>> captor = ArgumentCaptor.forClass(List.class);
        verify(valueOps).set(eq("match:recommend:10:1:llm"), captor.capture(), any());
        assertThat(captor.getValue().get(0).getReasonStatus()).isEqualTo(MatchReasonStatus.READY.getCode());
        assertThat(captor.getValue().get(0).getMatchReasons()).containsExactly("AI 理由");
        verify(redisTemplate).delete("match:recommend:10:1:llm:lock");
    }

    @Test
    @DisplayName("DeepSeek 失败则把模板理由标 fallback 写入 :llm")
    void generate_fail_writesFallback() {
        when(matchStrategy.match(any(), anyBoolean())).thenThrow(new RuntimeException("llm down"));
        SupplierRecommendVO template = row(201L);
        template.setMatchReasons(List.of("品类重合"));

        service.generateLlmReasons(req(), "match:recommend:10:1:llm", List.of(template));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SupplierRecommendVO>> captor = ArgumentCaptor.forClass(List.class);
        verify(valueOps).set(eq("match:recommend:10:1:llm"), captor.capture(), any());
        assertThat(captor.getValue().get(0).getReasonStatus()).isEqualTo(MatchReasonStatus.FALLBACK.getCode());
        assertThat(captor.getValue().get(0).getMatchReasons()).containsExactly("品类重合");
        verify(redisTemplate).delete("match:recommend:10:1:llm:lock");
    }

    private static RequirementMatchFieldsDTO req() {
        return RequirementMatchFieldsDTO.builder().id(10L).tenantId(88L).title("采购线缆").build();
    }

    private static SupplierRecommendVO row(Long tenantId) {
        SupplierRecommendVO vo = new SupplierRecommendVO();
        vo.setTenantId(tenantId);
        vo.setCompanyName("甲工厂");
        vo.setMatchScore(new BigDecimal("90"));
        return vo;
    }
}
