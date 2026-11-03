package cn.greensmartplanet.gsc.api.supplier.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 供应商风险评分契约 DTO — 供应商域内部接口返回给 AI 工具（risk_assess_supplier）的评分结果
 * <p>
 * 三维模型：交付（准时率）、质量（验收合格率+退货）、合规（准入状态）。
 * 语义统一为「健康分」——分数越高越安全（与 /risk/assess 旧注释「越高越危险」相反，已修正）。
 * <p>
 * 原始数据字段（onTimeRate 等）一并带回，供 AI 模型组织更具体的自然语言回答，
 * 而非只读一个总分。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSupplierRiskDTO {

    /** 供应商租户ID（雪花主键，序列化为字符串） */
    private Long tenantId;

    /** 公司名称 */
    private String companyName;

    /** 综合风险评分（0-100 健康分，越高越安全） */
    private Integer overallScore;

    /** 风险等级：low / medium / high */
    private String riskLevel;

    /** 交付维度得分（0-100） */
    private Integer deliveryScore;

    /** 质量维度得分（0-100） */
    private Integer qualityScore;

    /** 合规维度得分（0-100） */
    private Integer complianceScore;

    /** 履约样本不足标记（准时率/合格率无样本时，交付/质量按中性分评估） */
    private Boolean sampleInsufficient;

    /** 履约准时率（%） */
    private BigDecimal onTimeRate;

    /** 履约验收合格率（%） */
    private BigDecimal acceptPassRate;

    /** 履约退回批次数 */
    private Integer returnCount;

    /** 履约样本批次数 */
    private Integer perfSampleSize;

    /** 准入状态：approved / in_review / not_submitted */
    private String accessStatus;

    /** 规则生成的中文摘要（结构化事实概述，供模型作答参考） */
    private String summary;
}
