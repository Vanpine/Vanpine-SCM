package cn.greensmartplanet.gsc.supplier.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 发起对接意向请求 DTO
 *
 * @author gsc
 */
@Data
@Schema(description = "发起对接意向")
public class CreateConnectionDTO {

    @NotNull(message = "需求ID不能为空")
    @Schema(description = "需求ID")
    private Long requirementId;

    @NotNull(message = "供应商租户ID不能为空")
    @Schema(description = "供应商租户ID")
    private Long supplierTenantId;

    @Schema(description = "买方留言", example = "我司对贵司的产品很感兴趣，希望进一步沟通")
    private String message;
}
