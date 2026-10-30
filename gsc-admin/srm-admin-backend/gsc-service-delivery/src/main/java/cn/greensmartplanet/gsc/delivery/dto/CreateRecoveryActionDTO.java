package cn.greensmartplanet.gsc.delivery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 创建补救工单请求 DTO
 *
 * @author gsc
 */
@Data
@Schema(description = "创建补救工单")
public class CreateRecoveryActionDTO {

    @NotNull(message = "风险ID不能为空")
    @Schema(description = "关联风险ID")
    private Long riskId;

    @NotBlank(message = "动作类型不能为空")
    @Schema(description = "动作类型：air_shipment/alternative_supplier/substitution/stock_transfer/other")
    private String actionType;

    @Schema(description = "方案名称")
    private String actionName;

    @Schema(description = "方案说明")
    private String recommendation;

    @Schema(description = "预计恢复时间")
    private LocalDateTime expectedEta;

    @Schema(description = "预计成本")
    private BigDecimal expectedCost;

    @Schema(description = "可保障数量")
    private Integer expectedRecoveryQty;

    @Schema(description = "责任人用户ID")
    private Long ownerUserId;

    @Schema(description = "截止时间")
    private LocalDateTime deadline;
}
