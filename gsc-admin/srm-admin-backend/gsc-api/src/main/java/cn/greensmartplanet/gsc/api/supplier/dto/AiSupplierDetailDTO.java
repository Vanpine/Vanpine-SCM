package cn.greensmartplanet.gsc.api.supplier.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 供应商详情契约 DTO — 供应商域内部接口返回给 AI 工具（get_supplier_detail）的字段子集
 * <p>
 * 与 {@link AiSupplierSummaryDTO} 的差异：多带企业画像核心字段（成立年份/产能/规模/履约评分等）
 * 与尽调明细（事务所名、报告状态、报告下载 URL、管理员最终确认时间），
 * 供 AI 回答「企业画像 / 资质状态 / 尽调进度 / 尽调报告」类问题。
 * accessStatus 由供应商域拼接好（approved / in_review / not_submitted），AI 直接读值不推理。
 * ddReportUrl 为访问时现签的短效 URL（600 秒），仅用于当轮展示，AI 不应缓存/落库。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSupplierDetailDTO {

    /** 供应商租户ID（雪花主键，序列化为字符串） */
    private Long tenantId;

    /** 画像类型：buyer / supplier / both（AI 据此区分「采购方 / 供应商 / 双身份」措辞，不闹「供应商侧画像」的笑话） */
    private String profileType;

    /** 公司名称 */
    private String companyName;

    /** 所属行业 */
    private String industry;

    /** 生产品类（逗号分隔） */
    private String supCategories;

    /** 资质认证（逗号分隔） */
    private String supCerts;

    /** 省份 */
    private String province;

    /** 城市 */
    private String city;

    /** 主要产品/服务 */
    private String mainProducts;

    /** 企业简介 */
    private String description;

    /** 企业注册号 */
    private String regNumber;

    /** 成立年份 */
    private Integer establishedYear;

    /** 年产能 */
    private String supCapacity;

    /** 出口经验（0-4 档） */
    private String supExport;

    /** 主要客户 */
    private String keyClients;

    /** 交货周期 */
    private String leadTime;

    /** 员工人数（1-5 档） */
    private String employeeCount;

    /** 年营业额（1-5 档） */
    private String annualRevenue;
    /** 年营收金额（原币种） */
    private BigDecimal annualRevenueAmount;
    /** 年营收币种（ISO 4217） */
    private String annualRevenueCurrency;
    /** 年营收期间开始日期 */
    private LocalDate fiscalPeriodStart;
    /** 年营收期间结束日期 */
    private LocalDate fiscalPeriodEnd;

    /** 画像完整度评分（0-100） */
    private Integer completenessScore;

    /** 综合履约分（0-100） */
    private BigDecimal perfScore;

    /** 履约准时率（%） */
    private BigDecimal onTimeRate;

    /** 履约验收合格率（%） */
    private BigDecimal acceptPassRate;

    /** 履约退回批次数 */
    private Integer returnCount;

    /** 履约样本批次数（已验收普通批数；为 0 表示暂无履约样本） */
    private Integer perfSampleSize;

    /** 准入状态：approved / in_review / not_submitted */
    private String accessStatus;

    /** 核查事务所名称（无尽调记录为 null） */
    private String ddFirmName;

    /** 尽调报告状态：pending / submitted / completed（无尽调记录为 null） */
    private String ddReportStatus;

    /** 尽调报告名称（有报告时为 null 之外的值） */
    private String ddReportName;

    /** 尽调报告短效下载 URL（有报告时现签，600 秒有效；无尽调报告为 null） */
    private String ddReportUrl;

    /** 管理员最终确认时间（准入判定依据；未确认为 null） */
    private LocalDateTime ddConfirmedAt;
}
