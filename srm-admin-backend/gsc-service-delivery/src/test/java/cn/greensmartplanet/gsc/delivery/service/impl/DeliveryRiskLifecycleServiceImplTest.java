package cn.greensmartplanet.gsc.delivery.service.impl;

import cn.greensmartplanet.gsc.api.trade.ContractClient;
import cn.greensmartplanet.gsc.api.trade.dto.ContractInfoDTO;
import cn.greensmartplanet.gsc.common.enums.ContractStatus;
import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.delivery.entity.Delivery;
import cn.greensmartplanet.gsc.delivery.entity.DeliveryRisk;
import cn.greensmartplanet.gsc.delivery.enums.DeliveryStatus;
import cn.greensmartplanet.gsc.delivery.enums.LogisticsEventType;
import cn.greensmartplanet.gsc.delivery.enums.RiskLevel;
import cn.greensmartplanet.gsc.delivery.enums.RiskStatus;
import cn.greensmartplanet.gsc.delivery.enums.RiskType;
import cn.greensmartplanet.gsc.delivery.mapper.Delivery8dReportMapper;
import cn.greensmartplanet.gsc.delivery.mapper.DeliveryLogisticsEventMapper;
import cn.greensmartplanet.gsc.delivery.mapper.DeliveryMapper;
import cn.greensmartplanet.gsc.delivery.mapper.DeliveryRiskFactorMapper;
import cn.greensmartplanet.gsc.delivery.mapper.DeliveryRiskMapper;
import cn.greensmartplanet.gsc.delivery.mq.producer.WeatherAdvicePublisher;
import cn.greensmartplanet.gsc.delivery.service.NotificationService;
import cn.greensmartplanet.gsc.delivery.support.DeliveryTraceSupport;
import cn.greensmartplanet.gsc.delivery.support.WeatherRiskEvaluator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 回归：delivery_risk 有唯一索引（uk_delivery_active），并发实例同时插入时第二个会抛
 * DuplicateKeyException。该异常若不捕获，会从 evaluateDelivery 冒泡出 scanRisks 的
 * for 循环，导致整轮扫描中断、剩余交付单本轮全部跳过。
 */
@ExtendWith(MockitoExtension.class)
class DeliveryRiskLifecycleServiceImplTest {

    private static final Long DELIVERY_ID = 100L;
    private static final Long CONTRACT_ID = 1L;

    @Mock
    private DeliveryRiskMapper riskMapper;
    @Mock
    private DeliveryRiskFactorMapper factorMapper;
    @Mock
    private DeliveryLogisticsEventMapper eventMapper;
    @Mock
    private DeliveryMapper deliveryMapper;
    @Mock
    private Delivery8dReportMapper report8dMapper;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ContractClient contractClient;
    @Mock
    private WeatherRiskEvaluator weatherRiskEvaluator;
    @Mock
    private DeliveryTraceSupport traceSupport;
    @Mock
    private WeatherAdvicePublisher weatherAdvicePublisher;

    @InjectMocks
    private DeliveryRiskLifecycleServiceImpl service;

    @Test
    @DisplayName("插入撞唯一索引时捕获异常并转更新，scanRisks 不中断")
    void scanRisks_duplicateKey_doesNotAbort() {
        stubScanContext();

        // 第一次查为空 → 走插入；插入撞唯一索引；重查拿到并发实例刚写的那行
        DeliveryRisk concurrent = new DeliveryRisk();
        concurrent.setId(9L);
        concurrent.setRiskType(RiskType.LATE_SHIPMENT.getCode());
        concurrent.setRiskStatus(RiskStatus.WARNED.getCode());
        concurrent.setRiskLevel(RiskLevel.LOW.getCode());
        when(riskMapper.selectByDeliveryId(DELIVERY_ID)).thenReturn(null, concurrent);
        when(riskMapper.insert(any(DeliveryRisk.class))).thenThrow(new DuplicateKeyException("uk_delivery_active"));

        service.scanRisks(); // 不抛即通过

        // 落到「已有主表行」分支：走 updateCurrent 而不是中断
        verify(riskMapper).insert(any(DeliveryRisk.class));
        verify(riskMapper).updateCurrent(eq(9L), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("重查仍为空说明不是并发冲突，异常照常抛出")
    void scanRisks_duplicateKeyWithoutConcurrentRow_rethrows() {
        stubScanContext();
        when(riskMapper.selectByDeliveryId(DELIVERY_ID)).thenReturn(null);
        when(riskMapper.insert(any(DeliveryRisk.class))).thenThrow(new DuplicateKeyException("uk_delivery_active"));

        assertThatThrownBy(service::scanRisks).isInstanceOf(DuplicateKeyException.class);
    }

    /** 打桩一轮扫描的最小上下文：1 个待发货且已过计划发运日的交付单（触发 LATE_SHIPMENT） */
    private void stubScanContext() {
        Delivery d = new Delivery();
        d.setId(DELIVERY_ID);
        d.setContractId(CONTRACT_ID);
        d.setStatus(DeliveryStatus.PENDING_SHIPMENT.getCode());

        ContractInfoDTO c = new ContractInfoDTO();
        c.setId(CONTRACT_ID);
        c.setStatus(ContractStatus.SIGNED.getCode());
        c.setPlannedShipDate(LocalDate.now().minusDays(1));

        when(riskMapper.selectScanCandidates()).thenReturn(List.of(DELIVERY_ID));
        when(deliveryMapper.selectBatchIds(List.of(DELIVERY_ID))).thenReturn(List.of(d));
        when(contractClient.getContractByIds(List.of(CONTRACT_ID))).thenReturn(R.ok(Map.of(CONTRACT_ID, c)));
        when(eventMapper.selectLatestByDeliveryIds(List.of(DELIVERY_ID))).thenReturn(List.of());
        when(eventMapper.selectLatestEtaByDeliveryIds(List.of(DELIVERY_ID))).thenReturn(List.of());
        when(eventMapper.selectDeliveryIdsWithEventType(List.of(DELIVERY_ID), LogisticsEventType.CUSTOMS_HOLD.getCode()))
                .thenReturn(List.of());
    }
}
