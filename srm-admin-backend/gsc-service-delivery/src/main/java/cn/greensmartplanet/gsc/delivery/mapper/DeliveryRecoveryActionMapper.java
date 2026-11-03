package cn.greensmartplanet.gsc.delivery.mapper;

import cn.greensmartplanet.gsc.delivery.entity.DeliveryRecoveryAction;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 交付补救工单 Mapper
 *
 * @author gsc
 */
public interface DeliveryRecoveryActionMapper extends BaseMapper<DeliveryRecoveryAction> {

    /** 插入补救工单 */
    int insert(DeliveryRecoveryAction action);

    /** 按ID查单条 */
    DeliveryRecoveryAction selectById(@Param("id") Long id);

    /** 按风险查补救工单（时间倒序） */
    List<DeliveryRecoveryAction> selectByRiskId(@Param("riskId") Long riskId);

    /** 按风险查已完成的补救工单数（关闭风险证据校验用） */
    int countCompletedByRiskId(@Param("riskId") Long riskId);

    /** 开始执行工单：pending → executing */
    int start(@Param("id") Long id, @Param("ownerUserId") Long ownerUserId);

    /** 完成工单：executing → completed，填结果/回执 */
    int complete(@Param("id") Long id,
                 @Param("executionRemark") String executionRemark,
                 @Param("result") String result,
                 @Param("completedTime") LocalDateTime completedTime);
}
