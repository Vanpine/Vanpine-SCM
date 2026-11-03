package cn.greensmartplanet.gsc.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 报价实体
 * <p>
 * 供应商对询盘的报价响应。
 * 状态机：pending → withdrawn（供应商主动撤回）
 * 报价保密：供应商只能看自己的报价，采购方看全部。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("quote")
public class Quote {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 报价编号
     */
    private String quoteNo;

    /**
     * 关联询盘ID
     */
    private Long inquiryId;

    /**
     * 采购方租户ID（冗余，方便买方查询）
     */
    private Long buyerTenantId;

    /**
     * 报价方租户ID
     */
    private Long supplierTenantId;

    /**
     * 单价
     */
    private BigDecimal unitPrice;

    /**
     * 总价
     */
    private BigDecimal totalPrice;

    /**
     * 供应数量（供应商自报能供多少）
     */
    private Integer supplyQuantity;

    /**
     * 币种，默认CNY
     */
    private String currency;

    /**
     * 交期天数
     */
    private Integer deliveryDays;

    /**
     * 交货条款
     */
    private String deliveryTerms;

    /**
     * 报价有效期
     */
    private LocalDateTime validUntil;

    /**
     * 报价说明
     */
    private String remark;

    /**
     * 附件URL
     */
    private String attachmentKey;

    /**
     * 状态：pending/withdrawn
     */
    private String status;

    /**
     * 是否中标
     */
    private Integer isWinning;

    /**
     * 提交时间
     */
    private LocalDateTime submittedAt;

    /**
     * 创建人用户ID
     */
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
