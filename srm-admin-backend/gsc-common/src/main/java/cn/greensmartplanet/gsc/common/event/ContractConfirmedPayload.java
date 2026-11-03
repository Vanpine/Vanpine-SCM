package cn.greensmartplanet.gsc.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 合同确认批次事件载荷（gsc-contract-confirmed，eventVersion=1）
 * <p>
 * 事件契约类：trade 域生产、delivery 域消费，字段即契约，双方共用本类避免类型漂移。
 * 消费侧仍按 contractId 重查合同并校验状态（signed），载荷仅作触达与租户校验依据。
 *
 * @author gsc
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContractConfirmedPayload {

    /** 合同ID（消费幂等键之一，bizKey=contract:{contractId}） */
    private Long contractId;
    /** 询盘ID */
    private Long inquiryId;
    /** 履约数量（第 1 批全量） */
    private Integer quantity;
    /** 合同金额 */
    private BigDecimal amount;
    /** 币种 */
    private String currency;
    /** 交付条款 */
    private String deliveryTerms;
    /** 交付地点 */
    private String deliveryPlace;
    /** 交付日期 */
    private LocalDate deliveryDate;
    /** 买方租户ID（消费侧租户校验依据） */
    private Long buyerTenantId;
    /** 供应商租户ID */
    private Long supplierTenantId;
    /** 创建人 */
    private Long createBy;
}
