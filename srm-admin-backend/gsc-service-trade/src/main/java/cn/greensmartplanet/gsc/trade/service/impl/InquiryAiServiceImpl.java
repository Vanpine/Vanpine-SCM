package cn.greensmartplanet.gsc.trade.service.impl;

import cn.greensmartplanet.gsc.api.supplier.ProfileClient;
import cn.greensmartplanet.gsc.api.supplier.dto.ProfileInfoDTO;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.trade.entity.Inquiry;
import cn.greensmartplanet.gsc.trade.entity.Quote;
import cn.greensmartplanet.gsc.trade.entity.QuoteAnalysis;
import cn.greensmartplanet.gsc.trade.enums.QuoteStatus;
import cn.greensmartplanet.gsc.trade.mapper.InquiryMapper;
import cn.greensmartplanet.gsc.trade.mapper.QuoteAnalysisMapper;
import cn.greensmartplanet.gsc.trade.mapper.QuoteMapper;
import cn.greensmartplanet.gsc.trade.service.InquiryAiService;
import cn.greensmartplanet.gsc.trade.support.ProfileNameHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 询盘 AI 服务实现 — 调用 Python AI 服务完成比价分析/授标理由/询盘内容辅助
 * <p>
 * 敏感数据防线（红线 2）：AI 结果含全部报价排名与授标建议，属采购方决策数据，
 * analyzeQuotes / getAiAnalysis / generateAwardReason 必须校验调用方为采购方。
 * 报价快照（quoteSnapshot）用于 GET 缓存时动态判断分析结果是否过期。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InquiryAiServiceImpl implements InquiryAiService {

    private final InquiryMapper inquiryMapper;
    private final QuoteMapper quoteMapper;
    private final QuoteAnalysisMapper quoteAnalysisMapper;
    // 画像归供应商域，Feign 查询（供应商完整度评分 + 企业名）
    private final ProfileClient profileClient;
    private final ProfileNameHelper profileNameHelper;
    // AI 服务专用 HTTP 客户端（RestClientConfig 预设 baseUrl）
    private final RestClient aiRestClient;
    private final ObjectMapper objectMapper;

    /**
     * AI 综合比价分析：组装询盘+报价数据 → 调 AI 服务 → 替换真实供应商名 → 持久化结果
     */
    @Override
    public Map<String, Object> analyzeQuotes(Long inquiryId, Long tenantId) {
        // 1. 询盘存在 + 采购方归属校验（红线 2）
        Inquiry inquiry = inquiryMapper.selectById(inquiryId);
        if (inquiry == null) throw new BizException(ErrorCode.PROCUREMENT_INQUIRY_NOT_FOUND);
        if (!tenantId.equals(inquiry.getBuyerTenantId())) throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);

        // 2. 取有效报价（排除已撤回）
        List<Quote> quotes = quoteMapper.selectByInquiryId(inquiryId, tenantId);
        List<Quote> activeQuotes = quotes.stream().filter(q -> !QuoteStatus.WITHDRAWN.getCode().equals(q.getStatus())).toList();
        if (activeQuotes.isEmpty()) throw new BizException(ErrorCode.PROCUREMENT_STATUS_INVALID, "暂无有效报价");

        // 3. 批量查供应商画像完整度评分（画像归供应商域，Feign 批量查询）
        List<Long> supplierIds = activeQuotes.stream().map(Quote::getSupplierTenantId).distinct().toList();
        Map<Long, ProfileInfoDTO> profileMap = profileClient.getByTenantIds(supplierIds).getData();
        Map<Long, Integer> completenessMap = new LinkedHashMap<>();
        for (Long sid : supplierIds) {
            ProfileInfoDTO p = profileMap.get(sid);
            completenessMap.put(sid, p != null && p.getCompletenessScore() != null ? p.getCompletenessScore() : 0);
        }

        // 4. 组装 AI 入参并调用 Python 服务
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inquiry", buildInquiryData(inquiry));
        body.put("quotes", activeQuotes.stream().map(q -> buildQuoteData(q, completenessMap)).toList());

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = aiRestClient.post()
                    .uri("/api/procurement/analyze-quotes")
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            // 4a. 强制替换真实供应商名称（防 AI 编造"供应商1"等占位名）
            replaceSupplierNames(result, activeQuotes);

            // 4b. 持久化分析结果 + 报价快照（下次 GET 时比对判断是否过期）
            String resultJson = objectMapper.writeValueAsString(result);
            String snapshot = activeQuotes.stream().map(q -> String.valueOf(q.getId())).sorted().collect(Collectors.joining(","));
            QuoteAnalysis existing = quoteAnalysisMapper.selectByInquiryId(inquiryId);
            QuoteAnalysis analysis = existing != null ? existing : new QuoteAnalysis();
            analysis.setInquiryId(inquiryId);
            analysis.setAnalysisResult(resultJson);
            analysis.setQuoteSnapshot(snapshot);
            analysis.setAnalyzedAt(LocalDateTime.now());
            if (existing != null) quoteAnalysisMapper.updateByInquiryId(analysis);
            else quoteAnalysisMapper.insert(analysis);
            return result;
        } catch (Exception e) {
            log.error("AI比价分析失败: inquiryId={}", inquiryId, e);
            throw new BizException(ErrorCode.PROCUREMENT_AI_ANALYSIS_FAILED);
        }
    }

    /**
     * 取 AI 比价分析缓存：采购方归属校验（红线 2），比对报价快照判断是否过期
     */
    @Override
    public Map<String, Object> getAiAnalysis(Long inquiryId, Long tenantId) {
        // 1. 询盘存在 + 采购方归属校验（红线 2：AI 结果含全部报价对比，仅供采购方）
        Inquiry inquiry = inquiryMapper.selectById(inquiryId);
        if (inquiry == null) throw new BizException(ErrorCode.PROCUREMENT_INQUIRY_NOT_FOUND);
        if (!tenantId.equals(inquiry.getBuyerTenantId())) throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);

        // 2. 无缓存分析直接返回空
        QuoteAnalysis cached = quoteAnalysisMapper.selectByInquiryId(inquiryId);
        if (cached == null) return Collections.emptyMap();

        // 3. 比对当前报价快照，判断缓存是否过期（报价集合变化即失效）
        List<Quote> quotes = quoteMapper.selectByInquiryId(inquiryId, tenantId);
        List<Quote> active = quotes.stream().filter(q -> !QuoteStatus.WITHDRAWN.getCode().equals(q.getStatus())).toList();
        String currentSnapshot = active.stream().map(q -> String.valueOf(q.getId())).sorted().collect(Collectors.joining(","));
        boolean stale = !currentSnapshot.equals(cached.getQuoteSnapshot());

        // 4. 解析缓存 JSON + 替换真实供应商名，解析失败降级返回空分析
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> analysis = objectMapper.readValue(cached.getAnalysisResult(), Map.class);
            replaceSupplierNames(analysis, active);
            result.put("analysis", analysis);
        } catch (Exception e) {
            log.warn("AI分析缓存解析失败，降级返回空: inquiryId={}", inquiryId, e);
            result.put("analysis", null);
        }
        result.put("stale", stale);
        result.put("analyzedAt", cached.getAnalyzedAt() != null ? cached.getAnalyzedAt().toString() : null);
        return result;
    }

    /**
     * AI 授标理由生成：采购方归属校验 + quoteId 必须属于该询盘（红线 2）
     */
    @Override
    public String generateAwardReason(Long inquiryId, Long quoteId, Long tenantId) {
        // 1. 询盘存在 + 采购方归属校验（红线 2）
        Inquiry inquiry = inquiryMapper.selectById(inquiryId);
        if (inquiry == null) throw new BizException(ErrorCode.PROCUREMENT_INQUIRY_NOT_FOUND);
        if (!tenantId.equals(inquiry.getBuyerTenantId())) throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);

        // 2. 中标报价存在 + 必须属于该询盘（红线 2：关联 ID 校验归属）
        Quote winning = quoteMapper.selectById(quoteId);
        if (winning == null) throw new BizException(ErrorCode.PROCUREMENT_QUOTE_NOT_FOUND);
        if (!inquiryId.equals(winning.getInquiryId())) {
            throw new BizException(ErrorCode.PROCUREMENT_QUOTE_NOT_FOUND);
        }

        // 3. 组装对比数据：中标报价 + 其余有效报价
        List<Quote> allQuotes = quoteMapper.selectByInquiryId(inquiryId, tenantId);
        List<Quote> others = allQuotes.stream()
                .filter(q -> !q.getId().equals(quoteId) && !QuoteStatus.WITHDRAWN.getCode().equals(q.getStatus())).toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inquiry", buildInquiryData(inquiry));
        Map<String, Object> winnerData = buildQuoteData(winning, Collections.emptyMap());
        body.put("winningQuote", winnerData);
        body.put("otherQuotes", others.stream().map(q -> buildQuoteData(q, Collections.emptyMap())).toList());

        // 4. 调用 AI 服务生成授标理由，只取 reason 文本
        try {
            String raw = aiRestClient.post()
                    .uri("/api/procurement/award-reason")
                    .body(body)
                    .retrieve()
                    .body(String.class);
            // Python 返回 {"reason":"..."}，只取 reason 字段；取不到则原样返回
            Map<String, Object> result = objectMapper.readValue(raw, Map.class);
            Object reasonObj = result.get("reason");
            return reasonObj != null ? reasonObj.toString() : raw;
        } catch (Exception e) {
            log.error("AI授标理由生成失败: inquiryId={}, quoteId={}", inquiryId, quoteId, e);
            throw new BizException(ErrorCode.PROCUREMENT_AI_ANALYSIS_FAILED);
        }
    }

    /**
     * AI 询盘内容辅助：根据品类推荐规格模板和付款条件（无资源归属，仅参数透传）
     */
    @Override
    public Map<String, Object> suggestInquiryFields(String title, String category, String quantity, String unit) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("title", title != null ? title : "");
            body.put("category", category);
            body.put("quantity", quantity != null ? quantity : "");
            body.put("unit", unit != null ? unit : "");
            @SuppressWarnings("unchecked")
            Map<String, Object> result = aiRestClient.post()
                    .uri("/api/procurement/inquiry-suggest")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return result != null ? result : Collections.emptyMap();
        } catch (Exception e) {
            log.error("AI询盘辅助失败: category={}", category, e);
            throw new BizException(ErrorCode.PROCUREMENT_AI_ANALYSIS_FAILED);
        }
    }

    /**
     * 询盘实体 → AI 入参 Map（保留关键字段，空值降级为空串）
     */
    private Map<String, Object> buildInquiryData(Inquiry inquiry) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("inquiryNo", inquiry.getInquiryNo());
        data.put("title", inquiry.getTitle());
        data.put("category", inquiry.getCategory());
        data.put("quantity", inquiry.getQuantity() != null ? inquiry.getQuantity().toPlainString() : "");
        data.put("unit", inquiry.getUnit() != null ? inquiry.getUnit() : "");
        data.put("deliveryPlace", inquiry.getDeliveryPlace() != null ? inquiry.getDeliveryPlace() : "");
        data.put("deliveryDate", inquiry.getDeliveryDate() != null ? inquiry.getDeliveryDate().toString() : null);
        data.put("quoteDeadline", inquiry.getQuoteDeadline() != null ? inquiry.getQuoteDeadline().toString() : "");
        data.put("paymentTerms", inquiry.getPaymentTerms() != null ? inquiry.getPaymentTerms() : "");
        return data;
    }

    /**
     * 报价实体 → AI 入参 Map（含供应商完整度评分）
     */
    private Map<String, Object> buildQuoteData(Quote quote, Map<Long, Integer> completenessMap) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("quoteId", quote.getId());
        data.put("quoteNo", quote.getQuoteNo());
        data.put("supplierTenantId", quote.getSupplierTenantId());
        data.put("supplierCompanyName", profileNameHelper.getCompanyName(quote.getSupplierTenantId()));
        data.put("unitPrice", quote.getUnitPrice() != null ? quote.getUnitPrice() : BigDecimal.ZERO);
        data.put("totalPrice", quote.getTotalPrice() != null ? quote.getTotalPrice() : BigDecimal.ZERO);
        data.put("currency", quote.getCurrency() != null ? quote.getCurrency() : "CNY");
        data.put("deliveryDays", quote.getDeliveryDays() != null ? quote.getDeliveryDays() : 0);
        data.put("deliveryTerms", quote.getDeliveryTerms() != null ? quote.getDeliveryTerms() : "");
        data.put("validUntil", quote.getValidUntil() != null ? quote.getValidUntil().toString() : "");
        data.put("remark", quote.getRemark() != null ? quote.getRemark() : "");
        data.put("completenessScore", completenessMap.getOrDefault(quote.getSupplierTenantId(), 0));
        return data;
    }

    /**
     * 用 quoteId 映射表替换 AI 结果中的 supplierName 为真实公司名
     * <p>
     * 覆盖 rankings + risks + recommendation 三个容器；LLM 未带 quoteId 时按"供应商N"序号兜底取真实名。
     */
    @SuppressWarnings("unchecked")
    private void replaceSupplierNames(Map<String, Object> result, List<Quote> quotes) {
        // 1. 构建 quoteId → 真实公司名映射
        Map<Long, String> nameMap = new LinkedHashMap<>();
        for (Quote q : quotes) {
            String name = profileNameHelper.getCompanyName(q.getSupplierTenantId());
            if (name != null) nameMap.put(q.getId(), name);
        }
        // 2. 遍历三个容器逐项替换
        for (Map<String, Object> item : allAwardContainers(result)) {
            Long qid = parseQuoteId(item.get("quoteId"));
            if (qid != null) {
                String realName = nameMap.get(qid);
                if (realName != null) item.put("supplierName", realName);
            } else {
                // 2a. 兜底：LLM 未带 quoteId 但用了"供应商N"编号 → 按序号取真实名
                Object nameObj = item.get("supplierName");
                if (nameObj instanceof String name && name.matches("^供应商\\d+$")) {
                    try {
                        int idx = Integer.parseInt(name.substring(3)) - 1;
                        if (idx >= 0 && idx < quotes.size()) {
                            String realName = profileNameHelper.getCompanyName(quotes.get(idx).getSupplierTenantId());
                            if (realName != null) item.put("supplierName", realName);
                        }
                    } catch (NumberFormatException ignored) {
                        // 序号解析失败直接跳过，保留原样
                    }
                }
            }
        }
    }

    /**
     * 收集 rankings + risks + recommendation 三个容器（AI 结果中的供应商名单载体）
     */
    private List<Map<String, Object>> allAwardContainers(Map<String, Object> result) {
        List<Map<String, Object>> items = new ArrayList<>();
        Object rankings = result.get("rankings");
        if (rankings instanceof List) {
            for (Object o : (List<?>) rankings) {
                if (o instanceof Map) items.add((Map<String, Object>) o);
            }
        }
        Object risks = result.get("risks");
        if (risks instanceof List) {
            for (Object o : (List<?>) risks) {
                if (o instanceof Map) items.add((Map<String, Object>) o);
            }
        }
        Object rec = result.get("recommendation");
        if (rec instanceof Map) items.add((Map<String, Object>) rec);
        return items;
    }

    /**
     * 兼容 int / long / String 形式的 quoteId
     */
    private Long parseQuoteId(Object v) {
        if (v instanceof Number) return ((Number) v).longValue();
        if (v instanceof String) {
            try {
                return Long.parseLong((String) v);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
