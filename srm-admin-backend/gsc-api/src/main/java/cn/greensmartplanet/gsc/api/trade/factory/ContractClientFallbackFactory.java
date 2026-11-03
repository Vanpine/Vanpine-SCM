package cn.greensmartplanet.gsc.api.trade.factory;

import cn.greensmartplanet.gsc.api.trade.ContractClient;
import cn.greensmartplanet.gsc.api.trade.dto.ContractBatchPlanDTO;
import cn.greensmartplanet.gsc.api.trade.dto.ContractCompleteDTO;
import cn.greensmartplanet.gsc.api.trade.dto.ContractInfoDTO;
import cn.greensmartplanet.gsc.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 交易域合同查询 Feign 降级工厂
 * <p>
 * 降级决策：
 * <ul>
 *   <li>查询返回 null——列表回填最多缺文案；发货/建单校验处判空抛原有错误码</li>
 *   <li>状态回写返回 false——降级时拒绝完成合同（交付侧状态不推进），
 *       交易域恢复后可由再次验收触发重试，不会错误置 completed</li>
 * </ul>
 *
 * @author gsc
 */
@Slf4j
@Component
public class ContractClientFallbackFactory implements FallbackFactory<ContractClient> {

    @Override
    public ContractClient create(Throwable cause) {
        return new ContractClient() {
            @Override
            public R<ContractInfoDTO> getContractById(Long id) {
                log.warn("交易服务不可用，合同查询降级返回 null: id={}, cause={}",
                        id, cause != null ? cause.getMessage() : "unknown");
                return R.ok(null);
            }

            @Override
            public R<Map<Long, ContractInfoDTO>> getContractByIds(List<Long> ids) {
                log.warn("交易服务不可用，批量合同查询降级返回空: size={}, cause={}",
                        ids != null ? ids.size() : 0,
                        cause != null ? cause.getMessage() : "unknown");
                return R.ok(Map.of());
            }

            @Override
            public R<Map<Long, String>> getInquiryNosByContractIds(List<Long> contractIds) {
                log.warn("交易服务不可用，询盘编号查询降级返回空: size={}, cause={}",
                        contractIds != null ? contractIds.size() : 0,
                        cause != null ? cause.getMessage() : "unknown");
                return R.ok(Map.of());
            }

            @Override
            public R<List<ContractInfoDTO>> listSignedBySupplier(Long supplierTenantId) {
                log.warn("交易服务不可用，已签署合同列表降级返回空: supplierTenantId={}, cause={}",
                        supplierTenantId, cause != null ? cause.getMessage() : "unknown");
                return R.ok(List.of());
            }

            @Override
            public R<Map<Long, List<ContractBatchPlanDTO>>> getBatchPlansByContractIds(List<Long> contractIds) {
                log.warn("交易服务不可用，分批计划查询降级返回空: size={}, cause={}",
                        contractIds != null ? contractIds.size() : 0,
                        cause != null ? cause.getMessage() : "unknown");
                return R.ok(Map.of());
            }

            @Override
            public R<Boolean> updateCompleted(Long id, ContractCompleteDTO context) {
                log.error("交易服务不可用，合同完成状态回写降级拒绝: id={}, cause={}",
                        id, cause != null ? cause.getMessage() : "unknown");
                return R.ok(false);
            }
        };
    }
}
