package cn.greensmartplanet.gsc.delivery.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 交付单实体
 * <p>
 * 合同签署（signed）后自动生成，承接采购履约环节：发货 → 收货 → 验收。
 * 一行即一个批次：合同签署时生成第 1 批（数量/金额从合同带出），
 * 发货数量少于本批应发时自动拆出下一批，同合同的多批共享 contract_id、以 batch_no 区分。
 * 状态机见 {@link cn.greensmartplanet.gsc.delivery.enums.DeliveryStatus}。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("delivery")
public class Delivery {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 交付单号 */
    private String deliveryNo;

    /** 关联合同ID */
    private Long contractId;

    /** 关联询盘ID（冗余，列表展示） */
    private Long inquiryId;

    /** 批次号，同一合同下从 1 递增，分批发货时自动生成下一批 */
    private Integer batchNo;

    /** 采购方租户ID */
    private Long buyerTenantId;

    /** 供应方租户ID */
    private Long supplierTenantId;

    /** 本批应发数量（首批取合同数量，拆批后收窄为本批实发） */
    private Integer quantity;

    /** 本批金额（按单价从合同金额分摊） */
    private BigDecimal amount;

    /** 币种，默认CNY */
    private String currency;

    /** 交付条款（FOB/CIF等） */
    private String deliveryTerms;

    /** 交货地点 */
    private String deliveryPlace;

    /** 交付日期 */
    private LocalDate deliveryDate;

    /** 状态：pending_shipment/shipped/received/accepted/returned */
    private String status;

    /** 物流公司 */
    private String shipCompany;

    /** 运单号 */
    private String shipTrackingNo;

    /** 发货数量 */
    private Integer shipQty;

    /** 发货日期 */
    private LocalDate shipDate;

    /** 发货凭证 URL（url|原始文件名） */
    private String shipAttachmentKey;

    /** 发货时间 */
    private LocalDateTime shipTime;

    /** 收货时间 */
    private LocalDateTime receiveTime;

    /** 实收数量 */
    private Integer receiveQty;

    /** 收货差异说明 */
    private String receiveDiffRemark;

    /** 到货照片 URL（url|原始文件名） */
    private String receiveAttachmentKey;

    /** 验收结果：accepted/returned */
    private String acceptResult;

    /** 验收时间 */
    private LocalDateTime acceptTime;

    /** 验收备注 */
    private String acceptRemark;

    /** 残次数量（验收部分残次时原批记录，合格 = 实收 - 残次） */
    private Integer defectiveQty;

    /** 补发来源交付单ID（补发批指向原批，普通批为 NULL） */
    private Long reshipFrom;

    /** 退回原因（验收不合格） */
    private String returnReason;

    /** 已确认承诺交期（供应商提交、采购方确认后写回；无则 null，考核基准回退合同交期） */
    private LocalDate confirmedDeliveryDate;

    /** 违约金延迟天数（验收时计算落库，≤0 不罚） */
    private Integer penaltyDays;

    /** 违约金金额（验收时计算落库） */
    private BigDecimal penaltyAmount;

    /** 违约金计算时间 */
    private LocalDateTime penaltyCalculatedAt;

    /** 退回凭证 URL（url|原始文件名） */
    private String returnAttachmentKey;

    /** 退货物流公司（验收退回） */
    private String returnShipCompany;

    /** 退货单号（验收退回） */
    private String returnShipTrackingNo;

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
