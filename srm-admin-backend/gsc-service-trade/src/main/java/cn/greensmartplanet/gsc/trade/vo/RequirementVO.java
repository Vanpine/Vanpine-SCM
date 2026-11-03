package cn.greensmartplanet.gsc.trade.vo;

import cn.greensmartplanet.gsc.trade.entity.ProcurementRequirement;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 采购需求 VO
 *
 * @author gsc
 */
@Data
@Schema(description = "采购需求")
public class RequirementVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "发布方租户ID")
    private Long tenantId;

    @Schema(description = "需求标题")
    private String title;

    @Schema(description = "采购品类")
    private String category;

    @Schema(description = "采购数量描述")
    private String quantity;

    @Schema(description = "单位")
    private String unit;

    @Schema(description = "期望供应商地域")
    private String region;

    @Schema(description = "资质要求")
    private String certRequire;

    @Schema(description = "截止日期")
    private LocalDate deadline;

    @Schema(description = "预算描述")
    private String budget;

    @Schema(description = "需求详细描述")
    private String description;

    @Schema(description = "状态：open/closed/expired")
    private String status;

    @Schema(description = "联系人")
    private String contactName;

    @Schema(description = "联系电话")
    private String contactPhone;

    @Schema(description = "联系邮箱")
    private String contactEmail;

    @Schema(description = "发布方企业名称")
    private String buyerCompanyName;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "对接数量")
    private Integer connectionCount;

    @Schema(description = "目标供应商数量（null=不限）")
    private Integer targetSupplierCount;

    @Schema(description = "是否公开到需求广场")
    private Boolean isPublic;

    @Schema(description = "当前供应商对该需求的对接状态：pending/accepted/rejected，null=未申请")
    private String myConnectionStatus;

    @Schema(description = "拒绝原因（仅 myConnectionStatus=rejected 时返回）")
    private String myRejectReason;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    /**
     * 从 Entity 转换（状态以 DB 为准，过期由查询时 SQL 自动更新）
     */
    public static RequirementVO fromEntity(ProcurementRequirement e) {
        RequirementVO vo = new RequirementVO();
        vo.setId(e.getId());
        vo.setTenantId(e.getTenantId());
        vo.setTitle(e.getTitle());
        vo.setCategory(e.getCategory());
        vo.setQuantity(e.getQuantity());
        vo.setUnit(e.getUnit());
        vo.setRegion(e.getRegion());
        vo.setCertRequire(e.getCertRequire());
        vo.setDeadline(e.getDeadline());
        vo.setBudget(e.getBudget());
        vo.setDescription(e.getDescription());
        vo.setStatus(e.getStatus());
        vo.setTargetSupplierCount(e.getTargetSupplierCount());
        vo.setIsPublic(e.getIsPublic());
        vo.setContactName(e.getContactName());
        vo.setContactPhone(e.getContactPhone());
        vo.setContactEmail(e.getContactEmail());
        vo.setCreateTime(e.getCreateTime());
        vo.setUpdateTime(e.getUpdateTime());
        return vo;
    }
}
