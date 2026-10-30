package cn.greensmartplanet.gsc.trade.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 创建合同请求 DTO（一期最小化）
 *
 * @author gsc
 */
@Data
@Schema(description = "创建合同")
public class CreateContractDTO {

    @NotNull(message = "询盘ID不能为空")
    @Schema(description = "关联询盘ID")
    private Long inquiryId;

    @NotBlank(message = "合同标题不能为空")
    @Schema(description = "合同标题")
    private String title;

    @Schema(description = "合同金额")
    private BigDecimal amount;

    @Schema(description = "币种", example = "CNY")
    private String currency;

    @Schema(description = "签署日期（即合同生效日期，签署即生效）")
    private LocalDate signDate;

    @NotNull(message = "合同截止日期不能为空")
    @Schema(description = "截止日期")
    private LocalDate validUntil;

    @Schema(description = "交货地点")
    private String deliveryPlace;

    @Schema(description = "交付日期")
    private LocalDate deliveryDate;

    @Schema(description = "付款条件")
    private String paymentTerms;

    @Schema(description = "交付条款")
    private String deliveryTerms;

    @Schema(description = "生产需求日期")
    private LocalDate productionNeedDate;

    @Schema(description = "物料关键等级：low/medium/high")
    private String materialCriticalLevel;

    @Schema(description = "库存覆盖天数")
    private Integer inventoryCoverDays;

    @Schema(description = "约定最晚发运日期")
    private LocalDate plannedShipDate;

    @Schema(description = "合同允许延期天数")
    private Integer allowedDelayDays;

    @Schema(description = "是否允许部分交付：0否 1是")
    private Integer partialDeliveryAllowed;

    @Schema(description = "是否允许替代物料：0否 1是")
    private Integer substitutionAllowed;

    @Schema(description = "是否允许加急运输：0否 1是")
    private Integer expediteAllowed;

    @Schema(description = "供应商需提前几天反馈发运计划")
    private Integer deliveryNoticeDays;

    @Schema(description = "发生异常后最晚通知小时数")
    private Integer exceptionNoticeHours;

    @Schema(description = "每日违约金率（万分之五=0.0005，选填，无则不计算违约金）")
    private BigDecimal penaltyDailyRate;

    @Schema(description = "违约金上限比例（10%=0.10，选填）")
    private BigDecimal penaltyMaxRate;

    @Schema(description = "合同附件URL")
    private String attachmentKey;

    @Schema(description = "中标报价ID，手动指定；不传则从询盘授标信息自动推导")
    private Long winningQuoteId;

    @Schema(description = "分批计划（可空，空则默认一批全量）")
    private List<BatchPlanItem> batchPlans;
}
