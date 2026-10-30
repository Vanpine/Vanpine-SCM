package cn.greensmartplanet.gsc.supplier.service.impl;

import cn.greensmartplanet.gsc.api.trade.RequirementClient;
import cn.greensmartplanet.gsc.api.trade.dto.RequirementMatchFieldsDTO;
import cn.greensmartplanet.gsc.api.trade.dto.RequirementRecallQueryDTO;
import cn.greensmartplanet.gsc.common.constant.SystemConstants;
import cn.greensmartplanet.gsc.common.enums.ConnectionStatus;
import cn.greensmartplanet.gsc.common.enums.RequirementStatus;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.common.result.PageResult;
import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.supplier.entity.Connection;
import cn.greensmartplanet.gsc.supplier.entity.EnterpriseProfile;
import cn.greensmartplanet.gsc.supplier.mapper.ConnectionMapper;
import cn.greensmartplanet.gsc.supplier.mapper.EnterpriseProfileMapper;
import cn.greensmartplanet.gsc.supplier.projection.ConnectionCountRow;
import cn.greensmartplanet.gsc.supplier.support.MatchCandidateRecall;
import cn.greensmartplanet.gsc.supplier.vo.RequirementRecommendVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 需求广场「找需求」智能推荐：本厂画像 → SQL 两路召回 → Python 精排 → 分页。
 * 不写 AI 理由。对接状态现查。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequirementPlazaRecommendService {

    private static final int RECALL_LIMIT = MatchCandidateRecall.RECALL_LIMIT;

    private final EnterpriseProfileMapper enterpriseProfileMapper;
    private final RequirementClient requirementClient;
    private final ConnectionMapper connectionMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public PageResult<RequirementRecommendVO> recommend(Long tenantId, String keyword, int pageNum, int pageSize) {
        int safePage = Math.max(pageNum, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), SystemConstants.MATCH_RECOMMEND_MAX_PAGE_SIZE);

        EnterpriseProfile profile = enterpriseProfileMapper.selectByTenantId(tenantId);
        if (profile == null || "buyer".equals(profile.getProfileType())) {
            throw new BizException(ErrorCode.MATCH_INVALID_STATE, "仅供应商可智能推荐需求");
        }

        List<RequirementRecommendVO> ranked = rankWithCache(profile);
        if (StringUtils.hasText(keyword)) {
            fillBuyerNames(ranked);
        }
        List<RequirementRecommendVO> filtered = filterKeyword(ranked, keyword);
        int total = filtered.size();
        int from = (safePage - 1) * safeSize;
        if (total == 0 || from >= total) {
            return PageResult.of(Collections.emptyList(), total, safePage, safeSize);
        }
        List<RequirementRecommendVO> page = new ArrayList<>(filtered.subList(from, Math.min(from + safeSize, total)));
        fillPlazaExtras(page, tenantId);
        return PageResult.of(page, total, safePage, safeSize);
    }

    private List<RequirementRecommendVO> rankWithCache(EnterpriseProfile profile) {
        String cacheKey = SystemConstants.MATCH_PLAZA_REQ_CACHE_PREFIX
                + profile.getTenantId() + ":" + profileFingerprint(profile);
        List<RequirementRecommendVO> cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }
        List<RequirementMatchFieldsDTO> recalled = recallRequirements(profile);
        List<RequirementRecommendVO> ranked = scoreWithPython(profile, recalled);
        writeCache(cacheKey, ranked);
        return ranked;
    }

    private List<RequirementMatchFieldsDTO> recallRequirements(EnterpriseProfile profile) {
        List<String> categoryTokens = MatchCandidateRecall.splitTokens(profile.getSupCategories());
        String region = StringUtils.hasText(profile.getProvince())
                ? profile.getProvince().trim()
                : (StringUtils.hasText(profile.getCity()) ? profile.getCity().trim() : null);

        List<RequirementMatchFieldsDTO> merged;
        if (!categoryTokens.isEmpty() && region != null) {
            List<RequirementMatchFieldsDTO> byCat = recallOne(profile.getTenantId(), categoryTokens, null);
            List<RequirementMatchFieldsDTO> byRegion = recallOne(profile.getTenantId(), List.of(), region);
            merged = mergeById(byCat, byRegion);
        } else {
            merged = recallOne(profile.getTenantId(), categoryTokens, region);
        }
        if (merged.isEmpty() && (!categoryTokens.isEmpty() || region != null)) {
            merged = recallOne(profile.getTenantId(), List.of(), null);
        }
        return merged;
    }

    private List<RequirementMatchFieldsDTO> recallOne(Long excludeTenantId, List<String> tokens, String region) {
        R<List<RequirementMatchFieldsDTO>> res = requirementClient.recallOpenForMatch(
                RequirementRecallQueryDTO.builder()
                        .excludeTenantId(excludeTenantId)
                        .categoryTokens(tokens)
                        .region(region)
                        .limit(RECALL_LIMIT)
                        .build());
        List<RequirementMatchFieldsDTO> data = res == null ? null : res.getData();
        return data == null ? List.of() : data;
    }

    private List<RequirementMatchFieldsDTO> mergeById(List<RequirementMatchFieldsDTO> primary,
                                                      List<RequirementMatchFieldsDTO> secondary) {
        Map<Long, RequirementMatchFieldsDTO> merged = new LinkedHashMap<>();
        for (RequirementMatchFieldsDTO row : primary) {
            if (row != null && row.getId() != null) {
                merged.putIfAbsent(row.getId(), row);
            }
        }
        for (RequirementMatchFieldsDTO row : secondary) {
            if (merged.size() >= RECALL_LIMIT) {
                break;
            }
            if (row != null && row.getId() != null) {
                merged.putIfAbsent(row.getId(), row);
            }
        }
        return new ArrayList<>(merged.values());
    }

    @SuppressWarnings("unchecked")
    private List<RequirementRecommendVO> scoreWithPython(EnterpriseProfile profile,
                                                         List<RequirementMatchFieldsDTO> recalled) {
        Map<Long, RequirementMatchFieldsDTO> byId = new LinkedHashMap<>();
        for (RequirementMatchFieldsDTO row : recalled) {
            byId.put(row.getId(), row);
        }
        List<Map<String, Object>> candidates = recalled.stream().map(this::toCandidate).toList();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("profile", toProfilePayload(profile));
            body.put("candidates", candidates);
            Map<String, Object> response = restClient.post()
                    .uri("/api/match-requirements")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            List<Map<String, Object>> results = response == null ? List.of()
                    : (List<Map<String, Object>>) response.get("results");
            if (results == null || results.isEmpty()) {
                log.warn("找需求 Python 返回空结果，回退本地打分: tenantId={}", profile.getTenantId());
                return scoreLocally(profile, recalled);
            }
            List<RequirementRecommendVO> out = new ArrayList<>();
            for (Map<String, Object> item : results) {
                Object idRaw = item.get("requirement_id");
                Object scoreRaw = item.get("score");
                if (!(idRaw instanceof Number) || !(scoreRaw instanceof Number)) {
                    continue;
                }
                Long reqId = ((Number) idRaw).longValue();
                RequirementMatchFieldsDTO fields = byId.get(reqId);
                if (fields == null) {
                    fields = hydrateOpen(reqId, profile.getTenantId());
                }
                if (fields == null) {
                    continue;
                }
                RequirementRecommendVO vo = RequirementRecommendVO.fromFields(fields);
                vo.setMatchScore(BigDecimal.valueOf(((Number) scoreRaw).doubleValue())
                        .setScale(1, RoundingMode.HALF_UP));
                out.add(vo);
            }
            if (out.isEmpty()) {
                log.warn("找需求 Python 结果无法回填，回退本地打分: tenantId={}", profile.getTenantId());
                return scoreLocally(profile, recalled);
            }
            return out;
        } catch (Exception e) {
            log.warn("找需求 Python 精排失败，回退本地打分: tenantId={}, error={}",
                    profile.getTenantId(), e.getMessage());
            return scoreLocally(profile, recalled);
        }
    }

    /** Python 不可用时仍给出匹配分，避免广场「匹配度」空白 */
    private List<RequirementRecommendVO> scoreLocally(EnterpriseProfile profile,
                                                      List<RequirementMatchFieldsDTO> recalled) {
        List<RequirementRecommendVO> out = new ArrayList<>();
        for (RequirementMatchFieldsDTO req : recalled) {
            RequirementRecommendVO vo = RequirementRecommendVO.fromFields(req);
            vo.setMatchScore(computeLocalScore(profile, req));
            out.add(vo);
        }
        out.sort((a, b) -> b.getMatchScore().compareTo(a.getMatchScore()));
        return out.size() > 20 ? new ArrayList<>(out.subList(0, 20)) : out;
    }

    private BigDecimal computeLocalScore(EnterpriseProfile profile, RequirementMatchFieldsDTO req) {
        double category = jaccard(req.getCategory(), profile.getSupCategories());
        double cert = certCoverage(req.getCertRequire(), profile.getSupCerts());
        double region = regionScore(req.getRegion(), profile.getProvince(), profile.getCity());
        double completeness = profile.getCompletenessScore() == null ? 0 : profile.getCompletenessScore();
        double export = exportScore(profile.getSupExport());
        double total = category * 30
                + cert * 25
                + 50 * 0.15
                + region * 0.10
                + 50 * 0.05
                + completeness * 0.10
                + export * 0.05;
        return BigDecimal.valueOf(total).setScale(1, RoundingMode.HALF_UP);
    }

    private static double jaccard(String left, String right) {
        Set<String> a = tokens(left);
        Set<String> b = tokens(right);
        if (a.isEmpty() && b.isEmpty()) {
            return 0.5;
        }
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return union.isEmpty() ? 0 : (double) inter.size() / union.size();
    }

    private static double certCoverage(String required, String owned) {
        Set<String> need = tokens(required);
        if (need.isEmpty()) {
            return 1;
        }
        Set<String> have = tokens(owned);
        long hit = need.stream().filter(have::contains).count();
        return (double) hit / need.size();
    }

    private static double regionScore(String reqRegion, String province, String city) {
        if (!StringUtils.hasText(reqRegion) || "不限".equals(reqRegion.trim())) {
            return 50;
        }
        String region = reqRegion.trim();
        String p = province == null ? "" : province;
        String c = city == null ? "" : city;
        if (p.contains(region) || c.contains(region) || region.contains(p) || region.contains(c)) {
            return 100;
        }
        return 20;
    }

    private static double exportScore(String raw) {
        try {
            int level = raw == null ? 0 : Integer.parseInt(raw.trim());
            return switch (level) {
                case 1 -> 25;
                case 2 -> 50;
                case 3 -> 75;
                case 4 -> 100;
                default -> 0;
            };
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static Set<String> tokens(String text) {
        if (!StringUtils.hasText(text)) {
            return Set.of();
        }
        return Arrays.stream(text.split("[,，、;；\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private RequirementMatchFieldsDTO hydrateOpen(Long requirementId, Long excludeTenantId) {
        R<RequirementMatchFieldsDTO> res = requirementClient.getMatchFields(requirementId);
        RequirementMatchFieldsDTO fields = res == null ? null : res.getData();
        if (fields == null || !RequirementStatus.OPEN.getCode().equals(fields.getStatus())) {
            return null;
        }
        if (Boolean.FALSE.equals(fields.getIsPublic())) {
            return null;
        }
        if (excludeTenantId != null && excludeTenantId.equals(fields.getTenantId())) {
            return null;
        }
        return fields;
    }

    private Map<String, Object> toProfilePayload(EnterpriseProfile p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("company_name", nullToEmpty(p.getCompanyName()));
        m.put("sup_categories", nullToEmpty(p.getSupCategories()));
        m.put("sup_certs", nullToEmpty(p.getSupCerts()));
        m.put("sup_export", p.getSupExport() == null ? "0" : p.getSupExport());
        m.put("completeness_score", p.getCompletenessScore() == null ? 0 : p.getCompletenessScore());
        m.put("main_products", nullToEmpty(p.getMainProducts()));
        m.put("industry", nullToEmpty(p.getIndustry()));
        m.put("description", nullToEmpty(p.getDescription()));
        m.put("province", nullToEmpty(p.getProvince()));
        m.put("city", nullToEmpty(p.getCity()));
        return m;
    }

    private Map<String, Object> toCandidate(RequirementMatchFieldsDTO r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("requirement_id", r.getId());
        m.put("title", nullToEmpty(r.getTitle()));
        m.put("category", nullToEmpty(r.getCategory()));
        m.put("cert_require", nullToEmpty(r.getCertRequire()));
        m.put("region", nullToEmpty(r.getRegion()));
        m.put("description", nullToEmpty(r.getDescription()));
        return m;
    }

    private void fillPlazaExtras(List<RequirementRecommendVO> page, Long supplierTenantId) {
        if (page.isEmpty()) {
            return;
        }
        fillBuyerNames(page);
        List<Long> reqIds = page.stream().map(RequirementRecommendVO::getId).toList();
        Map<Long, Long> countMap = new HashMap<>();
        for (ConnectionCountRow row : connectionMapper.countAcceptedByRequirementIds(reqIds)) {
            countMap.put(row.getRequirementId(), row.getCnt());
        }
        Map<Long, Connection> mine = new HashMap<>();
        for (Connection c : connectionMapper.batchSelectByRequirementIds(supplierTenantId, reqIds)) {
            mine.put(c.getRequirementId(), c);
        }
        for (RequirementRecommendVO vo : page) {
            Long cnt = countMap.get(vo.getId());
            vo.setConnectionCount(cnt == null ? 0 : cnt.intValue());
            Connection my = mine.get(vo.getId());
            if (my != null) {
                vo.setMyConnectionStatus(my.getStatus());
                if (ConnectionStatus.REJECTED.getCode().equals(my.getStatus())) {
                    vo.setMyRejectReason(my.getReplyMessage());
                }
            }
        }
    }

    private void fillBuyerNames(List<RequirementRecommendVO> rows) {
        List<Long> buyerIds = rows.stream()
                .map(RequirementRecommendVO::getTenantId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (buyerIds.isEmpty()) {
            return;
        }
        Map<Long, String> names = new HashMap<>();
        for (EnterpriseProfile p : enterpriseProfileMapper.selectByTenantIds(buyerIds)) {
            names.put(p.getTenantId(), p.getCompanyName());
        }
        for (RequirementRecommendVO vo : rows) {
            if (!StringUtils.hasText(vo.getBuyerCompanyName())) {
                vo.setBuyerCompanyName(names.get(vo.getTenantId()));
            }
        }
    }

    private List<RequirementRecommendVO> filterKeyword(List<RequirementRecommendVO> ranked, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return ranked;
        }
        String kw = keyword.trim().toLowerCase();
        return ranked.stream()
                .filter(vo -> contains(vo.getTitle(), kw)
                        || contains(vo.getCategory(), kw)
                        || contains(vo.getRegion(), kw)
                        || contains(vo.getCertRequire(), kw)
                        || contains(vo.getBuyerCompanyName(), kw))
                .toList();
    }

    private boolean contains(String value, String kw) {
        return value != null && value.toLowerCase().contains(kw);
    }

    private String profileFingerprint(EnterpriseProfile p) {
        return String.valueOf(Objects.hash(
                p.getSupCategories(), p.getSupCerts(), p.getProvince(), p.getCity(),
                p.getMainProducts(), p.getDescription()));
    }

    private List<RequirementRecommendVO> readCache(String key) {
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (!(cached instanceof List<?> raw) || raw.isEmpty()) {
                return cached instanceof List<?> empty && empty.isEmpty() ? List.of() : null;
            }
            List<RequirementRecommendVO> result = new ArrayList<>();
            for (Object item : raw) {
                if (item instanceof RequirementRecommendVO vo) {
                    result.add(vo);
                } else if (item instanceof Map<?, ?>) {
                    result.add(objectMapper.convertValue(item, RequirementRecommendVO.class));
                } else {
                    return null;
                }
            }
            if (result.stream().anyMatch(vo -> vo.getMatchScore() == null)) {
                return null;
            }
            return result;
        } catch (Exception e) {
            log.warn("读取找需求缓存失败, key={}", key, e);
            return null;
        }
    }

    private void writeCache(String key, List<RequirementRecommendVO> list) {
        try {
            redisTemplate.opsForValue().set(key, new ArrayList<>(list),
                    Duration.ofSeconds(SystemConstants.MATCH_RECOMMEND_CACHE_TTL));
        } catch (Exception e) {
            log.warn("写找需求缓存失败, key={}", key, e);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
