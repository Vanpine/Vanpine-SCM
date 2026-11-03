package cn.greensmartplanet.gsc.delivery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 验收不合格请求 DTO（采购商判定不合格，进入待供应商确认）
 *
 * @author gsc
 */
@Data
@Schema(description = "验收不合格")
public class RejectDeliveryDTO {

    @NotBlank(message = "退回原因不能为空")
    @Schema(description = "退回原因")
    private String reason;

    @NotBlank(message = "请上传退回凭证")
    @Schema(description = "退回凭证 URL（url|原始文件名）")
    private String returnAttachmentKey;
}
