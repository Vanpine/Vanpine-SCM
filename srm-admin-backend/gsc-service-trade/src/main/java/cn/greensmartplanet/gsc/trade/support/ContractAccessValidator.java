package cn.greensmartplanet.gsc.trade.support;

import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.trade.entity.Contract;
import cn.greensmartplanet.gsc.trade.mapper.ContractMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 合同归属校验器 — 校验合同存在 + 操作方身份（红线 2：资源归属校验是业务逻辑一部分）
 * <p>
 * 抽离自 ContractServiceImpl 的私有方法 getContract / getContractAnyParty，
 * 供 查询/签署/完成/生命周期 四个拆分服务共用，避免重复实现。
 *
 * @author gsc
 */
@Component
@RequiredArgsConstructor
public class ContractAccessValidator {

    private final ContractMapper contractMapper;

    /**
     * 查合同并校验买方权限（仅采购方可执行的操作：编辑/发起/撤回/完成/终止）
     */
    public Contract getContract(Long id, Long tenantId) {
        Contract contract = contractMapper.selectById(id);
        if (contract == null) {
            throw new BizException(ErrorCode.PROCUREMENT_CONTRACT_NOT_FOUND);
        }
        if (!tenantId.equals(contract.getBuyerTenantId())) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }
        return contract;
    }

    /**
     * 查合同并校验任一方（采购方或供应方）归属权限（详情/确认/拒绝/盖章等双方操作）
     */
    public Contract getContractAnyParty(Long id, Long tenantId) {
        Contract contract = contractMapper.selectById(id);
        if (contract == null) {
            throw new BizException(ErrorCode.PROCUREMENT_CONTRACT_NOT_FOUND);
        }
        if (!tenantId.equals(contract.getBuyerTenantId()) && !tenantId.equals(contract.getSupplierTenantId())) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }
        return contract;
    }
}
