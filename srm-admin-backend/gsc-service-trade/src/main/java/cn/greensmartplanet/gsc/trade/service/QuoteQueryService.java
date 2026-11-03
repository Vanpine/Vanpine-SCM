package cn.greensmartplanet.gsc.trade.service;

import cn.greensmartplanet.gsc.trade.vo.QuoteVO;

import java.util.List;

/**
 * 报价查询服务接口 — 询盘报价列表（只读）
 * <p>
 * 报价保密红线：供应商只能看自己的报价，采购方看全部（WHERE 条件在 Mapper 层实现）。
 *
 * @author gsc
 */
public interface QuoteQueryService {

    /**
     * 询盘报价列表（保密红线在 Mapper 层：供应商只看自己），附授标数量与合同状态
     */
    List<QuoteVO> listByInquiry(Long inquiryId, Long tenantId);
}
