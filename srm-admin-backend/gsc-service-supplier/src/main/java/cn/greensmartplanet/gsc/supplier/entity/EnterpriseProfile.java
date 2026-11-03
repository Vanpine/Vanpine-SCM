package cn.greensmartplanet.gsc.supplier.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 企业画像实体 — 从入驻申请抽取的结构化企业业务画像
 * <p>
 * 尽调确认通过时自动初始化，后续可由企业管理员手动编辑维护。
 * 撮合匹配以本表为供应商数据源（跨租户查询）。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("enterprise_profile")
public class EnterpriseProfile {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联租户（UK，一个租户只有一条画像） */
    private Long tenantId;

    /** 画像类型：buyer / supplier / both */
    private String profileType;

    /** 公司名称 */
    private String companyName;

    /** 所在国家 */
    private String country;

    /** 省份 */
    private String province;

    /** 城市 */
    private String city;

    // ---- 采购方画像 ----
    /** 采购品类（逗号分隔） */
    private String buyCategories;

    /** 年采购额（1-5 档） */
    private String buyVolume;
    private BigDecimal buyVolumeAmount;
    private String buyVolumeCurrency;
    private LocalDate fiscalPeriodStart;
    private LocalDate fiscalPeriodEnd;

    /** 企业注册号 */
    private String regNumber;

    /** 成立年份 */
    private Integer establishedYear;

    /** 目标市场 */
    private String buyMarket;

    /** 采购产品明细 */
    private String procurementDetail;

    // ---- 供应方画像 ----
    /** 生产品类（逗号分隔） */
    private String supCategories;

    /** 年产能 */
    private String supCapacity;

    /** 资质认证（逗号分隔） */
    private String supCerts;

    /** 出口经验（0-4 档） */
    private String supExport;

    /** 主要产品/服务 */
    private String mainProducts;

    /** 所属行业 */
    private String industry;

    /** 主要客户 */
    private String keyClients;

    /** 交货周期 */
    private String leadTime;

    /** 员工人数（1-5 档） */
    private String employeeCount;

    /** 年营业额（1-5 档） */
    private String annualRevenue;
    private BigDecimal annualRevenueAmount;
    private String annualRevenueCurrency;

    /** 企业简介 */
    private String description;

    // ---- 联系方式 ----
    /** 联系人 */
    private String contactName;

    /** 联系电话 */
    private String contactPhone;

    /** 联系邮箱 */
    private String contactEmail;

    /** 法定代表人 */
    private String legalRep;

    /** 注册地址 */
    private String registeredAddress;

    /** 画像完整度评分（0-100） */
    private Integer completenessScore;

    // ---- 履约表现（由交付终态聚合回写，delivery 表统计） ----
    /** 准时率（%） */
    private BigDecimal onTimeRate;

    /** 验收合格率（%） */
    private BigDecimal acceptPassRate;

    /** 退回批次数 */
    private Integer returnCount;

    /** 综合履约分（0-100） */
    private BigDecimal perfScore;

    /** 履约样本批次数（已验收普通批数） */
    private Integer perfSampleSize;

    // ---- 审计 ----
    /** 创建人用户ID */
    private Long createBy;

    /** 更新人用户ID */
    private Long updateBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;
}
