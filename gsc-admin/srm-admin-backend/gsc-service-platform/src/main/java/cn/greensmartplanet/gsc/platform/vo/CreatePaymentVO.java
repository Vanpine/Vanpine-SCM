package cn.greensmartplanet.gsc.platform.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 创建支付单结果 — 前端展示收款码，等待回调改已支付。
 */
@Data
@Builder
@Schema(description = "创建支付单")
public class CreatePaymentVO {

    @Schema(description = "银联/工行收款码 URL")
    private String qrcode;

    @Schema(description = "商户订单号")
    private String outTradeNo;

    @Schema(description = "中台/银行订单号")
    private String orderNo;

    @Schema(description = "应付金额（元）")
    private String amountYuan;

    @Schema(description = "币种")
    private String currency;
}
