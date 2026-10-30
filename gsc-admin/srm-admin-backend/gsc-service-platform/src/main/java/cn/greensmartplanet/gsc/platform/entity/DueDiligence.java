package cn.greensmartplanet.gsc.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 尽职调查实体 — 企业在平台填写的真实企业信息 + 支付 + 推送 + 报告
 *
 * <p>⚠️ 迁移期桥接（微服务拆分）：本文件归属供应商域（阶段 7 gsc-service-supplier），platform 内为过渡副本，supplier 上线后随桥接收敛删除/改 Feign。</p>
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("due_diligence")
public class DueDiligence {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 关联入驻申请ID（1:1）
     */
    private Long applicationId;

    /** 租户ID（JOIN application 表查得，不在本表） */
    @TableField(exist = false)
    private Long tenantId;

    // ---- 基础信息（与 application 重叠，尽调表单可修改） ----
    private String companyName;
    private String country;
    private String regNumber;
    private Integer establishedYear;
    /** 注册地址（尽调表单填写，同步到企业画像） */
    private String registeredAddress;

    // ---- 联系信息（与 application 重叠，尽调表单可修改） ----
    private String contactName;
    private String contactTitle;
    private String contactEmail;
    private String contactPhone;

    // ---- 采购方信息（与 application 重叠，尽调表单可修改） ----
    private String buyCategory;
    private String buyVolume;
    private BigDecimal buyVolumeAmount;
    private String buyVolumeCurrency;
    private LocalDate fiscalPeriodStart;
    private LocalDate fiscalPeriodEnd;
    private String buyMarket;

    // ---- 供应方信息（与 application 重叠，尽调表单可修改） ----
    private String supCategory;
    private String supCapacity;
    private String supCerts;
    private String supExport;

    // ---- 申请身份（与 application 重叠） ----
    private String applicantRole;

    // ---- 企业补充信息（尽调独有） ----
    private String province;
    private String city;
    /** 营业执照 OSS key（访问时现签 URL） */
    private String businessLicenseKey;
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
    /** 股权结构文件 OSS key */
    private String shareholderInfoKey;
    /** 审计报告 OSS key */
    private String financialReportKey;
    /** 诉讼说明文本（与法律文书附件分列存储） */
    private String legalRecordText;
    /** 法律文书 OSS key */
    private String legalRecordKey;

    // ---- 套餐/支付 ----
    private String packageType;
    private String paymentStatus;
    private Long paymentAmount;
    /** 支付币种（ISO 4217） */
    private String paymentCurrency;
    /** 中台商户订单号 */
    private String paymentOutTradeNo;
    /** 中台/银行订单号 */
    private String paymentOrderNo;
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

    // ---- 尽调报告 ----
    /** 尽调报告 OSS key（访问时现签 URL） */
    private String ddReportKey;
    private String ddReportName;
    private String ddReportStatus;
    private LocalDateTime ddReportTime;
    /** 管理员最终确认时间（confirmDdComplete 时写入） */
    private LocalDateTime ddConfirmedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;
}
