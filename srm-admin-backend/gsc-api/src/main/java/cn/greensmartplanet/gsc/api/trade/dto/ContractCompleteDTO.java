package cn.greensmartplanet.gsc.api.trade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 合同完成回写契约 DTO — 交付域末批验收后回写合同状态的请求体
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractCompleteDTO {

    /** 采购方租户ID（回写 SQL 的归属校验条件） */
    private Long buyerTenantId;
    /** 触发验收的用户ID（记入 update_by） */
    private Long userId;
}
