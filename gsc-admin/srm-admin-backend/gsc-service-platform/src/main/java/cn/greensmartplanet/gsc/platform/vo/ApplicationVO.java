package cn.greensmartplanet.gsc.platform.vo;

import cn.greensmartplanet.gsc.platform.entity.Application;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 入驻申请展示 VO — 包含全部申请字段 + 申请人角色中文映射
 *
 * @author gsc
 */
@Data
@Schema(description = "入驻申请详情")
public class ApplicationVO {

    @Schema(description = "申请ID")
    private Long id;

    @Schema(description = "AI世界护照UUID")
    private String passportUuid;

    @Schema(description = "申请身份（原始值）")
    private String applicantRole;

    @Schema(description = "申请身份（中文）")
    private String applicantRoleLabel;

    @Schema(description = "公司名称")
    private String companyName;

    @Schema(description = "所在国家/地区")
    private String country;

    @Schema(description = "企业注册号")
    private String regNumber;

    @Schema(description = "成立年份")
    private Integer establishedYear;

    @Schema(description = "联系人姓名")
    private String contactName;

    @Schema(description = "职位")
    private String contactTitle;

    @Schema(description = "联系邮箱")
    private String contactEmail;

    @Schema(description = "联系电话")
    private String contactPhone;

    // ---- 采购方字段 ----
    @Schema(description = "采购品类")
    private String buyCategory;

    @Schema(description = "年采购额")
    private String buyVolume;

    @Schema(description = "年采购额（中文）")
    private String buyVolumeLabel;
    @Schema(description = "年采购金额（原币种）")
    private BigDecimal buyVolumeAmount;
    @Schema(description = "年采购币种（ISO 4217）")
    private String buyVolumeCurrency;
    @Schema(description = "财务期间开始日期")
    private LocalDate fiscalPeriodStart;
    @Schema(description = "财务期间结束日期")
    private LocalDate fiscalPeriodEnd;

    @Schema(description = "目标市场")
    private String buyMarket;

    // ---- 供应方字段 ----
    @Schema(description = "生产品类")
    private String supCategory;

    @Schema(description = "年产能")
    private String supCapacity;

    @Schema(description = "资质认证")
    private String supCerts;

    @Schema(description = "出口经验")
    private String supExport;

    @Schema(description = "出口经验（中文）")
    private String supExportLabel;

    // ---- 审核字段 ----
    @Schema(description = "状态")
    private String status;

    @Schema(description = "审核意见")
    private String reviewComment;

    @Schema(description = "审核人ID")
    private Long reviewedBy;

    @Schema(description = "审核时间")
    private LocalDateTime reviewedAt;

    @Schema(description = "关联租户ID")
    private Long tenantId;

    @Schema(description = "尽调数据（1:1关联，可能为null）")
    private DueDiligenceVO dueDiligence;

    @Schema(description = "制裁筛查状态：clean/warning/pending（未筛查），列表页黄牌标识用")
    private String sanctionStatus;

    @Schema(description = "制裁命中条数")
    private Integer sanctionHitCount;

    @Schema(description = "提交时间")
    private LocalDateTime createTime;

    // ---- 申请人角色中文映射 ----
    private static final java.util.Map<String, String> ROLE_MAP = java.util.Map.of(
            "buyer", "采购商",
            "supplier", "供应商",
            "buyer,supplier", "采购商+供应商"
    );

    // ---- 年采购额中文映射 ----
    private static final java.util.Map<String, String> BUY_VOLUME_MAP = java.util.Map.of(
            "1", "100万以下",
            "2", "100-500万",
            "3", "500-2000万",
            "4", "2000万-1亿",
            "5", "1亿以上"
    );

    // ---- 出口经验中文映射 ----
    private static final java.util.Map<String, String> SUP_EXPORT_MAP = java.util.Map.of(
            "0", "无",
            "1", "3年以下",
            "2", "3-5年",
            "3", "5-10年",
            "4", "10年以上"
    );

    /**
     * 从 Entity 构建 VO，自动填充中文映射
     */
    public static ApplicationVO fromEntity(Application a) {
        ApplicationVO vo = new ApplicationVO();
        vo.setId(a.getId());
        vo.setPassportUuid(a.getPassportUuid());
        vo.setApplicantRole(a.getApplicantRole());
        vo.setApplicantRoleLabel(a.getApplicantRole() != null ? ROLE_MAP.getOrDefault(a.getApplicantRole(), a.getApplicantRole()) : null);
        vo.setCompanyName(a.getCompanyName());
        vo.setCountry(a.getCountry());
        vo.setRegNumber(a.getRegNumber());
        vo.setEstablishedYear(a.getEstablishedYear());
        vo.setContactName(a.getContactName());
        vo.setContactTitle(a.getContactTitle());
        vo.setContactEmail(a.getContactEmail());
        vo.setContactPhone(a.getContactPhone());
        vo.setBuyCategory(a.getBuyCategory());
        vo.setBuyVolume(a.getBuyVolume());
        vo.setBuyVolumeLabel(a.getBuyVolume() != null ? BUY_VOLUME_MAP.getOrDefault(a.getBuyVolume(), a.getBuyVolume()) : null);
        vo.setBuyVolumeAmount(a.getBuyVolumeAmount());
        vo.setBuyVolumeCurrency(a.getBuyVolumeCurrency());
        vo.setFiscalPeriodStart(a.getFiscalPeriodStart());
        vo.setFiscalPeriodEnd(a.getFiscalPeriodEnd());
        vo.setBuyMarket(a.getBuyMarket());
        vo.setSupCategory(a.getSupCategory());
        vo.setSupCapacity(a.getSupCapacity());
        vo.setSupCerts(a.getSupCerts());
        vo.setSupExport(a.getSupExport());
        vo.setSupExportLabel(a.getSupExport() != null ? SUP_EXPORT_MAP.getOrDefault(a.getSupExport(), a.getSupExport()) : null);
        vo.setStatus(a.getStatus());
        vo.setReviewComment(a.getReviewComment());
        vo.setReviewedBy(a.getReviewedBy());
        vo.setReviewedAt(a.getReviewedAt());
        vo.setTenantId(a.getTenantId());
        vo.setCreateTime(a.getCreateTime());
        return vo;
    }
}
