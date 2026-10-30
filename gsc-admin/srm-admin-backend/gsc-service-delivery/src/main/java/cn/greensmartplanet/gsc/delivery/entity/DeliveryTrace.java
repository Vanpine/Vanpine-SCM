package cn.greensmartplanet.gsc.delivery.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 交付履约时间线实体
 * <p>
 * 记录交付单每次状态流转（发货/收货/验收/退回），是谁在何时做了什么。
 * 属操作日志（append-only），非商品明细；未来多 SKU 明细走独立的 delivery_item 表。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("delivery_trace")
public class DeliveryTrace {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联交付单ID */
    private Long deliveryId;

    /** 操作类型：create/ship/receive/accept/return/correct_tracking */
    private String action;

    /** 操作描述 */
    private String actionDesc;

    /** 操作方租户ID */
    private Long operatorTenantId;

    /** 操作用户ID */
    private Long operatorUserId;

    /** 备注（退回原因/验收意见） */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
