package cn.greensmartplanet.gsc.supplier.mapper;

import cn.greensmartplanet.gsc.supplier.entity.DueDiligence;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DueDiligenceMapper extends BaseMapper<DueDiligence> {

    /**
     * 按入驻申请ID查尽调记录（1:1）
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

    /**
     * 更新尽调报告
     */
    int updateDdReport(@Param("applicationId") Long applicationId,
                       @Param("ddReportUrl") String ddReportUrl,
                       @Param("ddReportName") String ddReportName);
}
