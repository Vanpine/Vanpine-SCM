package cn.greensmartplanet.gsc.trade.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

/**
 * 发布采购需求请求 DTO
 *
 * @author gsc
 */
@Data
@Schema(description = "发布采购需求")
public class PublishRequirementDTO {

    @NotBlank(message = "需求标题不能为空")
    @Schema(description = "需求标题", example = "2025年度钢材采购需求")
    private String title;

    @NotBlank(message = "采购品类不能为空")
    @Schema(description = "采购品类（逗号分隔，如：钢材,铝材,铜材）", example = "钢材,铝材")
    private String category;

    @NotBlank(message = "采购数量不能为空")
    @Schema(description = "采购数量描述", example = "5000")
    private String quantity;

    @NotBlank(message = "单位不能为空")
    @Schema(description = "单位", example = "吨")
    private String unit;

    @Schema(description = "期望供应商地域（空=不限）", example = "广东省")
    private String region;

    @Schema(description = "资质要求（空=不限，用于AI语义解析）", example = "ISO9001,CE认证")
    private String certRequire;

    @Schema(description = "截止日期", example = "2026-12-31")
    private LocalDate deadline;

    @Schema(description = "预算描述", example = "500万-800万人民币")
    private String budget;

    @Schema(description = "需求详细描述（AI语义分析核心字段）", example = "需要供应商具备钢材深加工能力，月产能不低于2000吨...")
    private String description;

    @Schema(description = "目标供应商数量（null=不限）", example = "3")
    private Integer targetSupplierCount;

    @Schema(description = "是否公开到需求广场")
    private Boolean isPublic;

    @Schema(description = "联系人")
    private String contactName;

    @Schema(description = "联系电话")
    private String contactPhone;

    @Schema(description = "联系邮箱")
    private String contactEmail;
}
