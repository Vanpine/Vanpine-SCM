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
 * 交付风险实体
 * <p>
 * 记录"为什么有风险，以及风险会造成什么影响"。由定时扫描根据合同交付规则字段生成，
 * 与交付单状态机分离：交付状态管"履约到哪一步"，风险状态管"会不会出问题、该怎么补救"。
 * promised_delivery_date/production_need_date/impact_qty/impact_amount 均为触发时快照，
 * 保证风险证据可追溯，不随后续合同/交付变更漂移。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("delivery_risk")
public class DeliveryRisk {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联交付单ID */
    private Long deliveryId;

    /** 关联合同ID */
    private Long contractId;

    /** 风险类型：late_shipment/logistics_stagnation/eta_delay/clearance_hold/quantity_shortage/weather_disruption/other */
    private String riskType;

    /** 风险等级：low/medium/high */
    private String riskLevel;

    /** 风险状态：active/warned/high/disposing/mitigated/closed */
    private String riskStatus;

    /** 预计到货时间（来自物流事件 ETA） */
    private LocalDateTime estimatedArrivalTime;

    /** 合同约定交期（快照） */
    private LocalDate promisedDeliveryDate;

    /** 生产需求日期（快照，断供判断用） */
    private LocalDate productionNeedDate;

    /** 影响数量（快照） */
    private Integer impactQty;

    /** 影响金额（快照） */
    private BigDecimal impactAmount;

    /** 风险分（0-100，透明公式计算） */
    private Integer riskScore;

    /** 触发证据（JSON/文本，详情页展示"为什么"） */
    private String evidence;

    /** 首次发现时间 */
    private LocalDateTime firstDetectedTime;

    /** 处置截止时间 */
    private LocalDateTime dueTime;

    /** 责任人用户ID */
    private Long ownerUserId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;
}
