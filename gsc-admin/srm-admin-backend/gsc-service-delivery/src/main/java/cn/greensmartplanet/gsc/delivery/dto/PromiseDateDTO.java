package cn.greensmartplanet.gsc.delivery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 提交承诺日期请求 DTO
 * <p>
 * 供应商提交新承诺交期 + 原因，采购方确认/拒绝。
 *
 * @author gsc
 */
@Data
@Schema(description = "提交承诺日期")
public class PromiseDateDTO {

    @NotNull(message = "承诺交期不能为空")
    @Schema(description = "承诺交期")
    private LocalDate promisedDate;

    @NotBlank(message = "原因不能为空")
    @Schema(description = "原因（如：原材料延迟、产能紧张）")
    private String reason;
}
