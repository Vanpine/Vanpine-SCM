package cn.greensmartplanet.gsc.trade.projection;

import lombok.Data;

/**
 * 询盘有效报价数投影，用于列表页批量回填每个询盘的报价数。
 * <p>
 * 对应 QuoteMapper.countByInquiryIds，SQL 只统计 status=pending 的有效报价。
 *
 * @author gsc
 */
@Data
public class QuoteCountRow {

    /** 询盘 ID */
    private Long inquiryId;

    /** 该询盘下 pending 状态的报价数量 */
    private Long quoteCount;
}
