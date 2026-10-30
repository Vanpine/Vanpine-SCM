package cn.greensmartplanet.gsc.delivery.mapper;

import cn.greensmartplanet.gsc.delivery.entity.DeliveryRisk;
import cn.greensmartplanet.gsc.delivery.vo.DeliveryRiskDashboardVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 交付风险 Mapper
 *
 * @author gsc
 */
public interface DeliveryRiskMapper extends BaseMapper<DeliveryRisk> {

    /**
     * 插入风险
     */
    int insert(DeliveryRisk risk);

    /**
     * 按ID查单条
     */
    DeliveryRisk selectById(@Param("id") Long id);

    /**
     * 按交付单查未关闭风险（定时扫描去重，避免重复生成同类风险）
     */
    List<DeliveryRisk> selectActiveByDelivery(@Param("deliveryId") Long deliveryId);

    /**
     * 采购方视角分页查风险（JOIN delivery 定位租户）
     */
    List<DeliveryRisk> selectByBuyer(@Param("tenantId") Long tenantId,
                                     @Param("riskStatus") String riskStatus,
                                     @Param("riskLevel") String riskLevel,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    /**
     * 采购方视角统计
     */
    long countByBuyer(@Param("tenantId") Long tenantId,
                      @Param("riskStatus") String riskStatus,
                      @Param("riskLevel") String riskLevel);

    /**
     * 供应方视角分页查风险
     */
    List<DeliveryRisk> selectBySupplier(@Param("tenantId") Long tenantId,
                                        @Param("riskStatus") String riskStatus,
                                        @Param("riskLevel") String riskLevel,
                                        @Param("offset") int offset,
                                        @Param("limit") int limit);

    /**
     * 供应方视角统计
     */
    long countBySupplier(@Param("tenantId") Long tenantId,
                         @Param("riskStatus") String riskStatus,
                         @Param("riskLevel") String riskLevel);

    /**
     * 更新风险等级/状态/ETA/证据/截止时间（定时扫描升级或处置流转用）
     */
    int updateRiskState(@Param("id") Long id,
                        @Param("riskLevel") String riskLevel,
                        @Param("riskStatus") String riskStatus,
                        @Param("estimatedArrivalTime") LocalDateTime estimatedArrivalTime,
                        @Param("evidence") String evidence,
                        @Param("dueTime") LocalDateTime dueTime);

    /**
     * 关闭风险
     */
    int close(@Param("id") Long id, @Param("riskStatus") String riskStatus);

    /**
     * 按交付单查主表行（含 closed/mitigated，一单最多一行；扫描复用用）
     */
    DeliveryRisk selectByDeliveryId(@Param("deliveryId") Long deliveryId);

    /**
     * 更新风险当前快照：类型/等级/状态/ETA/证据/分数/首测时间（扫描就地更新/复用重启用）
     */
    int updateCurrent(@Param("id") Long id,
                      @Param("riskType") String riskType,
                      @Param("riskLevel") String riskLevel,
                      @Param("riskStatus") String riskStatus,
                      @Param("estimatedArrivalTime") LocalDateTime estimatedArrivalTime,
                      @Param("evidence") String evidence,
                      @Param("riskScore") Integer riskScore,
                      @Param("firstDetectedTime") LocalDateTime firstDetectedTime);

    /**
     * 风险扫描候选：未闭环交付单ID列表（JOIN contract，仅 signed 未完成合同）
     */
    List<Long> selectScanCandidates();

    /** 驾驶舱分页：按交付单聚合活跃风险（采购方视角，一行一交付单） */
    List<DeliveryRiskDashboardVO> selectDashboardByBuyer(@Param("tenantId") Long tenantId,
                                                         @Param("riskStatus") String riskStatus,
                                                         @Param("riskLevel") String riskLevel,
                                                         @Param("offset") int offset,
                                                         @Param("limit") int limit);

    /** 驾驶舱统计：按交付单聚合活跃风险（采购方视角） */
    long countDashboardByBuyer(@Param("tenantId") Long tenantId,
                               @Param("riskStatus") String riskStatus,
                               @Param("riskLevel") String riskLevel);

    /** 驾驶舱分页：按交付单聚合活跃风险（供应方视角，一行一交付单） */
    List<DeliveryRiskDashboardVO> selectDashboardBySupplier(@Param("tenantId") Long tenantId,
                                                           @Param("riskStatus") String riskStatus,
                                                           @Param("riskLevel") String riskLevel,
                                                           @Param("offset") int offset,
                                                           @Param("limit") int limit);

    /** 驾驶舱统计：按交付单聚合活跃风险（供应方视角） */
    long countDashboardBySupplier(@Param("tenantId") Long tenantId,
                                  @Param("riskStatus") String riskStatus,
                                  @Param("riskLevel") String riskLevel);

    /** 类型切换收口：交付单当前风险类型变化时，其他类型的未关闭风险自动缓解 */
    int mitigateOtherTypes(@Param("deliveryId") Long deliveryId,
                           @Param("currentType") String currentType,
                           @Param("riskStatus") String riskStatus);
}
