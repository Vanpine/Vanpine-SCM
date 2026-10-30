package cn.greensmartplanet.gsc.delivery.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交付补救工单实体
 * <p>
 * 记录"决定采取什么补救方案，以及有没有执行完成"。由用户确认系统推荐方案后生成，
 * 含责任人/截止时间/状态/回执，是关闭风险的必要证据来源（无回执不可关闭风险）。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("delivery_recovery_action")
public class DeliveryRecoveryAction {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联风险ID */
    private Long riskId;

    /** 动作类型：air_shipment/alternative_supplier/substitution/stock_transfer/other */
    private String actionType;

    /** 方案名称 */
    private String actionName;

    /** 方案说明（系统推荐文案） */
    private String recommendation;

    /** 预计恢复时间 */
    private LocalDateTime expectedEta;

    /** 预计成本 */
    private BigDecimal expectedCost;

    /** 可保障数量 */
    private Integer expectedRecoveryQty;

    /** 状态：pending/executing/completed/failed */
    private String status;

    /** 责任人用户ID */
    private Long ownerUserId;

    /** 截止时间 */
    private LocalDateTime deadline;

    /** 执行备注/回执 */
    private String executionRemark;

    /** 完成时间 */
    private LocalDateTime completedTime;

    /** 结果说明（是否解除风险） */
    private String result;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;
}
