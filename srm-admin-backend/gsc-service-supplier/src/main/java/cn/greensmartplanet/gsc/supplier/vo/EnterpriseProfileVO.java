package cn.greensmartplanet.gsc.supplier.vo;

import cn.greensmartplanet.gsc.supplier.entity.EnterpriseProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 企业画像 VO
 *
 * @author gsc
 */
@Data
@Schema(description = "企业画像")
public class EnterpriseProfileVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "租户ID")
    private Long tenantId;

    @Schema(description = "画像类型：buyer/supplier/both")
    private String profileType;

    @Schema(description = "公司名称")
    private String companyName;

    @Schema(description = "所在国家")
    private String country;

    @Schema(description = "省份")
    private String province;

    @Schema(description = "城市")
    private String city;

    @Schema(description = "采购品类")
    private String buyCategories;

    @Schema(description = "年采购额（1-5档）")
    private String buyVolume;
    @Schema(description = "年采购金额（原币种）")
    private BigDecimal buyVolumeAmount;
    @Schema(description = "年采购币种（ISO 4217）")
    private String buyVolumeCurrency;
    @Schema(description = "财务期间开始日期")
    private LocalDate fiscalPeriodStart;
    @Schema(description = "财务期间结束日期")
    private LocalDate fiscalPeriodEnd;

    @Schema(description = "企业注册号")
    private String regNumber;

    @Schema(description = "成立年份")
    private Integer establishedYear;

    @Schema(description = "目标市场")
    private String buyMarket;

    @Schema(description = "采购产品明细")
    private String procurementDetail;

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

    @Schema(description = "员工人数（1-5档）")
    private String employeeCount;

    @Schema(description = "年营业额（1-5档）")
    private String annualRevenue;
    @Schema(description = "年营收金额（原币种）")
    private BigDecimal annualRevenueAmount;
    @Schema(description = "年营收币种（ISO 4217）")
    private String annualRevenueCurrency;

    @Schema(description = "企业简介")
    private String description;

    @Schema(description = "联系人")
    private String contactName;

    @Schema(description = "联系电话")
    private String contactPhone;

    @Schema(description = "联系邮箱")
    private String contactEmail;

    @Schema(description = "法定代表人")
    private String legalRep;

    @Schema(description = "注册地址")
    private String registeredAddress;

    @Schema(description = "画像完整度评分（0-100）")
    private Integer completenessScore;

    @Schema(description = "履约准时率（%）")
    private BigDecimal onTimeRate;

    @Schema(description = "履约验收合格率（%）")
    private BigDecimal acceptPassRate;

    @Schema(description = "履约退回批次数")
    private Integer returnCount;

    @Schema(description = "综合履约分（0-100）")
    private BigDecimal perfScore;

    @Schema(description = "履约样本批次数")
    private Integer perfSampleSize;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    // ---- 对接状态 ----
    @Schema(description = "当前用户与该供应商的对接状态：pending/accepted/rejected/null")
    private String myConnectionStatus;

    @Schema(description = "当前用户与该供应商的对接需求数量")
    private Integer myConnectionCount;

    // ---- 尽调信息 ----
    @Schema(description = "核查事务所名称")
    private String ddFirmName;

    @Schema(description = "事务所核查结论")
    private String ddFirmRemark;

    @Schema(description = "尽调报告URL")
    private String ddReportUrl;

    public static EnterpriseProfileVO fromEntity(EnterpriseProfile p) {
        EnterpriseProfileVO vo = new EnterpriseProfileVO();
        vo.setId(p.getId());
        vo.setTenantId(p.getTenantId());
        vo.setProfileType(p.getProfileType());
        vo.setCompanyName(p.getCompanyName());
        vo.setCountry(p.getCountry());
        vo.setProvince(p.getProvince());
        vo.setCity(p.getCity());
        vo.setBuyCategories(p.getBuyCategories());
        vo.setBuyVolume(p.getBuyVolume());
        vo.setBuyVolumeAmount(p.getBuyVolumeAmount());
        vo.setBuyVolumeCurrency(p.getBuyVolumeCurrency());
        vo.setFiscalPeriodStart(p.getFiscalPeriodStart());
        vo.setFiscalPeriodEnd(p.getFiscalPeriodEnd());
        vo.setRegNumber(p.getRegNumber());
        vo.setEstablishedYear(p.getEstablishedYear());
        vo.setBuyMarket(p.getBuyMarket());
        vo.setProcurementDetail(p.getProcurementDetail());
        vo.setSupCategories(p.getSupCategories());
        vo.setSupCapacity(p.getSupCapacity());
        vo.setSupCerts(p.getSupCerts());
        vo.setSupExport(p.getSupExport());
        vo.setMainProducts(p.getMainProducts());
        vo.setIndustry(p.getIndustry());
        vo.setKeyClients(p.getKeyClients());
        vo.setLeadTime(p.getLeadTime());
        vo.setEmployeeCount(p.getEmployeeCount());
        vo.setAnnualRevenue(p.getAnnualRevenue());
        vo.setAnnualRevenueAmount(p.getAnnualRevenueAmount());
        vo.setAnnualRevenueCurrency(p.getAnnualRevenueCurrency());
        vo.setFiscalPeriodStart(p.getFiscalPeriodStart());
        vo.setFiscalPeriodEnd(p.getFiscalPeriodEnd());
        vo.setDescription(p.getDescription());
        vo.setContactName(p.getContactName());
        vo.setContactPhone(p.getContactPhone());
        vo.setContactEmail(p.getContactEmail());
        vo.setLegalRep(p.getLegalRep());
        vo.setRegisteredAddress(p.getRegisteredAddress());
        vo.setCompletenessScore(p.getCompletenessScore());
        vo.setOnTimeRate(p.getOnTimeRate());
        vo.setAcceptPassRate(p.getAcceptPassRate());
        vo.setReturnCount(p.getReturnCount());
        vo.setPerfScore(p.getPerfScore());
        vo.setPerfSampleSize(p.getPerfSampleSize());
        vo.setCreateTime(p.getCreateTime());
        vo.setUpdateTime(p.getUpdateTime());
        return vo;
    }
}
