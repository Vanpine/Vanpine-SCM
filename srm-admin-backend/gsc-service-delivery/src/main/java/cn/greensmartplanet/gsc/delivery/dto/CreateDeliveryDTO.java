package cn.greensmartplanet.gsc.delivery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 手动创建交付单请求 DTO
 * <p>
 * 供应方主动分批用：从自己已签署的合同里挑一份，指定本批数量建单。
 * 数量上限由服务端按「合同数量 - 已建各批数量之和」校验。
 *
 * @author gsc
 */
@Data
@Schema(description = "手动创建交付单")
public class CreateDeliveryDTO {

    @NotNull(message = "请选择合同")
    @Schema(description = "关联合同ID")
    private Long contractId;

    @NotNull(message = "本批数量不能为空")
    @Min(value = 1, message = "本批数量至少为 1")
    @Schema(description = "本批交付数量")
    private Integer quantity;
}
