package cn.greensmartplanet.gsc.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 合同实体
 * <p>
 * 询盘中标签约后创建，支持完整状态机：
 * draft(草稿) → pending_supplier(待供应商确认) → pending_confirm(待确认盖章件) → signed(已签)
 * → completed(已完成)/terminated(已终止)
 * 供应商拒绝时 pending_supplier 回退 draft，可多轮往返；终止仅 signed 可触发。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("contract")
public class Contract {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 合同编号 */
    private String contractNo;

    /** 关联询盘ID */
    private Long inquiryId;

    /** 中标报价ID（一期可为空） */
    private Long winningQuoteId;

    /** 采购方租户ID */
    private Long buyerTenantId;

    /** 供应方租户ID */
    private Long supplierTenantId;

    /** 合同标题 */
    private String title;

    /** 合同金额 */
    private BigDecimal amount;

    /** 履约数量（从授标 awardedQuantity 带出） */
    private Integer quantity;

    /** 币种，默认CNY */
    private String currency;

    /** 签署日期（即合同生效日期，签署即生效） */
    private LocalDate signDate;

    /** 合同截止日期 */
    private LocalDate validUntil;

    /** 交货地点 */
    private String deliveryPlace;

    /** 交付日期 */
    private LocalDate deliveryDate;

    /** 付款条件 */
    private String paymentTerms;

    /** 交付条款（FOB/CIF等） */
    private String deliveryTerms;

    /** 生产需求日期（断供判断：预计到货晚于该日期且库存不足覆盖则升级高风险） */
    private LocalDate productionNeedDate;

    /** 物料关键等级：low/medium/high（断供影响程度因子） */
    private String materialCriticalLevel;

    /** 库存覆盖天数（断供判断：库存能否覆盖延期天数） */
    private Integer inventoryCoverDays;

    /** 约定最晚发运日期（未按计划发运风险的基准日） */
    private LocalDate plannedShipDate;

    /** 合同允许延期天数（eta 预警触发线 = 交期 + 允许延期） */
    private Integer allowedDelayDays;

    /** 是否允许部分交付：0否 1是（否时部分发货视为数量风险） */
    private Integer partialDeliveryAllowed;

    /** 是否允许替代物料：0否 1是（补救方案"替代物料"开关） */
    private Integer substitutionAllowed;

    /** 是否允许加急运输：0否 1是（补救方案"改空运/加急"开关） */
    private Integer expediteAllowed;

    /** 供应商需提前几天反馈发运计划 */
    private Integer deliveryNoticeDays;

    /** 发生异常后最晚通知小时数（物流停滞阈值） */
    private Integer exceptionNoticeHours;

    /** 每日违约金率（万分之五=0.0005，无则不算违约金） */
    private BigDecimal penaltyDailyRate;

    /** 违约金上限比例（10%=0.10） */
    private BigDecimal penaltyMaxRate;

    /** 状态：draft|pending_supplier|pending_confirm|signed|completed|terminated */
    private String status;

    /** 终止原因 */
    private String terminateReason;

    /** 终止类型：mutual(协商)|supplier_breach(供应商违约)|buyer_breach(采购方违约)|force_majeure(不可抗力) */
    private String terminateType;

    /** 合同附件URL */
    private String attachmentKey;

    /** 供应商是否已确认条款：0未确认 1已确认 */
    private Integer supplierConfirmed;

    /** 供应商确认条款时间 */
    private LocalDateTime supplierConfirmedAt;

    /** 供应商拒绝理由（pending_supplier 退回 draft 时填写） */
    private String declineReason;

    /** 盖章件URL（历史单列，迁移后不再写入） */
    private String signAttachmentUrl;

    /** 盖章件 OSS key（存对象路径；返回 VO 时现签为 signAttachmentUrl） */
    private String signAttachmentKey;

    /** 盖章件上传方租户ID（历史字段，迁移后不再用于自确认校验） */
    private Long signUploadedByTenantId;

    /** 采购方盖章件URL（历史字段，迁移后不再写入，由 buyerSignAttachmentKey 现签填充） */
    private String buyerSignAttachmentUrl;

    /** 采购方盖章件 OSS key */
    private String buyerSignAttachmentKey;

    /** 供应方盖章件URL（历史字段，迁移后不再写入，由 supplierSignAttachmentKey 现签填充） */
    private String supplierSignAttachmentUrl;

    /** 供应方盖章件 OSS key */
    private String supplierSignAttachmentKey;

    /** 采购方是否已确认对方盖章件：0未确认 1已确认 */
    private Integer buyerSignConfirm;

    /** 供应方是否已确认对方盖章件：0未确认 1已确认 */
    private Integer supplierSignConfirm;

    /** 父合同ID（补充协议用，本期仅补字段映射） */
    private Long parentContractId;

    /** 采购方撤回时间（draft 状态非空表示采购方撤回待修改） */
    private LocalDateTime withdrawnAt;

    /** 创建人用户ID */
    private Long createBy;

    /** 更新人用户ID */
    private Long updateBy;

    // ---- 审计 ----
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;
}
