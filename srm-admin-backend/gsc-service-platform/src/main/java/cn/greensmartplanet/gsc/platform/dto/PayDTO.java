package cn.greensmartplanet.gsc.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 创建尽调支付单请求。金额由服务端价目表决定，客户端只传套餐和币种。
 *
 * <p>⚠️ 迁移期桥接（微服务拆分）：本文件归属供应商域（阶段 7 gsc-service-supplier），platform 内为过渡副本，supplier 上线后随桥接收敛删除/改 Feign。</p>
 * @author gsc
 */
@Data
@Schema(description = "创建尽调支付单")
public class PayDTO {

    @NotBlank(message = "套餐类型不能为空")
    @Schema(description = "套餐类型（basic/standard/premium）")
    private String packageType;

    @NotBlank(message = "支付币种不能为空")
    @Pattern(regexp = "^[A-Z]{3}$", message = "支付币种需为 ISO 4217 三位代码，如 USD")
    @Schema(description = "支付币种（平台已开通的 ISO 4217，如 USD/EUR/CNY）")
    private String paymentCurrency;
}
