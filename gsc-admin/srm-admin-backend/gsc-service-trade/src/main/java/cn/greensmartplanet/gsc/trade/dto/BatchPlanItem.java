package cn.greensmartplanet.gsc.trade.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 合同分批计划项（创建/编辑请求与详情回填共用）
 *
 * @author gsc
 */
@Data
@Schema(description = "合同分批计划项")
public class BatchPlanItem {

    @NotNull(message = "本批数量不能为空")
    @Schema(description = "本批数量")
    private Integer quantity;

    @Schema(description = "本批交货日期")
    private LocalDate deliveryDate;

    @Schema(description = "备注")
    private String remark;
}
