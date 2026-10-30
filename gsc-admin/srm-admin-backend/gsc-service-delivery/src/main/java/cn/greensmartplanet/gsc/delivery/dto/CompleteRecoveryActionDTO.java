package cn.greensmartplanet.gsc.delivery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 完成补救工单请求 DTO
 *
 * @author gsc
 */
@Data
@Schema(description = "完成补救工单")
public class CompleteRecoveryActionDTO {

    @NotBlank(message = "执行回执不能为空")
    @Schema(description = "执行备注/回执")
    private String executionRemark;

    @NotBlank(message = "结果说明不能为空")
    @Schema(description = "结果说明（是否解除风险）")
    private String result;
}
