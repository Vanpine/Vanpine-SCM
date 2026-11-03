package cn.greensmartplanet.gsc.trade.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 授标项 DTO — 分量授标时每个供应商的份额
 *
 * @author gsc
 */
@Data
public class AwardItemDTO {

    /** 中标报价ID */
    @NotNull(message = "报价ID不能为空")
    private Long quoteId;

    /** 授标数量（必须大于0；已中标项须与原授标数量一致） */
    @NotNull(message = "授标数量不能为空")
    @Min(value = 1, message = "授标数量必须大于0")
    private Integer awardedQuantity;
}
