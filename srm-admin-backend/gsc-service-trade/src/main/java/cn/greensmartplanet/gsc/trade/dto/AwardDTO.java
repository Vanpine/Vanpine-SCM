package cn.greensmartplanet.gsc.trade.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 授标请求 DTO — 包装授标列表
 *
 * @author gsc
 */
@Data
public class AwardDTO {

    /** 授标明细列表 */
    @NotEmpty(message = "至少选择一个中标供应商")
    @Valid
    private List<AwardItemDTO> items;
}
