package cn.greensmartplanet.gsc.supplier.service.impl;

import cn.greensmartplanet.gsc.api.supplier.dto.AiSupplierDetailDTO;
import cn.greensmartplanet.gsc.api.supplier.dto.AiSupplierRiskDTO;
import cn.greensmartplanet.gsc.supplier.service.ProfileQueryService;
import cn.greensmartplanet.gsc.supplier.service.SupplierRiskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 供应商风险评分服务实现 — 三维健康分（交付/质量/合规）
 * <p>
 * 复用 {@link ProfileQueryService#getDetailForAi} 拿画像 + 拼好的准入状态，避免重复
 * 组装 Feign（tenantId→applicationId→due_diligence）链路。评分算法如下：
 * <ul>
 *   <li>交付（0-100）：准时率 onTimeRate；无样本给中性 60 并标记样本不足</li>
 *   <li>质量（0-100）：验收合格率 acceptPassRate 打底，每笔退货扣 10 分（封顶扣到 0）</li>
 *   <li>合规（0-100）：approved=90 / in_review=60 / not_submitted=30，报告 completed 再 +5</li>
 * </ul>
 * 综合 = 交付×0.35 + 质量×0.40 + 合规×0.25；等级：≥80 low / 60-79 medium / &lt;60 high。
 * 语义统一为健康分——分数越高越安全。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierRiskServiceImpl implements SupplierRiskService {

    /** 质量维度：每笔退货扣分 */
    private static final int RETURN_PENALTY = 10;
    /** 无履约样本时的中性分（不惩罚也不奖励，标注样本不足供模型措辞） */
    private static final int NEUTRAL_SCORE = 60;
    /** 综合权重：交付 35% / 质量 40% / 合规 25% */
    private static final double W_DELIVERY = 0.35;
    private static final double W_QUALITY = 0.40;
    private static final double W_COMPLIANCE = 0.25;

    private final ProfileQueryService profileQueryService;

    @Override
    public AiSupplierRiskDTO assess(Long tenantId) {
        AiSupplierDetailDTO detail = profileQueryService.getDetailForAi(tenantId);
        if (detail == null) {
            return null;
        }

        int deliveryScore = scoreDelivery(detail);
        int qualityScore = scoreQuality(detail);
        int complianceScore = scoreCompliance(detail.getAccessStatus(), detail.getDdReportStatus());

        // 履约样本不足：准时率/合格率均无样本时标记，供模型解释为何交付/质量走中性分
        boolean sampleInsufficient = detail.getPerfSampleSize() == null || detail.getPerfSampleSize() == 0;
        boolean noDeliverySample = detail.getOnTimeRate() == null;
        boolean noQualitySample = detail.getAcceptPassRate() == null;

        int overall = (int) Math.round(
                deliveryScore * W_DELIVERY + qualityScore * W_QUALITY + complianceScore * W_COMPLIANCE);

        return AiSupplierRiskDTO.builder()
                .tenantId(detail.getTenantId())
                .companyName(detail.getCompanyName())
                .overallScore(overall)
                .riskLevel(resolveLevel(overall))
                .deliveryScore(deliveryScore)
                .qualityScore(qualityScore)
                .complianceScore(complianceScore)
                .sampleInsufficient(sampleInsufficient)
                .onTimeRate(detail.getOnTimeRate())
                .acceptPassRate(detail.getAcceptPassRate())
                .returnCount(detail.getReturnCount())
                .perfSampleSize(detail.getPerfSampleSize())
                .accessStatus(detail.getAccessStatus())
                .summary(buildSummary(detail, overall, deliveryScore, qualityScore, complianceScore,
                        noDeliverySample, noQualitySample))
                .build();
    }

    /** 交付维度：准时率直取；无样本给中性分 */
    private int scoreDelivery(AiSupplierDetailDTO detail) {
        BigDecimal rate = detail.getOnTimeRate();
        if (rate == null) {
            return NEUTRAL_SCORE;
        }
        return clamp(rate.intValue());
    }

    /** 质量维度：合格率打底，每笔退货扣 {@value #RETURN_PENALTY} 分（封顶扣到 0） */
    private int scoreQuality(AiSupplierDetailDTO detail) {
        BigDecimal rate = detail.getAcceptPassRate();
        int base = rate == null ? NEUTRAL_SCORE : clamp(rate.intValue());
        int returns = detail.getReturnCount() == null ? 0 : detail.getReturnCount();
        return Math.max(0, base - returns * RETURN_PENALTY);
    }

    /** 合规维度：准入状态分档，报告 completed 再加 5 分 */
    private int scoreCompliance(String accessStatus, String ddReportStatus) {
        int base;
        if (accessStatus == null) {
            base = 30;
        } else {
            base = switch (accessStatus) {
                case "approved" -> 90;
                case "in_review" -> 60;
                default -> 30; // not_submitted / 其他
            };
        }
        if ("completed".equals(ddReportStatus)) {
            base = Math.min(100, base + 5);
        }
        return base;
    }

    /** 0-100 夹逼（防御异常值，如负值/超 100 的脏数据） */
    private int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private String resolveLevel(int overall) {
        if (overall >= 80) {
            return "low";
        }
        if (overall >= 60) {
            return "medium";
        }
        return "high";
    }

    /** 规则模板拼中文摘要——结构事实概述，供模型作答参考，非大模型生成 */
    private String buildSummary(AiSupplierDetailDTO detail, int overall, int delivery, int quality,
                                int compliance, boolean noDeliverySample, boolean noQualitySample) {
        StringBuilder sb = new StringBuilder();
        sb.append(detail.getCompanyName() == null ? "该供应商" : detail.getCompanyName())
                .append("综合风险评分 ").append(overall).append(" 分（").append(resolveLevel(overall)).append("）");
        sb.append("；交付 ").append(delivery)
                .append(noDeliverySample ? "（暂无履约样本，按中性评估）" : "")
                .append("；质量 ").append(quality)
                .append(noQualitySample ? "（暂无验收样本，按中性评估）" : "")
                .append("；合规 ").append(compliance).append("。");
        if (detail.getReturnCount() != null && detail.getReturnCount() > 0) {
            sb.append("近履约退回 ").append(detail.getReturnCount()).append(" 批次。");
        }
        return sb.toString();
    }
}
