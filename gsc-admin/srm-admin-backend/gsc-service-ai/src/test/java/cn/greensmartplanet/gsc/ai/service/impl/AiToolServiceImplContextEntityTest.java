package cn.greensmartplanet.gsc.ai.service.impl;

import cn.greensmartplanet.gsc.ai.dto.AiToolCallRequest;
import cn.greensmartplanet.gsc.ai.dto.AiToolCallResponse;
import cn.greensmartplanet.gsc.ai.dto.AiToolProposal;
import cn.greensmartplanet.gsc.ai.dto.ContextEntityDTO;
import cn.greensmartplanet.gsc.ai.service.AiChatService;
import cn.greensmartplanet.gsc.ai.support.AiToolProposalService;
import cn.greensmartplanet.gsc.ai.tool.AiToolRegistry;
import cn.greensmartplanet.gsc.api.delivery.AiDeliveryClient;
import cn.greensmartplanet.gsc.api.delivery.dto.AiDeliveryDetailDTO;
import cn.greensmartplanet.gsc.api.delivery.dto.AiDeliverySummaryDTO;
import cn.greensmartplanet.gsc.api.supplier.AiSupplierClient;
import cn.greensmartplanet.gsc.api.supplier.dto.AiSupplierDetailDTO;
import cn.greensmartplanet.gsc.api.trade.AiContractClient;
import cn.greensmartplanet.gsc.api.trade.AiInquiryClient;
import cn.greensmartplanet.gsc.api.trade.dto.AiContractSummaryDTO;
import cn.greensmartplanet.gsc.api.trade.dto.AiInquirySummaryDTO;
import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.framework.weather.WeatherClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * AI 工具回传业务对象实体（ai_conversations.context_entities 的数据源）测试。
 * <p>
 * 实体链路：Java 构建 entities → Python 放进 tool_result/confirm_required 事件 →
 * AiChatServiceImpl.updateContextEntities 按 conversationId 覆盖落库。
 * 本测试守住链路起点：读工具按结果集产实体、写工具带出被操作对象、查无结果不产实体。
 */
@ExtendWith(MockitoExtension.class)
class AiToolServiceImplContextEntityTest {

    @Mock
    private AiToolRegistry toolRegistry;
    @Mock
    private AiSupplierClient aiSupplierClient;
    @Mock
    private AiContractClient aiContractClient;
    @Mock
    private AiInquiryClient aiInquiryClient;
    @Mock
    private AiDeliveryClient aiDeliveryClient;
    @Mock
    private AiToolProposalService aiToolProposalService;
    @Mock
    private AiChatService aiChatService;
    @Mock
    private WeatherClient weatherClient;

    @InjectMocks
    private AiToolServiceImpl service;

    private AiToolCallRequest req(String toolName, Map<String, Object> args) {
        return AiToolCallRequest.builder().toolName(toolName).arguments(args).build();
    }

    @Test
    @DisplayName("get_supplier_detail：出参带供应商实体，ID 转字符串")
    void supplierDetail_returnsContextEntity() {
        when(aiSupplierClient.getSupplierDetail(1001L)).thenReturn(R.ok(AiSupplierDetailDTO.builder()
                .tenantId(1001L).companyName("华南钢铁").accessStatus("approved").build()));

        AiToolCallResponse resp = service.execute(
                req(AiToolRegistry.GET_SUPPLIER_DETAIL, Map.of("tenantId", "1001")), 1L, 1L, null);

        assertThat(resp.getEntities()).hasSize(1);
        ContextEntityDTO entity = resp.getEntities().get(0);
        assertThat(entity.getObjectType()).isEqualTo("supplier");
        assertThat(entity.getObjectId()).isEqualTo("1001");
        assertThat(entity.getTitle()).isEqualTo("华南钢铁");
    }

    @Test
    @DisplayName("get_supplier_detail：查无此人时不产实体（不把「查不到」写成有效上下文）")
    void supplierDetail_notFound_returnsNoEntity() {
        when(aiSupplierClient.getSupplierDetail(1001L)).thenReturn(R.ok(null));

        AiToolCallResponse resp = service.execute(
                req(AiToolRegistry.GET_SUPPLIER_DETAIL, Map.of("tenantId", "1001")), 1L, 1L, null);

        assertThat(resp.getEntities()).isEmpty();
    }

    @Test
    @DisplayName("list_contracts：整页出实体并保留列表顺序（供下一轮「第一条/第二条」按位定位）")
    void listContracts_keepsOrder() {
        when(aiContractClient.listContracts(any())).thenReturn(R.ok(List.of(
                AiContractSummaryDTO.builder().id(11L).contractNo("HT001").title("钢材采购合同").build(),
                AiContractSummaryDTO.builder().id(12L).contractNo("HT002").title("铝材采购合同").build())));

        AiToolCallResponse resp = service.execute(
                req(AiToolRegistry.LIST_CONTRACTS, Map.of()), 1L, 1L, null);

        List<ContextEntityDTO> entities = resp.getEntities();
        assertThat(entities).hasSize(2);
        assertThat(entities.get(0).getTitle()).isEqualTo("钢材采购合同");
        assertThat(entities.get(1).getTitle()).isEqualTo("铝材采购合同");
        assertThat(entities.get(1).getObjectId()).isEqualTo("12");
    }

    @Test
    @DisplayName("list_contracts：标题缺失时退化用合同编号")
    void listContracts_fallsBackToContractNo() {
        when(aiContractClient.listContracts(any())).thenReturn(R.ok(List.of(
                AiContractSummaryDTO.builder().id(11L).contractNo("HT001").build())));

        AiToolCallResponse resp = service.execute(
                req(AiToolRegistry.LIST_CONTRACTS, Map.of()), 1L, 1L, null);

        assertThat(resp.getEntities().get(0).getTitle()).isEqualTo("HT001");
    }

    @Test
    @DisplayName("get_delivery_detail：详情继承摘要，自带单号作为实体标题")
    void getDeliveryDetail_returnsEntity() {
        AiDeliveryDetailDTO detail = new AiDeliveryDetailDTO();
        detail.setId(3001L);
        detail.setDeliveryNo("D20260101");
        detail.setContractTitle("钢材采购合同");
        when(aiDeliveryClient.getDelivery(any())).thenReturn(R.ok(detail));

        AiToolCallResponse resp = service.execute(
                req(AiToolRegistry.GET_DELIVERY_DETAIL, Map.of("deliveryId", "3001")), 1L, 1L, null);

        assertThat(resp.getEntities()).hasSize(1);
        ContextEntityDTO entity = resp.getEntities().get(0);
        assertThat(entity.getObjectType()).isEqualTo("delivery");
        assertThat(entity.getObjectId()).isEqualTo("3001");
        assertThat(entity.getTitle()).isEqualTo("D20260101");
    }

    @Test
    @DisplayName("list_deliveries：标题用交付单号（list_deliveries 的 keyword 可搜回）")
    void listDeliveries_usesDeliveryNoAsTitle() {
        when(aiDeliveryClient.listDeliveries(any())).thenReturn(R.ok(List.of(
                AiDeliverySummaryDTO.builder().id(31L).deliveryNo("D001").contractTitle("钢材合同").build())));

        AiToolCallResponse resp = service.execute(
                req(AiToolRegistry.LIST_DELIVERIES, Map.of()), 1L, 1L, null);

        assertThat(resp.getEntities()).hasSize(1);
        assertThat(resp.getEntities().get(0).getTitle()).isEqualTo("D001");
    }

    @Test
    @DisplayName("update_inquiry：提案带出被操作询盘实体，确认文案展示编号+标题而非裸 ID")
    void updateInquiry_proposalCarriesEntity() {
        when(aiToolProposalService.save(any(AiToolProposal.class))).thenReturn("proposal-1");
        when(aiInquiryClient.getInquiry(any())).thenReturn(R.ok(AiInquirySummaryDTO.builder()
                .id(2001L).inquiryNo("RFQ001").title("钢材询价").build()));

        AiToolCallResponse resp = service.execute(
                req(AiToolRegistry.UPDATE_INQUIRY, Map.of("inquiryId", "2001", "title", "新标题")),
                1L, 1L, null);

        assertThat(resp.isRequireConfirm()).isTrue();
        assertThat(resp.getActionSummary()).contains("RFQ001").contains("钢材询价");
        assertThat(resp.getEntities()).hasSize(1);
        assertThat(resp.getEntities().get(0).getObjectType()).isEqualTo("inquiry");
        assertThat(resp.getEntities().get(0).getObjectId()).isEqualTo("2001");
        assertThat(resp.getEntities().get(0).getTitle()).isEqualTo("钢材询价");
    }

    @Test
    @DisplayName("create_supplier_note：确认文案带供应商名（规范 8.3 铁律 4 禁裸 ID），实体同步回填")
    void createSupplierNote_summaryShowsSupplierName() {
        when(aiToolProposalService.save(any(AiToolProposal.class))).thenReturn("proposal-2");
        when(aiSupplierClient.getSupplierDetail(1001L)).thenReturn(R.ok(AiSupplierDetailDTO.builder()
                .tenantId(1001L).companyName("华南钢铁").build()));

        AiToolCallResponse resp = service.execute(
                req(AiToolRegistry.CREATE_SUPPLIER_NOTE,
                        Map.of("supplierTenantId", "1001", "content", "交期稳定")),
                1L, 1L, null);

        assertThat(resp.getActionSummary()).contains("华南钢铁").contains("交期稳定");
        assertThat(resp.getEntities()).hasSize(1);
        assertThat(resp.getEntities().get(0).getObjectType()).isEqualTo("supplier");
        assertThat(resp.getEntities().get(0).getTitle()).isEqualTo("华南钢铁");
    }
}
