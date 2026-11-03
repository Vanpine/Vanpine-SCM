package cn.greensmartplanet.gsc.api.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 交付物流事件契约 DTO — AI 工具 get_delivery_detail 的 logisticsEvents 元素
 * <p>
 * 记录交付单的物流节点（计划发运/实际发运/运输中/清关/延误/到达等）。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDeliveryLogisticsEventDTO {

    /** 标准化事件类型：planned_ship/actual_ship/in_transit/arrived_transit/customs_start/customs_hold/customs_clear/delay/arrived_delivered/exception */
    private String eventType;

    /** 事件发生时间 */
    private LocalDateTime eventTime;

    /** 预计到货时间（ETA） */
    private LocalDateTime estimatedArrivalTime;

    /** 地点/节点 */
    private String location;

    /** 备注（异常/延误原因） */
    private String remark;

    /** 数据来源：manual（一期）/ tms（二期） */
    private String source;
}
