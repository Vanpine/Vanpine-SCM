package cn.greensmartplanet.gsc.delivery.service;

import cn.greensmartplanet.gsc.delivery.dto.CompleteRecoveryActionDTO;
import cn.greensmartplanet.gsc.delivery.dto.CreateRecoveryActionDTO;
import cn.greensmartplanet.gsc.delivery.vo.RecoveryActionVO;

import java.util.List;

/**
 * 交付补救工单服务接口
 * recovery  恢复
 *
 * @author gsc
 */
public interface DeliveryRecoveryActionService {

    /**
     * 创建补救工单（用户确认推荐方案后生成）
     */
    RecoveryActionVO create(Long tenantId, Long userId, CreateRecoveryActionDTO dto);

    /**
     * 按风险查补救工单列表
     */
    List<RecoveryActionVO> listByRisk(Long riskId, Long tenantId);

    /**
     * 开始执行工单
     */
    void start(Long id, Long tenantId, Long userId);

    /**
     * 完成工单（填回执/结果），满足条件后自动缓解风险
     */
    void complete(Long id, Long tenantId, Long userId, CompleteRecoveryActionDTO dto);

    /**
     * 关闭风险：须有已完成补救工单回执或风险已缓解，否则拒绝
     */
    void closeRisk(Long riskId, Long tenantId, Long userId);
}
