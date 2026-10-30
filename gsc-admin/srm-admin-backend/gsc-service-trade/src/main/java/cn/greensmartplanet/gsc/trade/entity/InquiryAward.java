package cn.greensmartplanet.gsc.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 授标明细实体
 * <p>
 * 支持分量授标：一个询盘可拆分量授给多家供应商。
 * 每个授标明细记录一个供应商中标的份额。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("inquiry_award")
public class InquiryAward {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 询盘ID */
    private Long inquiryId;

    /** 中标报价ID */
    private Long quoteId;

    /** 中标供应商租户ID */
    private Long supplierTenantId;

    /** 授标数量 */
    private Integer awardedQuantity;

    /** 授标人ID */
    private Long awardedBy;

    /** 授标时间 */
    private LocalDateTime awardedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
