package cn.greensmartplanet.gsc.trade.mapper;

import cn.greensmartplanet.gsc.trade.entity.InquiryInvite;
import cn.greensmartplanet.gsc.trade.projection.InviteStatusCountRow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 询盘受邀供应商 Mapper
 *
 * @author gsc
 */
public interface InquiryInviteMapper extends BaseMapper<InquiryInvite> {

    /** 按询盘ID查受邀名单 */
    List<InquiryInvite> selectByInquiryId(@Param("inquiryId") Long inquiryId);

    /** 按ID查单条 */
    InquiryInvite selectById(@Param("id") Long id);

    /** 按供应商查受邀记录 */
    List<InquiryInvite> selectBySupplierId(@Param("supplierTenantId") Long supplierTenantId,
                                             @Param("inviteStatus") String inviteStatus);

    /** 批量插入受邀供应商 */
    int insertBatch(@Param("list") List<InquiryInvite> list);

    /** 更新受邀状态（供应商响应） */
    int updateInviteStatus(@Param("id") Long id,
                           @Param("supplierTenantId") Long supplierTenantId,
                           @Param("inviteStatus") String inviteStatus,
                           @Param("declineReason") String declineReason,
                           @Param("updateBy") Long updateBy);

    /** 批量查某供应商在多个询盘中的邀请记录 */
    List<InquiryInvite> selectByInquiryIdsAndSupplier(@Param("inquiryIds") List<Long> inquiryIds,
                                                      @Param("supplierTenantId") Long supplierTenantId);

    /** 批量统计各询盘按状态分组的供应商数（pending/accepted/declined 分组计数） */
    List<InviteStatusCountRow> countByInquiryIdsGroupByStatus(@Param("inquiryIds") List<Long> inquiryIds);

    /** 重置邀请为待响应（重新邀请已拒绝供应商） */
    int resetToPending(@Param("id") Long id, @Param("updateBy") Long updateBy);

    /** 按需求ID查拒绝过询盘的供应商租户ID集合（智能匹配页"已拒绝该询盘"标注） */
    List<Long> selectDeclinedSupplierIdsByRequirement(@Param("requirementId") Long requirementId,
                                                      @Param("buyerTenantId") Long buyerTenantId);

    /** 查该采购商在该需求下已授标过的供应商租户ID集合（前端"已授标"集合，禁选用） */
    List<Long> selectInvitedSupplierIdsByBuyer(@Param("buyerTenantId") Long buyerTenantId,
                                               @Param("requirementId") Long requirementId);

    /** 查这批供应商中在该需求下已授标过的（创建询盘/追加供应商防重复校验） */
    List<Long> selectInvitedSupplierIdsByBuyerAndSuppliers(@Param("buyerTenantId") Long buyerTenantId,
                                                           @Param("requirementId") Long requirementId,
                                                           @Param("supplierTenantIds") List<Long> supplierTenantIds);

    /** 查该需求下进行中询盘（open/quoting）已邀请的供应商租户ID（前端"询价中"禁选用） */
    List<Long> selectActiveInquirySupplierIdsByRequirement(@Param("buyerTenantId") Long buyerTenantId,
                                                           @Param("requirementId") Long requirementId);

    /** 查这批供应商中在该需求下进行中询盘已邀请的（创建询盘/追加供应商防重复校验） */
    List<Long> selectActiveInquirySupplierIdsByRequirementAndSuppliers(@Param("buyerTenantId") Long buyerTenantId,
                                                                       @Param("requirementId") Long requirementId,
                                                                       @Param("supplierTenantIds") List<Long> supplierTenantIds);
}
