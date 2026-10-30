package cn.greensmartplanet.gsc.trade.service;

import cn.greensmartplanet.gsc.trade.dto.CreateContractDTO;
import cn.greensmartplanet.gsc.trade.vo.ContractVO;

/**
 * 合同生命周期服务接口 — 创建 / 编辑 / 发起 / 供应商确认/拒绝 / 采购方撤回
 * <p>
 * 生命周期早期状态机：draft(草稿) → pending_supplier(待供应商确认)，
 * 供应商拒绝时 pending_supplier → draft 可多轮往复（磋商）。
 *
 * @author gsc
 */
public interface ContractLifecycleService {

    /**
     * 创建合同：校验询盘已授标+中标报价有效，防重，从报价/询盘推导履约条款，初始状态 draft
     */
    ContractVO create(Long tenantId, Long userId, CreateContractDTO dto);

    /**
     * 编辑合同（仅 draft，采购方）
     */
    void update(Long id, Long tenantId, Long userId, CreateContractDTO dto);

    /**
     * 发起合同（采购方，draft → pending_supplier）
     */
    void submit(Long id, Long tenantId, Long userId);

    /**
     * 供应商确认条款（供应商，pending_supplier 记确认标记，状态不变）
     */
    void confirm(Long id, Long tenantId, Long userId);

    /**
     * 供应商拒绝条款（供应商，pending_supplier → draft，必填理由）
     */
    void decline(Long id, Long tenantId, Long userId, String reason);

    /**
     * 采购方撤回/修订（采购方，pending_supplier → draft，清确认标记与拒绝理由，无需理由）
     */
    void withdraw(Long id, Long tenantId, Long userId);
}