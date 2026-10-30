package cn.greensmartplanet.gsc.platform.mapper;

import cn.greensmartplanet.gsc.platform.entity.DueDiligence;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DueDiligenceMapper extends BaseMapper<DueDiligence> {

    /**
     * 按入驻申请ID查尽调记录（1:1）
      * <p>⚠️ 迁移期桥接（微服务拆分）：本文件归属供应商域（阶段 7 gsc-service-supplier），platform 内为过渡副本，supplier 上线后随桥接收敛删除/改 Feign。</p>
 */
    DueDiligence selectByApplicationId(@Param("applicationId") Long applicationId);

    /**
     * 批量按入驻申请ID查尽调记录
     */
    List<DueDiligence> selectByApplicationIds(@Param("applicationIds") List<Long> applicationIds);

    /**
     * 按主键查
     */
    DueDiligence selectById(@Param("id") Long id);

    /**
     * 新增尽调记录
     */
    int insert(DueDiligence dd);

    /**
     * 更新尽调信息（用户填表保存）
     */
    int update(DueDiligence dd);

    /**
     * 更新支付状态
     */
    int updatePaymentStatus(@Param("applicationId") Long applicationId,
                            @Param("packageType") String packageType,
                            @Param("paymentAmount") Long paymentAmount,
                            @Param("paymentCurrency") String paymentCurrency);

    /** 下单后写入待支付单号，不改 paid */
    int updatePaymentIntent(@Param("applicationId") Long applicationId,
                            @Param("packageType") String packageType,
                            @Param("paymentAmount") Long paymentAmount,
                            @Param("paymentCurrency") String paymentCurrency,
                            @Param("outTradeNo") String outTradeNo,
                            @Param("orderNo") String orderNo);

    @Select("""
            SELECT id, application_id, package_type, payment_status, payment_amount, payment_currency,
                   payment_out_trade_no, payment_order_no
            FROM due_diligence
            WHERE payment_out_trade_no = #{outTradeNo} AND is_deleted = 0
            LIMIT 1
            """)
    DueDiligence selectByOutTradeNo(@Param("outTradeNo") String outTradeNo);

    /**
     * 更新尽调报告
     */
    int updateDdReport(@Param("applicationId") Long applicationId,
                       @Param("ddReportUrl") String ddReportUrl,
                       @Param("ddReportName") String ddReportName);

    /**
     * 按事务所ID查已分配的尽调记录
     */
    List<DueDiligence> selectByFirmId(@Param("firmId") Long firmId);

    /**
     * 分配事务所 + 更新状态
     */
    int updateFirmInfo(@Param("applicationId") Long applicationId,
                       @Param("firmId") Long firmId,
                       @Param("firmName") String firmName,
                       @Param("firmStatus") String firmStatus,
                       @Param("deadline") java.time.LocalDate deadline,
                       @Param("instruction") String instruction);

    /**
     * 事务所提交尽调结果
     */
    int submitFirmResult(@Param("applicationId") Long applicationId,
                         @Param("firmStatus") String firmStatus,
                         @Param("firmProgress") String firmProgress,
                         @Param("reportUrl") String reportUrl,
                         @Param("reportName") String reportName);

    /**
     * 事务所退回尽调给企业补充材料（状态 assigned → returned）
     */
    int returnToEnterprise(@Param("applicationId") Long applicationId,
                           @Param("returnReason") String returnReason,
                           @Param("firmId") Long firmId);

    /**
     * 管理员确认尽调通过（submitted → completed，含报告状态）
     */
    int confirmDdComplete(@Param("applicationId") Long applicationId);

    /**
     * 按租户ID列表批量查尽调（JOIN application 表）
     */
    List<DueDiligence> selectByTenantIds(@Param("tenantIds") List<Long> tenantIds);
}
