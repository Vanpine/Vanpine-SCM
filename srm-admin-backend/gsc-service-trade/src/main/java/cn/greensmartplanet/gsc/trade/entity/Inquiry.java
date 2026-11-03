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
 * 询盘实体
 * <p>
 * 采购方向受邀供应商发出的询价请求。
 * 状态机：open → quoting → awarded / closed / expired / cancelled
 * open / quoting 过截止 → expired；延时后按有无报价恢复 open / quoting
 * 授标规则：分配量凑满需求量才 awarded；未满保持 quoting；采购方可主动完成授标。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("inquiry")
public class Inquiry {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 询盘编号 RFQ20260803001 */
    private String inquiryNo;

    /** 关联采购需求ID（可空） */
    private Long requirementId;

    /** 关联对接记录ID（可空） */
    private Long connectionId;

    /** 采购方租户ID */
    private Long buyerTenantId;

    /** 询盘标题 */
    private String title;

    /** 币种（报价锁定同币种） */
    private String currency;

    /** 采购品类 */
    private String category;

    /** 采购数量 */
    private BigDecimal quantity;

    /** 单位 */
    private String unit;

    /** 交货地点 */
    private String deliveryPlace;

    /** 期望交付日期 */
    private LocalDate deliveryDate;

    /** 报价截止时间 */
    private LocalDateTime quoteDeadline;

    /** 付款条件 */
    private String paymentTerms;

    /** 规格条款 */
    private String specText;

    /** 附件URL */
    private String attachmentKey;

    /** 状态：open/quoting/awarded/closed/expired/cancelled */
    private String status;

    /** 中标报价ID */
    private Long winningQuoteId;

    /** 中标时间 */
    private LocalDateTime awardedAt;

    /** 中标操作人 */
    private Long awardedBy;

    /** 中标理由 */
    private String awardReason;

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
