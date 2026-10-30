package cn.greensmartplanet.gsc.supplier.service.impl;

import cn.greensmartplanet.gsc.api.trade.dto.RequirementMatchFieldsDTO;
import cn.greensmartplanet.gsc.supplier.vo.SupplierRecommendVO;

import java.util.List;

/**
 * 智能匹配策略接口
 * <p>
 * 对一条采购需求，匹配候选供应商并打分排序。
 * 首版用规则引擎（RuleBasedMatchStrategy），v2 用 AI 语义匹配（AiMatchStrategy）。
 * <p>
 * ⚠️ 阶段 9：需求表归交易域，需求入参改契约 DTO
 * （由 RequirementClient.getMatchFields 提供，字段见 RequirementMatchFieldsDTO），
 * 不再依赖供应商域的需求实体。
 *
 * @author gsc
 */
public interface MatchStrategy {

    /**
     * 执行智能匹配
     *
     * @param requirement     采购需求匹配字段契约 DTO
     * @param needLlmReasons  true=详情页要 DeepSeek 理由；false=广场只要分，不调 LLM
     * @return 候选供应商列表（按匹配分降序）
     */
    List<SupplierRecommendVO> match(RequirementMatchFieldsDTO requirement, boolean needLlmReasons);
}
