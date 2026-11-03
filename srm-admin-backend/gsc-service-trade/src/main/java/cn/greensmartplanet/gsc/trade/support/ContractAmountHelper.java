package cn.greensmartplanet.gsc.trade.support;

import cn.greensmartplanet.gsc.trade.entity.InquiryAward;
import cn.greensmartplanet.gsc.trade.entity.Quote;
import cn.greensmartplanet.gsc.trade.mapper.InquiryAwardMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 合同金额推导组件 — 由中标报价推导合同金额
 * <p>
 * 抽离自 ContractServiceImpl 的私有方法 deriveContractAmount / selectAwardByQuoteId，
 * 供 合同创建（生命周期服务）与 详情展示（查询服务）共用，消除重复实现。
 *
 * @author gsc
 */
@Component
@RequiredArgsConstructor
public class ContractAmountHelper {

    private final InquiryAwardMapper inquiryAwardMapper;

    /**
     * 推导合同金额：优先 单价 × 授标数量；无授标明细或无单价时 fallback 报价总价
     */
    public BigDecimal deriveContractAmount(Quote quote) {
        // 1. 查该报价的授标数量（分量授标场景）
        InquiryAward award = selectAwardByQuoteId(quote.getId());
        if (award != null && award.getAwardedQuantity() != null) {
            if (quote.getUnitPrice() != null) {
                return quote.getUnitPrice().multiply(BigDecimal.valueOf(award.getAwardedQuantity()));
            }
        }
        // 2. fallback：报价总价
        return quote.getTotalPrice();
    }

    /**
     * 按中标报价查授标明细
     * <p>
     * MP 等价 SQL：
     * SELECT id, inquiry_id, quote_id, supplier_tenant_id, awarded_quantity, awarded_by, awarded_at,
     *        create_time, update_time
     * FROM inquiry_award
     * WHERE quote_id = #{quoteId};
     * <p>
     * 业务约束：一条报价最多对应一条授标明细，因此使用 selectOne。
     */
    public InquiryAward selectAwardByQuoteId(Long quoteId) {
        return inquiryAwardMapper.selectOne(
                new LambdaQueryWrapper<InquiryAward>().eq(InquiryAward::getQuoteId, quoteId));
    }
}
