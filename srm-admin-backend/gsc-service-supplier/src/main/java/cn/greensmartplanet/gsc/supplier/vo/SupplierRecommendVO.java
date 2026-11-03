package cn.greensmartplanet.gsc.supplier.vo;

import cn.greensmartplanet.gsc.supplier.entity.EnterpriseProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 供应商推荐 VO — 智能匹配结果
 *
 * @author gsc
 */
@Data
@Schema(description = "供应商推荐")
public class SupplierRecommendVO {

    @Schema(description = "供应商租户ID")
    private Long tenantId;

    @Schema(description = "公司名称")
    private String companyName;

    @Schema(description = "省份")
    private String province;

    @Schema(description = "城市")
    private String city;

    @Schema(description = "生产品类")
    private String supCategories;

    @Schema(description = "年产能")
    private String supCapacity;

    @Schema(description = "资质认证")
    private String supCerts;

    @Schema(description = "出口经验（0-4档）")
    private String supExport;

    @Schema(description = "主要产品/服务")
    private String mainProducts;

    @Schema(description = "所属行业")
    private String industry;

    @Schema(description = "主要客户")
    private String keyClients;

    @Schema(description = "交货周期")
    private String leadTime;

    @Schema(description = "企业注册号/信用代码")
    private String regNumber;

    @Schema(description = "成立年份")
    private Integer establishedYear;

    @Schema(description = "画像完整度")
    private Integer completenessScore;

    @Schema(description = "综合履约分（0-100）")
    private BigDecimal perfScore;

    @Schema(description = "履约样本批次数")
    private Integer perfSampleSize;

    @Schema(description = "匹配得分（0-100）")
    private BigDecimal matchScore;

    @Schema(description = "匹配理由列表")
    private List<String> matchReasons;

    @Schema(description = "理由状态：generating / ready / fallback，仅详情页使用")
    private String reasonStatus;

    @Schema(description = "当前采购方与该供应商的对接状态")
    private String myConnectionStatus;

    @Schema(description = "当前采购方与该供应商已接受的对接数")
    private Integer myConnectionCount;

    // ---- 尽调信息 ----
    @Schema(description = "尽调状态：pending/submitted/completed")
    private String ddStatus;

    @Schema(description = "尽调报告URL")
    private String ddReportUrl;

    @Schema(description = "尽调报告名称")
    private String ddReportName;

    @Schema(description = "事务所名称")
    private String ddFirmName;

    @Schema(description = "事务所核查结论/退回原因")
    private String ddFirmRemark;

    @Schema(description = "尽调确认时间")
    private LocalDateTime ddConfirmedAt;

    /** 从画像 Entity 创建基础 VO */
    public static SupplierRecommendVO fromProfile(EnterpriseProfile p) {
        SupplierRecommendVO vo = new SupplierRecommendVO();
        vo.setTenantId(p.getTenantId());
        vo.setCompanyName(p.getCompanyName());
        vo.setProvince(p.getProvince());
        vo.setCity(p.getCity());
        vo.setSupCategories(p.getSupCategories());
        vo.setSupCapacity(p.getSupCapacity());
        vo.setSupCerts(p.getSupCerts());
        vo.setSupExport(p.getSupExport());
        vo.setMainProducts(p.getMainProducts());
        vo.setIndustry(p.getIndustry());
        vo.setKeyClients(p.getKeyClients());
        vo.setLeadTime(p.getLeadTime());
        vo.setRegNumber(p.getRegNumber());
        vo.setEstablishedYear(p.getEstablishedYear());
        vo.setCompletenessScore(p.getCompletenessScore());
        vo.setPerfScore(p.getPerfScore());
        vo.setPerfSampleSize(p.getPerfSampleSize());
        return vo;
    }
}
