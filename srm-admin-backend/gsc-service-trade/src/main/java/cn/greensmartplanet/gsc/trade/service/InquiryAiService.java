package cn.greensmartplanet.gsc.trade.service;

import java.util.Map;

/**
 * 询盘 AI 服务接口 — AI 比价分析 / 结果缓存读取 / 授标理由生成 / 询盘内容辅助
 * <p>
 * AI 能力从询盘主服务拆出：调用方为采购方（AI 结果含全部报价对比，属采购视角敏感数据）。
 *
 * @author gsc
 */
public interface InquiryAiService {

    /**
     * AI 综合比价分析 — 分析询盘的全部报价，给出排名和授标建议，结果持久化
     */
    Map<String, Object> analyzeQuotes(Long inquiryId, Long tenantId);

    /**
     * 获取已缓存的 AI 分析结果（有就返回 + 是否过期，没有就返回空）
     */
    Map<String, Object> getAiAnalysis(Long inquiryId, Long tenantId);

    /**
     * AI 授标理由生成 — 根据中标报价和对比数据生成授标理由
     */
    String generateAwardReason(Long inquiryId, Long quoteId, Long tenantId);

    /**
     * AI 询盘内容辅助 — 根据品类推荐规格模板和付款条件
     */
    Map<String, Object> suggestInquiryFields(String title, String category, String quantity, String unit);
}
