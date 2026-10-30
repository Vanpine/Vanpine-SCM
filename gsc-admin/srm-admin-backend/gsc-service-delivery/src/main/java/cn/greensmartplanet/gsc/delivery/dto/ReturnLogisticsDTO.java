package cn.greensmartplanet.gsc.delivery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 退货物流请求 DTO（供应商确认退回后，采购商填写退货物流）
 *
 * @author gsc
 */
@Data
@Schema(description = "退货物流")
public class ReturnLogisticsDTO {

    @NotBlank(message = "请填写退货物流公司")
    @Schema(description = "退货物流公司")
    private String returnShipCompany;

    @NotBlank(message = "请填写退货单号")
    @Schema(description = "退货单号")
    private String returnShipTrackingNo;
}
