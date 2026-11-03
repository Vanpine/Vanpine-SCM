package cn.greensmartplanet.gsc.api.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 交付域工作台统计契约 DTO — 平台域工作台 BFF 经 Feign 聚合的交付/超期计数
 * <p>
 * 字段用 long 基本类型：缺失/降级时自然为 0，调用方无需判空。
 * 状态集合定义与原单体 DashboardServiceImpl 保持一致（shipped/received/pending_shipment 等）。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryStatsDTO {

    /** 采购方：交付待收货（shipped） */
    private long deliveryBuyerToReceive;
    /** 采购方：交付待验收（received） */
    private long deliveryBuyerToAccept;
    /** 供应方：交付待发货（pending_shipment/returned） */
    private long deliverySupplierToShip;
    /** 供应方：超期未发货（delivery_date 已过） */
    private long overdueUnshipped;
    /** 采购方：超期未收货（shipped 超 3 天） */
    private long overdueUnreceived;
    /** 采购方：超期未验收（received 超 3 天） */
    private long overdueUnaccepted;
}
