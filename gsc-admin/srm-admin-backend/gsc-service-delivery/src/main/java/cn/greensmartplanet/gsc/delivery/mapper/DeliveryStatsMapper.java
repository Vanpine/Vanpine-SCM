package cn.greensmartplanet.gsc.delivery.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 工作台统计 Mapper — 交付/超期计数（从旧单体 DashboardMapper 按表归属迁入）
 * <p>
 * ⚠️ 阶段 8.1：Dashboard BFF 拆分，平台域工作台经 Feign 聚合本域统计。
 * SQL 与原单体 DashboardMapper.xml 交付段逐字一致，保证数字基线不变。
 *
 * @author gsc
 */
@Mapper
public interface DeliveryStatsMapper {

    /** 采购方：按状态统计交付单 */
    long countDeliveryByBuyer(@Param("tenantId") Long tenantId, @Param("statuses") List<String> statuses);

    /** 供应方：按状态统计交付单 */
    long countDeliveryBySupplier(@Param("tenantId") Long tenantId, @Param("statuses") List<String> statuses);

    /** 供应方：超期未发货 */
    long countOverdueUnshipped(@Param("tenantId") Long tenantId);

    /** 采购方：超期未收货（发货后超过 overdueDays 天） */
    long countOverdueUnreceived(@Param("tenantId") Long tenantId, @Param("overdueDays") int overdueDays);

    /** 采购方：超期未验收（收货后超过 overdueDays 天） */
    long countOverdueUnaccepted(@Param("tenantId") Long tenantId, @Param("overdueDays") int overdueDays);
}
