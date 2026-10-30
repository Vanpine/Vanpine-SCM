package cn.greensmartplanet.gsc.delivery.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 交付风险因子实体
 * <p>
 * 记录风险类型的演变轨迹（时间序列）：同一时刻一个交付单只有一个 active 因子（= 主表当前类型），
 * 类型切换时旧因子 closed、新因子 active，保证「一单一主表 + 因子历史」可追溯。
 * 主表 delivery_risk 收敛为「当前风险」，因子表保留每次类型变化的时间/等级/证据快照。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("delivery_risk_factor")
public class DeliveryRiskFactor {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联风险主表ID
     */
    private Long riskId;

    /**
     * 冗余交付单ID（便于按单查因子）
     */
    private Long deliveryId;

    /**
     *
     * 因子类型：late_shipment(未按计划发运)/
     * logistics_stagnation(物流停滞)/
     * eta_delay(预计晚于交期)/
     * clearance_hold(清关阻塞)/
     * quantity_shortage(数量不足)/
     * other(其他异常)
     */
    private String riskType;

    /**
     * 该因子当时等级：
     * low(低)
     * /medium(中)
     * /high(高)
     */
    private String riskLevel;

    /**
     * 因子状态：active(活跃)/closed(已解除)
     */
    private String factorStatus;

    /**
     * 该因子当时 ETA
     */
    private LocalDateTime estimatedArrivalTime;

    /**
     * 该因子当时分数
     */
    private Integer riskScore;

    /**
     * 该因子触发证据
     */
    private String evidence;

    /**
     * 该因子首次触发时间
     */
    private LocalDateTime firstDetectedTime;

    /**
     * 结束时间（active 为 NULL）
     */
    private LocalDateTime closedTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;
}
