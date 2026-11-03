package cn.greensmartplanet.gsc.api.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 入驻申请信息契约 DTO — 平台域内部接口返回给供应商域的申请字段子集
 * <p>
 * 供应商域使用场景（Feign 契约）：
 * <ul>
 *   <li>尽调记录重叠字段兜底（DueDiligenceServiceImpl）</li>
 *   <li>企业画像初始化/完整度评分（ProfileLifecycleServiceImpl）</li>
 *   <li>智能匹配尽调信息回填（AiMatchStrategy / RuleBasedMatchStrategy，仅需 id）</li>
 * </ul>
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationInfoDTO {

    /** 申请ID */
    private Long id;
    /** 关联租户ID */
    private Long tenantId;
    /** 申请身份（buyer/supplier/buyer,supplier） */
    private String applicantRole;
    /** 公司名称 */
    private String companyName;
    /** 所在国家/地区 */
    private String country;
    /** 企业注册号 */
    private String regNumber;
    /** 成立年份 */
    private Integer establishedYear;
    /** 联系人姓名 */
    private String contactName;
    /** 职位 */
    private String contactTitle;
    /** 联系邮箱 */
    private String contactEmail;
    /** 联系电话 */
    private String contactPhone;
    // ---- 采购方字段 ----
    /** 采购品类 */
    private String buyCategory;
    /** 年采购额 */
    private String buyVolume;
    /** 年采购金额（原币种） */
    private BigDecimal buyVolumeAmount;
    /** 年采购币种（ISO 4217） */
    private String buyVolumeCurrency;
    /** 年采购期间开始日期 */
    private LocalDate fiscalPeriodStart;
    /** 年采购期间结束日期 */
    private LocalDate fiscalPeriodEnd;
    /** 目标市场 */
    private String buyMarket;
    // ---- 供应方字段 ----
    /** 生产品类 */
    private String supCategory;
    /** 年产能 */
    private String supCapacity;
    /** 资质认证 */
    private String supCerts;
    /** 出口经验 */
    private String supExport;
    /** 提交时间 */
    private LocalDateTime createTime;
}
