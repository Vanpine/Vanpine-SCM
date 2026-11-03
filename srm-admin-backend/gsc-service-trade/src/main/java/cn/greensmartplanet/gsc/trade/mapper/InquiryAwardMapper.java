package cn.greensmartplanet.gsc.trade.mapper;

import cn.greensmartplanet.gsc.trade.entity.InquiryAward;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 授标明细 Mapper
 *
 * @author gsc
 */
public interface InquiryAwardMapper extends BaseMapper<InquiryAward> {

    /** 插入授标明细 */
    int insert(InquiryAward award);

    /** 按询盘ID查所有授标明细 */
    List<InquiryAward> selectByInquiryId(@Param("inquiryId") Long inquiryId);

    /** 批量查多个询盘的授标明细（采购方列表填充已授数量） */
    List<InquiryAward> selectByInquiryIds(@Param("inquiryIds") List<Long> inquiryIds);

    /** 批量查某供应商在多个询盘中的授标明细（供应商视角列表批量填充用） */
    List<InquiryAward> selectByInquiryIdsAndSupplier(@Param("inquiryIds") List<Long> inquiryIds,
                                                     @Param("supplierTenantId") Long supplierTenantId);

    /** 删除某询盘下所有授标明细（重新授标前清除旧数据） */
    int deleteByInquiryId(@Param("inquiryId") Long inquiryId);
}
