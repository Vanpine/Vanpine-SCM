package cn.greensmartplanet.gsc.delivery.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 交付物流事件实体（append-only）
 * <p>
 * 记录交付单的物流节点（计划发运/实际发运/运输中/清关/延误/到达等），
 * 属外部 TMS 或人工填报的物流事实，与平台内部动作日志 delivery_trace 分开存，
 * 避免"供应商操作日志"和"物流节点"混在一起无法区分数据来源。
 * source=manual 为人工填报（一期），source=tms 为二期外部接入。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("delivery_logistics_event")
public class DeliveryLogisticsEvent {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联交付单ID */
    private Long deliveryId;

    /** 标准化事件类型：planned_ship/actual_ship/in_transit/arrived_transit/customs_start/customs_hold/customs_clear/delay/arrived_delivered/exception */
    private String eventType;

    /** 事件发生时间 */
    private LocalDateTime eventTime;

    /** 预计到货时间（ETA，可在 delay/清关等节点更新，风险识别取最新一条带该值的事件） */
    private LocalDateTime estimatedArrivalTime;

    /** 地点/节点（可空） */
    private String location;

    /** 备注（异常/延误原因） */
    private String remark;

    /** 数据来源：manual（一期固定）/ tms（二期） */
    private String source;

    /** 外部事件ID（二期幂等用，一期可空） */
    private String externalEventId;

    /** 录入方租户ID */
    private Long operatorTenantId;

    /** 录入用户ID */
    private Long operatorUserId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
