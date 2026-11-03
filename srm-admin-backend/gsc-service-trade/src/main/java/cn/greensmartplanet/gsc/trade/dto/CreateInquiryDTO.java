package cn.greensmartplanet.gsc.trade.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建询盘请求 DTO
 *
 * @author gsc
 */
@Data
@Schema(description = "创建询盘")
public class CreateInquiryDTO {

    @NotNull(message = "请选择关联采购需求")
    @Schema(description = "关联采购需求ID")
    private Long requirementId;

    @Schema(description = "关联对接记录ID")
    private Long connectionId;

    @NotBlank(message = "询盘标题不能为空")
    @Schema(description = "询盘标题")
    private String title;

    @Schema(description = "币种", example = "CNY")
    private String currency;

    @Schema(description = "采购品类")
    private String category;

    @Schema(description = "采购数量")
    private BigDecimal quantity;

    @Schema(description = "单位")
    private String unit;

    @Schema(description = "交货地点")
    private String deliveryPlace;

    @Schema(description = "期望交付日期")
    private LocalDate deliveryDate;

    @Schema(description = "报价截止时间")
    private LocalDateTime quoteDeadline;

    @Schema(description = "付款条件")
    private String paymentTerms;

    @Schema(description = "规格条款")
    private String specText;

    @Schema(description = "附件URL")
    private String attachmentKey;

    @NotEmpty(message = "受邀供应商不能为空")
    @Schema(description = "受邀供应商租户ID列表")
    private List<Long> supplierTenantIds;
}
