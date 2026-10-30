package cn.greensmartplanet.gsc.trade.service;

import cn.greensmartplanet.gsc.api.trade.dto.ContractCompleteDTO;

/**
 * 合同完成服务接口 — 完成 / 终止 / 交付域回写完成
 * <p>
 * 合同终态（signed → completed / terminated）归口本服务。
 *
 * @author gsc
 */
public interface ContractCompletionService {

    /**
     * 完成合同（signed → completed，采购方；存在进行中交付单则禁止手动完成）
     */
    void complete(Long id, Long tenantId, Long userId);

    /**
     * 终止合同（signed → terminated，采购方；存在进行中交付单则禁止终止，原因必填）
     */
    void terminate(Long id, Long tenantId, Long userId, String reason, String terminateType);

    /**
     * 交付域回写：合同全部交付验收完成 → signed/completed → completed（幂等：已 completed 返回 true）
     */
    boolean updateCompleted(Long id, ContractCompleteDTO context);
}
