package cn.greensmartplanet.gsc.trade.mapper;

import cn.greensmartplanet.gsc.trade.entity.QuoteAnalysis;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

/**
 * AI 比价分析结果 Mapper
 *
 * @author gsc
 */
public interface QuoteAnalysisMapper extends BaseMapper<QuoteAnalysis> {

    /** 按询盘ID查分析结果 */
    QuoteAnalysis selectByInquiryId(@Param("inquiryId") Long inquiryId);

    /** 插入分析结果 */
    int insert(QuoteAnalysis analysis);

    /** 按询盘ID更新分析结果 */
    int updateByInquiryId(QuoteAnalysis analysis);
}
