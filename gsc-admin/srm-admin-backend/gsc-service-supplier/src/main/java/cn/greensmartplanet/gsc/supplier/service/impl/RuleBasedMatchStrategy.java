package cn.greensmartplanet.gsc.supplier.service.impl;

import cn.greensmartplanet.gsc.api.platform.ApplicationClient;
import cn.greensmartplanet.gsc.api.trade.dto.RequirementMatchFieldsDTO;
import cn.greensmartplanet.gsc.supplier.entity.DueDiligence;
import cn.greensmartplanet.gsc.supplier.entity.EnterpriseProfile;
import cn.greensmartplanet.gsc.supplier.mapper.DueDiligenceMapper;
import cn.greensmartplanet.gsc.supplier.support.MatchCandidateRecall;
import cn.greensmartplanet.gsc.supplier.vo.SupplierRecommendVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 规则匹配策略 — 首版智能寻源引擎
 * <p>
 * 三步走：品类/地域/资质硬过滤 → 多维度打分 → 按分降序排列。
 * v2 由 {@link AiMatchStrategy} 替代，接入 LLM 语义匹配。
 *
 * @author gsc
 */
@Slf4j
@Component("ruleBasedMatchStrategy")
@ConditionalOnProperty(name = "gsc.match.engine", havingValue = "ruleBased", matchIfMissing = true)
@RequiredArgsConstructor
public class RuleBasedMatchStrategy implements MatchStrategy {

    /**
     * 品类匹配权重
     */
    private static final BigDecimal W_CATEGORY = new BigDecimal("0.34");
    /**
     * 资质匹配权重
     */
    private static final BigDecimal W_CERT = new BigDecimal("0.21");
    /**
     * 画像完整度权重
     */
    private static final BigDecimal W_COMPLETENESS = new BigDecimal("0.21");
    /**
     * 出口经验权重
     */
    private static final BigDecimal W_EXPORT = new BigDecimal("0.09");
    /**
     * 履约表现权重
     */
    private static final BigDecimal W_PERFORMANCE = new BigDecimal("0.15");

    /**
     * 满分 100
     */
    private static final BigDecimal MAX_SCORE = new BigDecimal("100");
    /** 履约无样本时的中性分，避免新供应商被 0 分压死 */
    private static final BigDecimal PERFORMANCE_NEUTRAL = new BigDecimal("60");
    private final MatchCandidateRecall matchCandidateRecall;
    private final DueDiligenceMapper dueDiligenceMapper;
    private final ApplicationClient applicationClient;

    @Override
    public List<SupplierRecommendVO> match(RequirementMatchFieldsDTO requirement, boolean needLlmReasons) {
        // 规则引擎本身就是模板理由，needLlmReasons 不影响计算
        // 1. SQL 召回（品类/地域 + 排除自己），不再 COUNT 全表 + 拉 500 家
        List<EnterpriseProfile> suppliers = matchCandidateRecall.recall(requirement);
        if (suppliers.isEmpty()) {
            return Collections.emptyList();
        }
        log.info("智能匹配开始, requirementId={}, 召回候选数={}", requirement.getId(), suppliers.size());

        // 2. 过滤 + 打分
        List<SupplierRecommendVO> results = new ArrayList<>();
        for (EnterpriseProfile supplier : suppliers) {
            // 跳过发布方自己
            if (supplier.getTenantId().equals(requirement.getTenantId())) {
                continue;
            }

            List<String> reasons = new ArrayList<>();
            // 硬过滤 1：品类
            if (!passCategoryFilter(requirement.getCategory(), supplier.getSupCategories(), reasons)) {
                continue;
            }
            // 硬过滤 2：地域
            if (!passRegionFilter(requirement.getRegion(), supplier, reasons)) {
                continue;
            }
            // 硬过滤 3：资质
            if (!passCertFilter(requirement.getCertRequire(), supplier.getSupCerts(), reasons)) {
                continue;
            }

            // 打分
            BigDecimal score = computeScore(requirement, supplier, reasons);

            SupplierRecommendVO vo = SupplierRecommendVO.fromProfile(supplier);
            vo.setMatchScore(score);
            vo.setMatchReasons(reasons);
            results.add(vo);
        }

        // 3. 按分降序排列
        results.sort((a, b) -> b.getMatchScore().compareTo(a.getMatchScore()));

        // 4. 批量填充尽调信息（tenantId → applicationId → DD）
        if (!results.isEmpty()) {
            // 4a. 批量查申请映射（⚠️ Feign 一次批量调用替代逐租户直查 application 表）
            List<Long> tenantIds = results.stream().map(SupplierRecommendVO::getTenantId).toList();
            Map<Long, Long> tenantToAppId = applicationClient.getIdsByTenantIds(tenantIds).getData();
            if (tenantToAppId == null) {
                tenantToAppId = Map.of();
            }
            // 4b. 批查 DD → 建 tenantId → DD 映射
            if (!tenantToAppId.isEmpty()) {
                List<Long> appIds = new ArrayList<>(tenantToAppId.values());
                List<DueDiligence> ddList = dueDiligenceMapper.selectByApplicationIds(appIds);
                // appId → DD
                Map<Long, DueDiligence> appIdToDd = new HashMap<>();
                for (DueDiligence dd : ddList) {
                    appIdToDd.put(dd.getApplicationId(), dd);
                }
                // 填充到 VO
                for (SupplierRecommendVO vo : results) {
                    Long appId = tenantToAppId.get(vo.getTenantId());
                    if (appId != null) {
                        DueDiligence dd = appIdToDd.get(appId);
                        if (dd != null) {
                            vo.setDdStatus(dd.getDdFirmStatus());
                            vo.setDdReportUrl(dd.getDdReportKey());
                            vo.setDdReportName(dd.getDdReportName());
                            vo.setDdFirmName(dd.getDdFirmName());
                            vo.setDdFirmRemark(dd.getDdFirmReturnReason());
                            vo.setDdConfirmedAt(dd.getDdConfirmedAt());
                        }
                    }
                }
            }
        }

        log.info("智能匹配完成, requirementId={}, 匹配结果数={}", requirement.getId(), results.size());
        return results;
    }

    // ---- 硬过滤 ----

    /**
     * 品类过滤：需求品类与供应商生产品类有交集即通过
     */
    private boolean passCategoryFilter(String reqCategory, String supCategory, List<String> reasons) {
        if (reqCategory == null || reqCategory.isBlank()) return true; // 无品类要求则全放行
        if (supCategory == null || supCategory.isBlank()) return false;

        Set<String> reqSet = splitCategories(reqCategory);
        Set<String> supSet = splitCategories(supCategory);
        // 求交集
        Set<String> intersection = new HashSet<>(reqSet);
        intersection.retainAll(supSet);

        if (intersection.isEmpty()) return false;
        reasons.add("品类匹配：" + String.join("、", intersection));
        return true;
    }

    /**
     * 地域过滤：需求地域为空则全放行，否则供应商省份/城市包含需求地域
     */
    private boolean passRegionFilter(String reqRegion, EnterpriseProfile supplier, List<String> reasons) {
        if (reqRegion == null || reqRegion.isBlank()) {
            reasons.add("地域不限");
            return true;
        }
        String province = supplier.getProvince() != null ? supplier.getProvince() : "";
        String city = supplier.getCity() != null ? supplier.getCity() : "";
        if (province.contains(reqRegion) || city.contains(reqRegion)) {
            reasons.add("地域匹配：" + reqRegion);
            return true;
        }
        return false;
    }

    /**
     * 资质过滤：需求资质为空则全放行，否则需求的每项资质需在供应商资质中找到（模糊 LIKE）
     */
    private boolean passCertFilter(String reqCert, String supCert, List<String> reasons) {
        if (reqCert == null || reqCert.isBlank()) {
            reasons.add("无资质硬性要求");
            return true;
        }
        if (supCert == null || supCert.isBlank()) return false;

        List<String> reqCerts = splitList(reqCert);
        // 供应商资质也按分隔符拆分，精确比较（避免 contains 假阳性：ISO9001 命中 XXISO9001XX）
        Set<String> supCertSet = splitCategories(supCert);
        List<String> matched = new ArrayList<>();
        for (String rc : reqCerts) {
            String trimmed = rc.trim();
            if (trimmed.isEmpty()) continue;
            boolean exactMatch = supCertSet.stream().anyMatch(s -> s.equalsIgnoreCase(trimmed));
            if (exactMatch) {
                matched.add(trimmed);
            }
        }
        if (matched.size() < reqCerts.size()) return false;
        reasons.add("资质匹配：" + String.join("、", matched));
        return true;
    }

    // ---- 打分 ----

    /**
     * 综合评分 = 品类 × 0.34 + 资质 × 0.21 + 完整度 × 0.21 + 出口经验 × 0.09 + 履约 × 0.15
     */
    private BigDecimal computeScore(RequirementMatchFieldsDTO req, EnterpriseProfile supplier, List<String> reasons) {
        BigDecimal categoryScore = categoryScore(req.getCategory(), supplier.getSupCategories());
        BigDecimal certScore = certScore(req.getCertRequire(), supplier.getSupCerts());
        BigDecimal completenessScore = completenessScore(supplier.getCompletenessScore());
        BigDecimal exportScore = exportScore(supplier.getSupExport());
        BigDecimal performanceScore = performanceScore(supplier);

        BigDecimal total = categoryScore.multiply(W_CATEGORY)
                .add(certScore.multiply(W_CERT))
                .add(completenessScore.multiply(W_COMPLETENESS))
                .add(exportScore.multiply(W_EXPORT))
                .add(performanceScore.multiply(W_PERFORMANCE))
                .setScale(1, RoundingMode.HALF_UP);

        reasons.add(buildSummaryReason(categoryScore, certScore, completenessScore, exportScore, performanceScore, supplier, total));
        return total;
    }

    /** 生成自然语言匹配理由摘要 */
    private String buildSummaryReason(BigDecimal categoryScore, BigDecimal certScore,
                                       BigDecimal completenessScore, BigDecimal exportScore,
                                       BigDecimal performanceScore,
                                       EnterpriseProfile supplier, BigDecimal total) {
        StringBuilder sb = new StringBuilder("综合匹配度 ").append(total).append(" 分");
        // 品类
        if (categoryScore.compareTo(new BigDecimal("80")) >= 0) {
            sb.append(" — 品类高度匹配");
        } else if (categoryScore.compareTo(BigDecimal.ZERO) > 0) {
            sb.append(" — 品类部分匹配");
        }
        // 资质
        if (certScore.compareTo(MAX_SCORE) >= 0) {
            sb.append("，资质满足要求");
        } else if (certScore.compareTo(BigDecimal.ZERO) > 0) {
            sb.append("，资质部分满足");
        }
        // 完整度
        Integer completeness = supplier.getCompletenessScore();
        if (completeness != null && completeness >= 80) {
            sb.append("，企业信息完整可信");
        }
        // 出口经验
        String supExport = supplier.getSupExport();
        if (supExport != null && !supExport.isBlank()) {
            try {
                int level = Integer.parseInt(supExport.trim());
                if (level >= 3) sb.append("，出口经验丰富");
            } catch (NumberFormatException ignored) { }
        }
        // 履约表现（仅有履约样本时展示）
        Integer perfSample = supplier.getPerfSampleSize();
        if (perfSample != null && perfSample > 0 && supplier.getPerfScore() != null) {
            int perf = supplier.getPerfScore().intValue();
            if (perf >= 85) sb.append("，履约表现优秀");
            else if (perf >= 70) sb.append("，履约表现良好");
        }
        sb.append("。推荐优先联系。");
        return sb.toString();
    }

    /**
     * 品类得分 = Jaccard 相似度 × 100（交集/并集）
     */
    private BigDecimal categoryScore(String reqCategory, String supCategory) {
        if (reqCategory == null || reqCategory.isBlank()) return BigDecimal.ZERO;
        if (supCategory == null || supCategory.isBlank()) return BigDecimal.ZERO;

        Set<String> reqSet = splitCategories(reqCategory);
        Set<String> supSet = splitCategories(supCategory);
        Set<String> intersection = new HashSet<>(reqSet);
        intersection.retainAll(supSet);
        Set<String> union = new HashSet<>(reqSet);
        union.addAll(supSet);

        if (union.isEmpty()) return BigDecimal.ZERO;
        return new BigDecimal(intersection.size())
                .multiply(MAX_SCORE)
                .divide(new BigDecimal(union.size()), 1, RoundingMode.HALF_UP);
    }

    /**
     * 资质得分 = 需求资质被供应商覆盖比例 × 100（全部覆盖=100，部分覆盖=比例）
     */
    private BigDecimal certScore(String reqCert, String supCert) {
        if (reqCert == null || reqCert.isBlank()) return MAX_SCORE; // 无要求=满分
        if (supCert == null || supCert.isBlank()) return BigDecimal.ZERO;

        List<String> reqList = splitList(reqCert);
        Set<String> supCertSet = splitCategories(supCert);
        long matched = reqList.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .filter(s -> supCertSet.stream().anyMatch(sc -> sc.equalsIgnoreCase(s)))
                .count();
        return new BigDecimal(matched)
                .multiply(MAX_SCORE)
                .divide(new BigDecimal(reqList.size()), 1, RoundingMode.HALF_UP);
    }

    /**
     * 画像完整度得分 = completenessScore（已是 0-100）
     */
    private BigDecimal completenessScore(Integer score) {
        return score != null ? BigDecimal.valueOf(score) : BigDecimal.ZERO;
    }

    /**
     * 出口经验得分 = 出口档位 / 4 × 100
     */
    private BigDecimal exportScore(String supExport) {
        if (supExport == null || supExport.isBlank()) return BigDecimal.ZERO;
        try {
            int level = Integer.parseInt(supExport.trim());
            return new BigDecimal(level).multiply(MAX_SCORE).divide(new BigDecimal("4"), 1, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 履约得分 = perfScore（无样本取中性分 60，避免新供应商被压死）
     */
    private BigDecimal performanceScore(EnterpriseProfile supplier) {
        BigDecimal score = supplier.getPerfScore();
        return score != null ? score : PERFORMANCE_NEUTRAL;
    }

    // ---- 工具方法 ----

    /**
     * 按逗号或中文顿号分割品类
     */
    private Set<String> splitCategories(String text) {
        Set<String> result = new LinkedHashSet<>();
        for (String part : text.split("[,，、]")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }

    /**
     * 按逗号或中文顿号分割为列表
     */
    private List<String> splitList(String text) {
        List<String> result = new ArrayList<>();
        for (String part : text.split("[,，、]")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }
}
