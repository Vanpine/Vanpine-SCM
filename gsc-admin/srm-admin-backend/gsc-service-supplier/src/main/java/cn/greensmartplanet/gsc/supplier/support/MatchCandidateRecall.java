package cn.greensmartplanet.gsc.supplier.support;

import cn.greensmartplanet.gsc.api.trade.dto.RequirementMatchFieldsDTO;
import cn.greensmartplanet.gsc.supplier.entity.EnterpriseProfile;
import cn.greensmartplanet.gsc.supplier.mapper.EnterpriseProfileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 匹配召回 — 先按品类/地域把候选缩到几十家，再交给规则或 AI 精排。
 * <p>
 * 交互式推荐不能把全库整包丢给 Python/LLM。正规链路是「召回 + 精排」。
 * 品类、地域分两路查再合并（先品类后地域），不要 AND 绑死；
 * 两路都空才回退完整度 Top N。
 *
 * @author gsc
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchCandidateRecall {

    /** 召回上限：够精排，又不会把 Embedding/JSON 打爆 */
    public static final int RECALL_LIMIT = 80;

    private final EnterpriseProfileMapper enterpriseProfileMapper;

    /**
     * 按需求召回供应商画像（已排除发布方自己、纯买方）。
     *
     * @param requirement 需求匹配字段
     * @return 召回画像，最多 {@link #RECALL_LIMIT} 条
     */
    public List<EnterpriseProfile> recall(RequirementMatchFieldsDTO requirement) {
        List<String> categoryTokens = splitTokens(requirement.getCategory());
        String region = StringUtils.hasText(requirement.getRegion()) ? requirement.getRegion().trim() : null;
        Long excludeTenantId = requirement.getTenantId();

        List<EnterpriseProfile> recalled;
        if (!categoryTokens.isEmpty() && region != null) {
            // 两路召回：品类一路、地域一路，合并时品类优先占满名额
            List<EnterpriseProfile> byCategory = enterpriseProfileMapper.selectSuppliersForRecall(
                    excludeTenantId, categoryTokens, null, RECALL_LIMIT);
            List<EnterpriseProfile> byRegion = enterpriseProfileMapper.selectSuppliersForRecall(
                    excludeTenantId, List.of(), region, RECALL_LIMIT);
            recalled = mergePreferPrimary(byCategory, byRegion);
        } else {
            recalled = enterpriseProfileMapper.selectSuppliersForRecall(
                    excludeTenantId, categoryTokens, region, RECALL_LIMIT);
        }

        // 两路都空（品类写得很偏且地域也对不上）才回退完整度 Top N
        if (recalled.isEmpty() && (!categoryTokens.isEmpty() || region != null)) {
            log.info("匹配召回为空，回退完整度TopN: requirementId={}, category={}, region={}",
                    requirement.getId(), requirement.getCategory(), region);
            recalled = enterpriseProfileMapper.selectSuppliersForRecall(
                    excludeTenantId, List.of(), null, RECALL_LIMIT);
        }

        log.info("匹配召回完成: requirementId={}, size={}, categoryTokens={}, region={}",
                requirement.getId(), recalled.size(), categoryTokens.size(), region);
        return recalled;
    }

    /**
     * 按逗号/顿号拆品类，去空白、去重，供 SQL LIKE。
     *
     * @param text 需求品类原文
     * @return token 列表，空文本返回空列表
     */
    public static List<String> splitTokens(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        for (String part : text.split("[,，、]")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                tokens.add(trimmed);
            }
        }
        return new ArrayList<>(tokens);
    }

    /**
     * 主路（品类）在前，辅路（地域）补齐到 {@link #RECALL_LIMIT}，按 tenantId 去重。
     */
    static List<EnterpriseProfile> mergePreferPrimary(List<EnterpriseProfile> primary,
                                                      List<EnterpriseProfile> secondary) {
        Map<Long, EnterpriseProfile> merged = new LinkedHashMap<>();
        addUnique(merged, primary);
        for (EnterpriseProfile profile : secondary == null ? List.<EnterpriseProfile>of() : secondary) {
            if (merged.size() >= RECALL_LIMIT) {
                break;
            }
            addUnique(merged, List.of(profile));
        }
        return new ArrayList<>(merged.values());
    }

    private static void addUnique(Map<Long, EnterpriseProfile> merged, List<EnterpriseProfile> rows) {
        if (rows == null) {
            return;
        }
        for (EnterpriseProfile profile : rows) {
            if (profile == null || profile.getTenantId() == null) {
                continue;
            }
            merged.putIfAbsent(profile.getTenantId(), profile);
        }
    }
}
