package cn.greensmartplanet.gsc.supplier.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 保存尽调扩展信息请求体（dd-info 专用）
 * <p>
 * 为什么不用实体直接接收：{@code DueDiligence} 实体既含用户可编辑字段，
 * 又含事务所工作流/支付/报告等系统字段（payment_status、dd_firm_status、
 * dd_report_key…）。直接 {@code @RequestBody DueDiligence} 会让前端有机会
 * 篡改这些系统字段；同时实体上的 {@code *Key} 字段与前端提交的字段名不可控，
 * 一旦对不上就被 Jackson 静默丢弃。
 * <p>
 * 附件字段统一以 {@code *Key} 结尾（OSS 对象路径），与实体列名一致；
 * 多值附件走 {@link #attachments} 子表整体替换式保存。
 *
 * @author gsc
 */
@Data
@Schema(description = "保存尽调扩展信息请求")
public class UpdateDdInfoDTO {

    // ==================== 基础信息（与 application 重叠，允许用户修改） ====================
    @Schema(description = "企业名称")
    private String companyName;
    @Schema(description = "国家")
    private String country;
    @Schema(description = "注册号")
    private String regNumber;
    @Schema(description = "成立年份")
    private Integer establishedYear;
    @Schema(description = "注册地址")
    private String registeredAddress;

    // ==================== 联系信息 ====================
    @Schema(description = "联系人姓名")
    private String contactName;
    @Schema(description = "联系人职务")
    private String contactTitle;
    @Schema(description = "联系邮箱")
    private String contactEmail;
    @Schema(description = "联系电话")
    private String contactPhone;

    // ==================== 采购方 / 供应方信息 ====================
    @Schema(description = "采购品类")
    private String buyCategory;
    @Schema(description = "采购量")
    private String buyVolume;
    @Schema(description = "年采购金额（原币种；使用字符串传输，避免精度丢失）")
    private BigDecimal buyVolumeAmount;
    @Schema(description = "年采购币种（ISO 4217）")
    private String buyVolumeCurrency;
    @Schema(description = "财务期间开始日期")
    private LocalDate fiscalPeriodStart;
    @Schema(description = "财务期间结束日期")
    private LocalDate fiscalPeriodEnd;
    @Schema(description = "采购市场")
    private String buyMarket;
    @Schema(description = "供应品类")
    private String supCategory;
    @Schema(description = "供应产能")
    private String supCapacity;
    @Schema(description = "供应资质")
    private String supCerts;
    @Schema(description = "供应出口情况")
    private String supExport;

    // ==================== 企业补充信息（尽调独有） ====================
    @Schema(description = "省份")
    private String province;
    @Schema(description = "城市")
    private String city;
    @Schema(description = "营业执照 OSS 对象路径（非签名 URL）")
    private String businessLicenseKey;
    @Schema(description = "官网")
    private String website;
    @Schema(description = "企业简介")
    private String description;
    @Schema(description = "员工人数")
    private String employeeCount;
    @Schema(description = "年营收")
    private String annualRevenue;
    @Schema(description = "年营收金额（原币种；推荐字符串传输）")
    private BigDecimal annualRevenueAmount;
    @Schema(description = "年营收币种（ISO 4217）")
    private String annualRevenueCurrency;

    // ==================== 供应方补充（尽调独有） ====================
    @Schema(description = "主营产品")
    private String mainProducts;
    @Schema(description = "管理认证")
    private String managementCert;
    @Schema(description = "产品认证")
    private String productCert;
    @Schema(description = "主要客户")
    private String keyClients;
    @Schema(description = "交付条款")
    private String deliveryTerms;
    @Schema(description = "交货周期")
    private String leadTime;
    @Schema(description = "出口市场")
    private String exportMarkets;

    // ==================== 采购方补充（尽调独有） ====================
    @Schema(description = "所属行业")
    private String industry;
    @Schema(description = "采购详情")
    private String procurementDetail;
    @Schema(description = "供应商要求")
    private String supplierRequirement;
    @Schema(description = "目标区域")
    private String targetRegion;
    @Schema(description = "付款条件")
    private String paymentTerms;

    // ==================== 尽调核心材料 ====================
    @Schema(description = "法人姓名")
    private String legalPersonName;
    @Schema(description = "法人证件号")
    private String legalPersonId;
    @Schema(description = "股权结构文件 OSS 对象路径（非签名 URL）")
    private String shareholderInfoKey;
    @Schema(description = "审计报告 OSS 对象路径（非签名 URL）")
    private String financialReportKey;
    @Schema(description = "诉讼说明文本")
    private String legalRecordText;
    @Schema(description = "法律文书 OSS 对象路径（非签名 URL）")
    private String legalRecordKey;

    // ==================== 多值附件材料（整体替换式保存） ====================
    /**
     * 附件材料列表：有值则整体替换子表（先物理删旧、再插入新），
     * 传 null 表示本次不改动附件（避免误清空），传空数组表示清空。
     */
    @Schema(description = "附件材料列表（整体替换式保存；null 表示不改动）")
    private List<AttachmentItem> attachments;

    /**
     * 附件材料单项：前端上传后拿到的 ossKey + 原始文件名。
     */
    @Data
    @Schema(description = "附件材料项")
    public static class AttachmentItem {

        @Schema(description = "OSS 对象路径（非签名 URL）")
        private String ossKey;

        @Schema(description = "原始文件名")
        private String fileName;
    }
}
