package cn.greensmartplanet.gsc.delivery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 录入物流事件请求 DTO
 * <p>
 * 供应商/采购方在平台内人工填报物流节点，事件落库后触发风险识别。
 *
 * @author gsc
 */
@Data
@Schema(description = "录入物流事件")
public class CreateLogisticsEventDTO {

    @NotNull(message = "交付单ID不能为空")
    @Schema(description = "关联交付单ID")
    private Long deliveryId;

    @NotBlank(message = "事件类型不能为空")
    @Schema(description = "标准化事件类型：planned_ship/actual_ship/in_transit/arrived_transit/customs_hold/customs_clear/delay/arrived_delivered/exception")
    private String eventType;

    @NotNull(message = "事件时间不能为空")
    @Schema(description = "事件发生时间")
    private LocalDateTime eventTime;

    @Schema(description = "预计到货时间（ETA）")
    private LocalDateTime estimatedArrivalTime;

    @Schema(description = "地点/节点")
    private String location;

    @Schema(description = "备注（异常/延误原因）")
    private String remark;
}
