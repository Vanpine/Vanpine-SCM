package cn.greensmartplanet.gsc.platform.vo;

import cn.greensmartplanet.gsc.framework.oss.OssService;
import cn.greensmartplanet.gsc.platform.entity.DdChecklist;
import cn.greensmartplanet.gsc.platform.entity.DueDiligence;
import cn.greensmartplanet.gsc.platform.entity.DueDiligenceAttachment;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 尽职调查展示 VO
 *
 * <p>⚠️ 迁移期桥接（微服务拆分）：本文件归属供应商域（阶段 7 gsc-service-supplier），platform 内为过渡副本，supplier 上线后随桥接收敛删除/改 Feign。</p>
 * @author gsc
 */
@Data
@Schema(description = "尽职调查详情")
public class DueDiligenceVO {

    /**
     * 申请身份中文映射
     */
    private static final java.util.Map<String, String> ROLE_MAP = java.util.Map.of(
            "buyer", "采购商",
            "supplier", "供应商",
            "buyer,supplier", "采购商+供应商"
    );
    @Schema(description = "ID")
    private Long id;
    @Schema(description = "关联入驻申请ID")
    private Long applicationId;
    // ---- 基础信息（与 application 重叠） ----
    private String companyName;
    private String country;
    private String regNumber;
    private Integer establishedYear;
    private String registeredAddress;
    // ---- 联系信息（与 application 重叠） ----
    private String contactName;
    private String contactTitle;
    private String contactEmail;
    private String contactPhone;
    // ---- 采购方信息（与 application 重叠） ----
    private String buyCategory;
    private String buyVolume;
    private BigDecimal buyVolumeAmount;
    private String buyVolumeCurrency;
    @Schema(description = "财务期间开始日期")
    private LocalDate fiscalPeriodStart;
    @Schema(description = "财务期间结束日期")
    private LocalDate fiscalPeriodEnd;
    private String buyMarket;
    // ---- 供应方信息（与 application 重叠） ----
    private String supCategory;
    private String supCapacity;
    private String supCerts;
    private String supExport;
    // ---- 申请身份 ----
    private String applicantRole;
    private String applicantRoleLabel;
    // ---- 企业补充信息（尽调独有） ----
    private String province;
    private String city;
    /** 营业执照访问 URL（由 businessLicenseKey 现签，非库中值） */
    private String businessLicenseUrl;
    /** 营业执照 OSS 对象路径（供编辑回填后再次提交，非签名 URL） */
    private String businessLicenseKey;
    /** 附件材料列表（由子表现签组装） */
    private List<DueDiligenceAttachmentVO> attachments;
    private String website;
    private String description;
    private String employeeCount;
    private String annualRevenue;
    private BigDecimal annualRevenueAmount;
    private String annualRevenueCurrency;
    // ---- 供应方补充（尽调独有） ----
    private String mainProducts;
    private String managementCert;
    private String productCert;
    private String keyClients;
    private String deliveryTerms;
    private String leadTime;
    private String exportMarkets;
    // ---- 采购方补充（尽调独有） ----
    private String industry;
    private String procurementDetail;
    private String supplierRequirement;
    private String targetRegion;
    private String paymentTerms;
    // ---- 尽调核心材料 ----
    private String legalPersonName;
    private String legalPersonId;
    /** 股权结构文件访问 URL（现签） */
    private String shareholderInfo;
    /** 股权结构文件 OSS 对象路径（供编辑回填后再次提交，非签名 URL） */
    private String shareholderInfoKey;
    /** 审计报告访问 URL（现签） */
    private String financialReportUrl;
    /** 审计报告 OSS 对象路径（供编辑回填后再次提交，非签名 URL） */
    private String financialReportKey;
    /** 诉讼说明文本 */
    private String legalRecordText;
    /** 法律文书访问 URL（现签） */
    private String legalRecordUrl;
    /** 法律文书 OSS 对象路径（供编辑回填后再次提交，非签名 URL） */
    private String legalRecordKey;
    // ---- 套餐/支付 ----
    private String packageType;
    private String paymentStatus;
    private Long paymentAmount;
    /** 支付币种（ISO 4217） */
    private String paymentCurrency;
    /** 首次资料提交时间（支付完成时写入，不再变更） */
    private LocalDateTime ddSubmittedAt;
    /** 退回后重新提交时间（每次重新提交更新为最新） */
    private LocalDateTime ddResubmittedAt;
    // ---- 事务所 ----
    private Long ddFirmId;
    private String ddFirmName;
    private String ddFirmStatus;
    private String ddFirmProgress;
    private LocalDateTime ddFirmAssignedAt;
    private LocalDate ddFirmDeadline;
    private String ddFirmInstruction;
    /** 事务所退回原因（ddFirmStatus=returned 时填写） */
    private String ddFirmReturnReason;
    // ---- 核查清单 ----
    private List<DdChecklist> checklist;
    // ---- 报告 ----
    private String ddReportUrl;
    private String ddReportName;
    private String ddReportStatus;
    private LocalDateTime ddReportTime;
    private LocalDateTime ddConfirmedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 实体 → VO（不签名版）：附件字段为库中原值（key），供内部流程使用。
     * <p>需要可访问的附件地址时请用 {@link #fromEntity(DueDiligence, List, OssService)}。
     */
    public static DueDiligenceVO fromEntity(DueDiligence d) {
        return fromEntity(d, null, null);
    }

    /**
     * 实体 → VO（签名版）：附件字段按 oss_key 现签短效 URL，多值附件从子表读入。
     * <p>
     * 库里只存 key —— 签名 URL 会过期，持久化后必然变死链。
     * 每次查询现签，前端拿到的始终是新鲜可访问的地址。
     *
     * @param d           尽调实体
     * @param attachments 子表附件列表（可为 null，表示本次不查附件）
     * @param ossService  OSS 服务；为 null 时不签名
     */
    public static DueDiligenceVO fromEntity(DueDiligence d, List<DueDiligenceAttachment> attachments,
                                            OssService ossService) {
        DueDiligenceVO vo = new DueDiligenceVO();
        vo.setId(d.getId());
        vo.setApplicationId(d.getApplicationId());
        // 基础信息（与 application 重叠）
        vo.setCompanyName(d.getCompanyName());
        vo.setCountry(d.getCountry());
        vo.setRegNumber(d.getRegNumber());
        vo.setEstablishedYear(d.getEstablishedYear());
        vo.setRegisteredAddress(d.getRegisteredAddress());
        // 联系信息
        vo.setContactName(d.getContactName());
        vo.setContactTitle(d.getContactTitle());
        vo.setContactEmail(d.getContactEmail());
        vo.setContactPhone(d.getContactPhone());
        // 采购方信息
        vo.setBuyCategory(d.getBuyCategory());
        vo.setBuyVolume(d.getBuyVolume());
        vo.setBuyVolumeAmount(d.getBuyVolumeAmount());
        vo.setBuyVolumeCurrency(d.getBuyVolumeCurrency());
        vo.setFiscalPeriodStart(d.getFiscalPeriodStart());
        vo.setFiscalPeriodEnd(d.getFiscalPeriodEnd());
        vo.setBuyMarket(d.getBuyMarket());
        // 供应方信息
        vo.setSupCategory(d.getSupCategory());
        vo.setSupCapacity(d.getSupCapacity());
        vo.setSupCerts(d.getSupCerts());
        vo.setSupExport(d.getSupExport());
        // 申请身份
        vo.setApplicantRole(d.getApplicantRole());
        vo.setApplicantRoleLabel(d.getApplicantRole() != null ? ROLE_MAP.getOrDefault(d.getApplicantRole(), d.getApplicantRole()) : null);
        // 企业补充信息（尽调独有）—— 营业执照现签
        vo.setProvince(d.getProvince());
        vo.setCity(d.getCity());
        vo.setBusinessLicenseUrl(sign(d.getBusinessLicenseKey(), ossService));
        // 同时回传原始 key，前端编辑时可原样回填再次提交（URL 会过期，不能回传）
        vo.setBusinessLicenseKey(d.getBusinessLicenseKey());
        if (attachments != null) {
            vo.setAttachments(attachments.stream().map(a -> DueDiligenceAttachmentVO.of(a, ossService)).toList());
        }
        vo.setWebsite(d.getWebsite());
        vo.setDescription(d.getDescription());
        vo.setEmployeeCount(d.getEmployeeCount());
        vo.setAnnualRevenue(d.getAnnualRevenue());
        vo.setAnnualRevenueAmount(d.getAnnualRevenueAmount());
        vo.setAnnualRevenueCurrency(d.getAnnualRevenueCurrency());
        vo.setFiscalPeriodStart(d.getFiscalPeriodStart());
        vo.setFiscalPeriodEnd(d.getFiscalPeriodEnd());
        // 供应方补充（尽调独有）
        vo.setMainProducts(d.getMainProducts());
        vo.setManagementCert(d.getManagementCert());
        vo.setProductCert(d.getProductCert());
        vo.setKeyClients(d.getKeyClients());
        vo.setDeliveryTerms(d.getDeliveryTerms());
        vo.setLeadTime(d.getLeadTime());
        vo.setExportMarkets(d.getExportMarkets());
        // 采购方补充（尽调独有）
        vo.setIndustry(d.getIndustry());
        vo.setProcurementDetail(d.getProcurementDetail());
        vo.setSupplierRequirement(d.getSupplierRequirement());
        vo.setTargetRegion(d.getTargetRegion());
        vo.setPaymentTerms(d.getPaymentTerms());
        // 尽调核心材料 —— 现签
        vo.setLegalPersonName(d.getLegalPersonName());
        vo.setLegalPersonId(d.getLegalPersonId());
        vo.setShareholderInfo(sign(d.getShareholderInfoKey(), ossService));
        vo.setShareholderInfoKey(d.getShareholderInfoKey());
        vo.setFinancialReportUrl(sign(d.getFinancialReportKey(), ossService));
        vo.setFinancialReportKey(d.getFinancialReportKey());
        vo.setLegalRecordText(d.getLegalRecordText());
        vo.setLegalRecordUrl(sign(d.getLegalRecordKey(), ossService));
        vo.setLegalRecordKey(d.getLegalRecordKey());
        // 套餐/支付
        vo.setPackageType(d.getPackageType());
        vo.setPaymentStatus(d.getPaymentStatus());
        vo.setPaymentAmount(d.getPaymentAmount());
        vo.setPaymentCurrency(d.getPaymentCurrency());
        vo.setDdSubmittedAt(d.getDdSubmittedAt());
        vo.setDdResubmittedAt(d.getDdResubmittedAt());
        // 事务所
        vo.setDdFirmId(d.getDdFirmId());
        vo.setDdFirmName(d.getDdFirmName());
        vo.setDdFirmStatus(d.getDdFirmStatus());
        vo.setDdFirmProgress(d.getDdFirmProgress());
        vo.setDdFirmAssignedAt(d.getDdFirmAssignedAt());
        vo.setDdFirmDeadline(d.getDdFirmDeadline());
        vo.setDdFirmInstruction(d.getDdFirmInstruction());
        vo.setDdFirmReturnReason(d.getDdFirmReturnReason());
        // 报告 —— 现签
        vo.setDdReportUrl(sign(d.getDdReportKey(), ossService));
        vo.setDdReportName(d.getDdReportName());
        vo.setDdReportStatus(d.getDdReportStatus());
        vo.setDdReportTime(d.getDdReportTime());
        vo.setDdConfirmedAt(d.getDdConfirmedAt());
        vo.setCreateTime(d.getCreateTime());
        vo.setUpdateTime(d.getUpdateTime());
        return vo;
    }

    /** 附件字段现签：key → 短效 URL；key 为空返回 null（前端据此判断"未上传"） */
    private static String sign(String key, OssService ossService) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return ossService != null ? ossService.generateGetUrl(key) : key;
    }
}
