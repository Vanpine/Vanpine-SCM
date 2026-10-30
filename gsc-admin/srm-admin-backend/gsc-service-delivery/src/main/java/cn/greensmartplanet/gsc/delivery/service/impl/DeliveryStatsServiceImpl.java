package cn.greensmartplanet.gsc.delivery.service.impl;

import cn.greensmartplanet.gsc.api.delivery.dto.DeliveryStatsDTO;
import cn.greensmartplanet.gsc.delivery.mapper.DeliveryStatsMapper;
import cn.greensmartplanet.gsc.delivery.enums.DeliveryStatus;
import cn.greensmartplanet.gsc.delivery.service.DeliveryStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 工作台统计服务实现 — 交付/超期计数（SQL 从旧单体 DashboardMapper 按表归属迁入）
 * <p>
 * 状态集合与原单体 DashboardServiceImpl 常量逐项一致：
 * DELIVERY_TO_SHIP=(pending_shipment,returned)、DELIVERY_TO_RECEIVE=(shipped)、
 * DELIVERY_TO_ACCEPT=(received)、DELIVERY_OVERDUE_DAYS=3。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryStatsServiceImpl implements DeliveryStatsService {

    private static final List<String> DELIVERY_TO_SHIP = List.of(DeliveryStatus.PENDING_SHIPMENT.getCode(), DeliveryStatus.RETURNED.getCode());
    private static final List<String> DELIVERY_TO_RECEIVE = List.of(DeliveryStatus.SHIPPED.getCode());
    private static final List<String> DELIVERY_TO_ACCEPT = List.of(DeliveryStatus.RECEIVED.getCode());
    /** 发货后/收货后超过该天数仍未处理，记为超期待办 */
    private static final int DELIVERY_OVERDUE_DAYS = 3;

    private final DeliveryStatsMapper statsMapper;

    /** 工作台交付/超期计数（采购+供应两视角，BFF 按角色取用对应字段） */
    @Override
    public DeliveryStatsDTO getDashboardStats(Long tenantId) {
        // 1. 一次查齐 6 项计数（采购/供应双视角 + 3 项超期），BFF 按角色取用对应字段
        return DeliveryStatsDTO.builder()
                // 1a. 采购方：待收货（shipped）/待验收（received）
                .deliveryBuyerToReceive(statsMapper.countDeliveryByBuyer(tenantId, DELIVERY_TO_RECEIVE))
                .deliveryBuyerToAccept(statsMapper.countDeliveryByBuyer(tenantId, DELIVERY_TO_ACCEPT))
                // 1b. 供应方：待发货（pending_shipment/returned）
                .deliverySupplierToShip(statsMapper.countDeliveryBySupplier(tenantId, DELIVERY_TO_SHIP))
                // 1c. 超期项：未发货（过约定交期）/未收货、未验收（超 3 天）
                .overdueUnshipped(statsMapper.countOverdueUnshipped(tenantId))
                .overdueUnreceived(statsMapper.countOverdueUnreceived(tenantId, DELIVERY_OVERDUE_DAYS))
                .overdueUnaccepted(statsMapper.countOverdueUnaccepted(tenantId, DELIVERY_OVERDUE_DAYS))
                .build();
    }
}
