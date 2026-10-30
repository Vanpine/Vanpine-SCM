package cn.greensmartplanet.gsc.trade.mapper;

import cn.greensmartplanet.gsc.trade.entity.Quote;
import cn.greensmartplanet.gsc.trade.projection.QuoteCountRow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 报价 Mapper
 * <p>
 * 报价保密红线：供应商只能看自己的报价，采购方看全部。
 * 查询条件恒为 WHERE (buyer_tenant_id = :me) OR (supplier_tenant_id = :me)
 *
 * @author gsc
 */
public interface QuoteMapper extends BaseMapper<Quote> {

    /** 查某询盘下所有报价（采购方看全部 + 供应商只看自己，WHERE 条件保证隐私） */
    List<Quote> selectByInquiryId(@Param("inquiryId") Long inquiryId,
                                    @Param("tenantId") Long tenantId);

    /** 按ID查单条 */
    Quote selectById(@Param("id") Long id);

    /** 插入报价 */
    int insert(Quote quote);

    /** 撤回报价 */
    int withdraw(@Param("id") Long id,
                 @Param("supplierTenantId") Long supplierTenantId,
                 @Param("updateBy") Long updateBy);

    /** 原地更新报价（保持编号不变） */
    int updateFields(Quote quote);

    /** 标记中标 */
    int markWinning(@Param("id") Long id, @Param("updateBy") Long updateBy);

    /** 清除某询盘下所有中标标记 */
    int clearWinningByInquiryId(@Param("inquiryId") Long inquiryId, @Param("updateBy") Long updateBy);

    /** 批量查某供应商在多个询盘中的报价（供应商视角列表优化） */
    List<Quote> selectByInquiryIdsAndSupplier(@Param("inquiryIds") List<Long> inquiryIds,
                                              @Param("supplierTenantId") Long supplierTenantId);

    /** 批量按ID查报价 */
    List<Quote> selectByIds(@Param("ids") List<Long> ids);

    /** 批量统计各询盘的报价数（列表页回填报价数，SQL 限定 status='pending' 有效报价） */
    List<QuoteCountRow> countByInquiryIds(@Param("inquiryIds") List<Long> inquiryIds);
}
