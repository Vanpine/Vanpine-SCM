package cn.greensmartplanet.gsc.delivery.mapper;

import cn.greensmartplanet.gsc.delivery.entity.DeliveryRiskFactor;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 交付风险因子 Mapper
 *
 * @author gsc
 */
public interface DeliveryRiskFactorMapper {

    /** 插入因子（自增 id，create_time/update_time/is_deleted 走 DB 默认值） */
    int insert(DeliveryRiskFactor factor);

    /** 按风险查因子轨迹（时间正序） */
    List<DeliveryRiskFactor> selectByRiskId(@Param("riskId") Long riskId);

    /** 关闭该风险的活跃因子（类型切换/风险缓解时调用） */
    int closeActiveByRiskId(@Param("riskId") Long riskId);
}
