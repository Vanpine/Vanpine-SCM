package cn.greensmartplanet.gsc.delivery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 发货请求 DTO
 *
 * @author gsc
 */
@Data
@Schema(description = "交付发货")
public class ShipDeliveryDTO {

    @NotBlank(message = "物流公司不能为空")
    @Schema(description = "物流公司")
    private String shipCompany;

    @NotBlank(message = "运单号不能为空")
    @Schema(description = "运单号")
    private String shipTrackingNo;

    @NotNull(message = "发货数量不能为空")
    @Min(value = 1, message = "发货数量至少为 1")
    @Schema(description = "发货数量")
    private Integer shipQty;

    @NotNull(message = "发货日期不能为空")
    @Schema(description = "发货日期")
    private LocalDate shipDate;

    @NotBlank(message = "请上传发货凭证")
    @Schema(description = "发货凭证 URL（url|原始文件名）")
    private String shipAttachmentKey;
}
