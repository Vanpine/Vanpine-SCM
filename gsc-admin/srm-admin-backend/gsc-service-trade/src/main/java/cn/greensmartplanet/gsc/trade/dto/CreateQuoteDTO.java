package cn.greensmartplanet.gsc.trade.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 提交报价请求 DTO
 *
 * @author gsc
 */
@Data
@Schema(description = "提交报价")
public class CreateQuoteDTO {

    @NotNull(message = "询盘ID不能为空")
    @Schema(description = "询盘ID")
    private Long inquiryId;

    @Schema(description = "单价")
    private BigDecimal unitPrice;

    @NotNull(message = "报价总价不能为空")
    @DecimalMin(value = "0", message = "总价不能为负数")
    @Schema(description = "总价")
    private BigDecimal totalPrice;

    @Schema(description = "供应数量（供应商自报能供多少）")
    private Integer supplyQuantity;

    @Schema(description = "币种", example = "CNY")
    private String currency;

    @Schema(description = "交期天数")
    private Integer deliveryDays;

    @Schema(description = "交货条款")
    private String deliveryTerms;

    @Schema(description = "报价有效期")
    private LocalDateTime validUntil;

    @Schema(description = "报价说明")
    private String remark;

    @Schema(description = "附件URL")
    private String attachmentKey;
}
