package cn.greensmartplanet.gsc.supplier.support;

import cn.greensmartplanet.gsc.api.trade.dto.RequirementMatchFieldsDTO;
import cn.greensmartplanet.gsc.supplier.entity.EnterpriseProfile;
import cn.greensmartplanet.gsc.supplier.mapper.EnterpriseProfileMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 匹配召回：锁住分词与「条件过严回退完整度 TopN」。
 */
@ExtendWith(MockitoExtension.class)
class MatchCandidateRecallTest {

    @Mock
    private EnterpriseProfileMapper enterpriseProfileMapper;

    @InjectMocks
    private MatchCandidateRecall recall;

    @Test
    @DisplayName("品类按逗号顿号拆词，去空白")
    void splitTokens_splitsAndTrims() {
        assertThat(MatchCandidateRecall.splitTokens(null)).isEmpty();
        assertThat(MatchCandidateRecall.splitTokens("  ")).isEmpty();
        assertThat(MatchCandidateRecall.splitTokens("线缆，铜材、 电缆")).containsExactly("线缆", "铜材", "电缆");
    }

    @Test
    @DisplayName("带品类召回为空时回退无过滤 TopN")
    void recall_emptyStrict_fallsBack() {
        RequirementMatchFieldsDTO req = RequirementMatchFieldsDTO.builder()
                .id(10L)
                .tenantId(88L)
                .category("偏门品类")
                .build();
        when(enterpriseProfileMapper.selectSuppliersForRecall(eq(88L), eq(List.of("偏门品类")), isNull(), anyInt()))
                .thenReturn(List.of());
        EnterpriseProfile fallback = new EnterpriseProfile();
        fallback.setTenantId(201L);
        when(enterpriseProfileMapper.selectSuppliersForRecall(eq(88L), eq(List.of()), isNull(), anyInt()))
                .thenReturn(List.of(fallback));

        List<EnterpriseProfile> result = recall.recall(req);

        assertThat(result).hasSize(1);
        verify(enterpriseProfileMapper, times(2)).selectSuppliersForRecall(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("品类和地域分两路召回，品类优先，不去 AND")
    void recall_twoChannels_categoryFirst() {
        RequirementMatchFieldsDTO req = RequirementMatchFieldsDTO.builder()
                .id(10L)
                .tenantId(88L)
                .category("线缆")
                .region("广东")
                .build();
        when(enterpriseProfileMapper.selectSuppliersForRecall(eq(88L), eq(List.of("线缆")), isNull(), anyInt()))
                .thenReturn(List.of(profile(201L), profile(202L)));
        when(enterpriseProfileMapper.selectSuppliersForRecall(eq(88L), eq(List.of()), eq("广东"), anyInt()))
                .thenReturn(List.of(profile(202L), profile(303L)));

        List<EnterpriseProfile> result = recall.recall(req);

        assertThat(result).extracting(EnterpriseProfile::getTenantId).containsExactly(201L, 202L, 303L);
        verify(enterpriseProfileMapper, times(1))
                .selectSuppliersForRecall(eq(88L), eq(List.of("线缆")), isNull(), anyInt());
        verify(enterpriseProfileMapper, times(1))
                .selectSuppliersForRecall(eq(88L), eq(List.of()), eq("广东"), anyInt());
    }

    @Test
    @DisplayName("两路都空才回退完整度 TopN")
    void recall_bothChannelsEmpty_fallsBack() {
        RequirementMatchFieldsDTO req = RequirementMatchFieldsDTO.builder()
                .id(10L)
                .tenantId(88L)
                .category("偏门品类")
                .region("南极")
                .build();
        when(enterpriseProfileMapper.selectSuppliersForRecall(eq(88L), eq(List.of("偏门品类")), isNull(), anyInt()))
                .thenReturn(List.of());
        when(enterpriseProfileMapper.selectSuppliersForRecall(eq(88L), eq(List.of()), eq("南极"), anyInt()))
                .thenReturn(List.of());
        when(enterpriseProfileMapper.selectSuppliersForRecall(eq(88L), eq(List.of()), isNull(), anyInt()))
                .thenReturn(List.of(profile(201L)));

        List<EnterpriseProfile> result = recall.recall(req);

        assertThat(result).extracting(EnterpriseProfile::getTenantId).containsExactly(201L);
        verify(enterpriseProfileMapper, times(3)).selectSuppliersForRecall(any(), any(), any(), anyInt());
    }

    private static EnterpriseProfile profile(Long tenantId) {
        EnterpriseProfile row = new EnterpriseProfile();
        row.setTenantId(tenantId);
        return row;
    }
}
