package cn.greensmartplanet.gsc.supplier.service.impl;

import cn.greensmartplanet.gsc.api.platform.ApplicationClient;
import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.framework.oss.OssService;
import cn.greensmartplanet.gsc.supplier.entity.EnterpriseProfile;
import cn.greensmartplanet.gsc.supplier.mapper.ConnectionMapper;
import cn.greensmartplanet.gsc.supplier.mapper.DueDiligenceMapper;
import cn.greensmartplanet.gsc.supplier.mapper.EnterpriseProfileMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * AI 查供应商详情的可见性校验测试。
 * <p>
 * 列表接口（searchForAi）用 {@code profile_type IN ('supplier','both')} 过滤，
 * 详情接口若不校验同一规则，就会形成「列表查不到、详情却能拿到」的越权面——
 * AI 工具可凭任意 tenantId 拿到纯采购方租户的完整画像（年营收 / 主要客户 / 员工规模）。
 */
@ExtendWith(MockitoExtension.class)
class ProfileQueryServiceVisibilityTest {

    @Mock
    private EnterpriseProfileMapper enterpriseProfileMapper;
    @Mock
    private DueDiligenceMapper dueDiligenceMapper;
    @Mock
    private ConnectionMapper connectionMapper;
    @Mock
    private ApplicationClient applicationClient;
    @Mock
    private OssService ossService;

    @InjectMocks
    private ProfileQueryServiceImpl service;

    private static final Long TARGET = 1001L;

    private EnterpriseProfile profile(String type) {
        return EnterpriseProfile.builder()
                .tenantId(TARGET)
                .profileType(type)
                .companyName("某公司")
                .build();
    }

    /** 尽调装载：无申请映射时提前返回空 Map，不走 mapper。 */
    private void stubNoDueDiligence() {
        lenient().when(applicationClient.getIdsByTenantIds(any())).thenReturn(R.ok(Map.of()));
    }

    @Test
    @DisplayName("采购方型画像（profile_type=buyer）不可通过 AI 详情接口拿到")
    void buyerProfileNotVisible() {
        when(enterpriseProfileMapper.selectByTenantId(TARGET)).thenReturn(profile("buyer"));

        assertThat(service.getDetailForAi(TARGET)).isNull();
    }

    @Test
    @DisplayName("供应商型画像可见（不误伤正常查询）")
    void supplierProfileVisible() {
        when(enterpriseProfileMapper.selectByTenantId(TARGET)).thenReturn(profile("supplier"));
        stubNoDueDiligence();

        assertThat(service.getDetailForAi(TARGET)).isNotNull();
    }

    @Test
    @DisplayName("双重身份（profile_type=both）可见")
    void bothProfileVisible() {
        when(enterpriseProfileMapper.selectByTenantId(TARGET)).thenReturn(profile("both"));
        stubNoDueDiligence();

        assertThat(service.getDetailForAi(TARGET)).isNotNull();
    }

    @Test
    @DisplayName("画像不存在返回 null（语义不变）")
    void missingProfileReturnsNull() {
        when(enterpriseProfileMapper.selectByTenantId(TARGET)).thenReturn(null);

        assertThat(service.getDetailForAi(TARGET)).isNull();
    }
}
