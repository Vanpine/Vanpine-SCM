package cn.greensmartplanet.gsc.delivery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 运单更正请求 DTO
 *
 * @author gsc
 */
@Data
@Schema(description = "更正运单")
public class CorrectTrackingDTO {

    @NotBlank(message = "物流公司不能为空")
    @Schema(description = "物流公司")
    private String shipCompany;

    @NotBlank(message = "运单号不能为空")
    @Schema(description = "运单号")
    private String shipTrackingNo;
}
