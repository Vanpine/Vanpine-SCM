package cn.greensmartplanet.gsc.api.trade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 合同信息契约 DTO — 交易域内部接口返回给交付域的合同字段子集
 * <p>
 * 交付域使用场景（Feign 契约）：
 * <ul>
 *   <li>列表/详情回填（contractNo/title/inquiryId）</li>
 *   <li>手动建交付单校验（status/supplierTenantId/quantity）</li>
 *   <li>发货数量守恒校验（quantity）</li>
 *   <li>风险扫描（productionNeedDate/plannedShipDate/allowedDelayDays/exceptionNoticeHours/partialDeliveryAllowed/materialCriticalLevel/inventoryCoverDays）</li>
 *   <li>MQ 消费者重建交付单（inquiryId/buyerTenantId/supplierTenantId/quantity/amount/currency/deliveryTerms/deliveryPlace/deliveryDate/createBy）</li>
 * </ul>
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractInfoDTO {

    /** 合同ID */
    private Long id;
    /** 合同编号 */
    private String contractNo;
    /** 合同标题 */
    private String title;
    /** 关联询盘ID */
    private Long inquiryId;
    /** 采购方租户ID */
    private Long buyerTenantId;
    /** 供应方租户ID */
    private Long supplierTenantId;
    /** 合同金额 */
    private BigDecimal amount;
    /** 合同数量 */
    private Integer quantity;
    /** 币种 */
    private String currency;
    /** 交货条款 */
    private String deliveryTerms;
    /** 交货地点 */
    private String deliveryPlace;
    /** 约定交货日期 */
    private LocalDate deliveryDate;
    /** 合同状态 */
    private String status;
    /** 创建人用户ID */
    private Long createBy;
    // ---- 供应保障规则（风险扫描用） ----
    /** 生产需求日期 */
    private LocalDate productionNeedDate;
    /** 物料关键程度（high/medium/low） */
    private String materialCriticalLevel;
    /** 库存覆盖天数 */
    private Integer inventoryCoverDays;
    /** 计划发运日期 */
    private LocalDate plannedShipDate;
    /** 允许延期天数 */
    private Integer allowedDelayDays;
    /** 是否允许部分交付（0/1） */
    private Integer partialDeliveryAllowed;
    /** 异常通知时限（小时） */
    private Integer exceptionNoticeHours;
    // ---- 违约金条款（交付域验收时计算违约金用） ----
    /** 每日违约金率（万分之五=0.0005，无则不算） */
    private BigDecimal penaltyDailyRate;
    /** 违约金上限比例（10%=0.10） */
    private BigDecimal penaltyMaxRate;
}
