package cn.greensmartplanet.gsc.trade.service.impl;

import cn.greensmartplanet.gsc.trade.entity.Contract;
import cn.greensmartplanet.gsc.trade.entity.InquiryAward;
import cn.greensmartplanet.gsc.trade.entity.Quote;
import cn.greensmartplanet.gsc.trade.mapper.ContractMapper;
import cn.greensmartplanet.gsc.trade.mapper.InquiryAwardMapper;
import cn.greensmartplanet.gsc.trade.mapper.QuoteMapper;
import cn.greensmartplanet.gsc.trade.service.QuoteQueryService;
import cn.greensmartplanet.gsc.trade.support.ProfileNameHelper;
import cn.greensmartplanet.gsc.trade.vo.QuoteVO;
import cn.greensmartplanet.gsc.framework.oss.OssService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 报价查询服务实现 — 询盘报价列表（只读）
 * <p>
 * 报价保密红线在 QuoteMapper.xml 的 WHERE 条件中实现（供应商只看自己，采购方看全部）。
 * 授标数量、合同状态、企业名均先收集 ID 批量查询再组装（红线 1，消 N+1）。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuoteQueryServiceImpl implements QuoteQueryService {

    private final QuoteMapper quoteMapper;
    private final InquiryAwardMapper inquiryAwardMapper;
    private final ContractMapper contractMapper;
    private final ProfileNameHelper profileNameHelper;
    private final OssService ossService;  // 组装 VO 时按 oss_key 现签附件 URL

    /**
     * 询盘报价列表（保密红线在 Mapper 层：供应商只看自己），附授标数量与合同状态
     */
    @Override
    public List<QuoteVO> listByInquiry(Long inquiryId, Long tenantId) {
        // 1. 报价保密红线在 QuoteMapper.xml 的 WHERE 条件中实现
        List<Quote> quotes = quoteMapper.selectByInquiryId(inquiryId, tenantId);

        // 2. 组装授标数量 map：quoteId → awardedQuantity
        Map<Long, Integer> awardQtyMap = new HashMap<>();
        List<InquiryAward> awards = inquiryAwardMapper.selectByInquiryId(inquiryId);
        if (awards != null) {
            for (InquiryAward a : awards) {
                if (a.getAwardedQuantity() != null) {
                    awardQtyMap.put(a.getQuoteId(), a.getAwardedQuantity());
                }
            }
        }

        // 3. 合同 map：winning_quote_id → contract（比价详情按行判断"是否已创建合同"）
        Map<Long, Contract> contractMap = new HashMap<>();
        List<Long> quoteIds = quotes.stream().map(Quote::getId).collect(Collectors.toList());
        if (!quoteIds.isEmpty()) {
            List<Contract> contracts = contractMapper.selectByWinningQuoteIds(quoteIds);
            if (contracts != null) {
                for (Contract c : contracts) {
                    if (c.getWinningQuoteId() != null && !contractMap.containsKey(c.getWinningQuoteId())) {
                        contractMap.put(c.getWinningQuoteId(), c);
                    }
                }
            }
        }

        // 4. 收集本页所有供应商租户ID，一次批量查企业名（消 N+1）
        Set<Long> supplierTenantIds = quotes.stream()
                .map(Quote::getSupplierTenantId)
                .collect(Collectors.toCollection(HashSet::new));
        Map<Long, String> companyNames = profileNameHelper.getCompanyNames(supplierTenantIds);

        // 5. 组装 VO（授标数量/合同状态 + 企业名从批量结果取）
        return quotes.stream().map(q -> {
            QuoteVO vo = QuoteVO.fromEntity(q, ossService);
            vo.setSupplierCompanyName(companyNames.get(q.getSupplierTenantId()));
            vo.setAwardedQuantity(awardQtyMap.get(q.getId()));
            Contract c = contractMap.get(q.getId());
            if (c != null) {
                vo.setContractId(c.getId());
                vo.setContractStatus(c.getStatus());
            }
            return vo;
        }).collect(Collectors.toList());
    }
}
