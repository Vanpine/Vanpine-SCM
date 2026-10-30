package cn.greensmartplanet.gsc.trade.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 工作台统计 Mapper — 询盘/合同计数（从旧单体 DashboardMapper 按表归属迁入）
 * <p>
 * ⚠️ 阶段 8.1：Dashboard BFF 拆分，平台域工作台经 Feign 聚合本域统计。
 * SQL 与原单体 DashboardMapper.xml 前 7 条逐字一致，保证数字基线不变。
 *
 * @author gsc
 */
@Mapper
public interface TradeStatsMapper {

    /** 采购方：按状态统计询盘 */
    long countInquiryByBuyer(@Param("tenantId") Long tenantId, @Param("statuses") List<String> statuses);

    /** 采购方：按状态统计合同 */
    long countContractByBuyer(@Param("tenantId") Long tenantId, @Param("statuses") List<String> statuses);

    /** 供应方：按状态统计合同 */
    long countContractBySupplier(@Param("tenantId") Long tenantId, @Param("statuses") List<String> statuses);

    /** 供应方：待报价询盘（邀请已接受 + 询盘在报价期 + 尚未提交有效报价） */
    long countPendingQuote(@Param("supplierTenantId") Long supplierTenantId);

    /** 供应方：已中标询盘数（按询盘去重） */
    long countAwardBySupplier(@Param("supplierTenantId") Long supplierTenantId);

    /** 超管：平台合同总数（全租户） */
    long countAllContract();
}
