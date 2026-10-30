package cn.greensmartplanet.gsc.delivery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 部分残次补发请求 DTO（采购商验收发现部分残次，合格部分验收通过、残次部分发起补发）
 *
 * @author gsc
 */
@Data
@Schema(description = "部分残次补发")
public class PartialRejectDTO {

    @NotNull(message = "残次数量不能为空")
    @Schema(description = "残次数量（需大于0且小于实收数量）")
    private Integer defectiveQty;

    @NotBlank(message = "残次原因不能为空")
    @Schema(description = "残次原因")
    private String reason;

    @NotBlank(message = "请上传残次凭证")
    @Schema(description = "残次凭证 URL（url|原始文件名）")
    private String returnAttachmentKey;
}
