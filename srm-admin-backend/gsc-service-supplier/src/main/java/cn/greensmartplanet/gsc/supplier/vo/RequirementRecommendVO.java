package cn.greensmartplanet.gsc.supplier.vo;

import cn.greensmartplanet.gsc.api.trade.dto.RequirementMatchFieldsDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 找需求智能推荐行 — 广场需求字段 + 匹配分。
 */
@Data
@Schema(description = "需求推荐")
public class RequirementRecommendVO {

    private Long id;
    private Long tenantId;
    private String title;
    private String category;
    private String quantity;
    private String unit;
    private String region;
    private String certRequire;
    private LocalDate deadline;
    private String budget;
    private String description;
    private String status;
    private String buyerCompanyName;
    private LocalDateTime createTime;
    private Integer connectionCount;
    private Integer targetSupplierCount;
    private String myConnectionStatus;
    private String myRejectReason;
    @Schema(description = "匹配分")
    private BigDecimal matchScore;

    public static RequirementRecommendVO fromFields(RequirementMatchFieldsDTO src) {
        RequirementRecommendVO vo = new RequirementRecommendVO();
        BeanUtils.copyProperties(src, vo);
        return vo;
    }
}
