package cn.greensmartplanet.gsc.delivery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 收货请求 DTO
 *
 * @author gsc
 */
@Data
@Schema(description = "交付收货")
public class ReceiveDeliveryDTO {

    @NotNull(message = "实收数量不能为空")
    @Min(value = 0, message = "实收数量不能为负数")
    @Schema(description = "实收数量")
    private Integer receiveQty;

    @Schema(description = "差异说明（实收与发货数量不一致时必填）")
    private String receiveDiffRemark;

    @NotBlank(message = "请上传到货照片")
    @Schema(description = "到货照片 URL（url|原始文件名）")
    private String receiveAttachmentKey;
}
