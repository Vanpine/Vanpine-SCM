package cn.greensmartplanet.gsc.trade.mapper;

import cn.greensmartplanet.gsc.trade.entity.Inquiry;
import cn.greensmartplanet.gsc.trade.projection.ActiveConnectionInquiryRow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 询盘 Mapper
 *
 * @author gsc
 */
public interface InquiryMapper extends BaseMapper<Inquiry> {

    /**
     * 采购方分页查自己发起的询盘
     */
    List<Inquiry> selectByBuyer(@Param("tenantId") Long tenantId,
                                @Param("status") String status,
                                @Param("keyword") String keyword,
                                @Param("offset") int offset,
                                @Param("limit") int limit);

    /**
     * 采购方统计
     */
    long countByBuyer(@Param("tenantId") Long tenantId,
                      @Param("status") String status,
                      @Param("keyword") String keyword);

    /**
     * 供应商分页查受邀询盘（JOIN inquiry_invite）
     */
    List<Inquiry> selectBySupplier(@Param("tenantId") Long tenantId,
                                   @Param("status") String status,
                                   @Param("keyword") String keyword,
                                   @Param("offset") int offset,
                                   @Param("limit") int limit);

    /**
     * 供应商统计
     */
    long countBySupplier(@Param("tenantId") Long tenantId,
                         @Param("status") String status,
                         @Param("keyword") String keyword);

    /**
     * 按ID查单条
     */
    Inquiry selectById(@Param("id") Long id);

    /**
     * 插入询盘
     */
    int insert(Inquiry inquiry);

    /**
     * 更新状态
     */
    int updateStatus(@Param("id") Long id,
                     @Param("tenantId") Long tenantId,
                     @Param("status") String status,
                     @Param("updateBy") Long updateBy);

    /**
     * 过期扫描：open / quoting 且 deadline 已过 → expired
     */
    int updateExpired();

    /**
     * 更新中标信息
     */
    int updateAward(@Param("id") Long id,
                    @Param("tenantId") Long tenantId,
                    @Param("winningQuoteId") Long winningQuoteId,
                    @Param("awardedBy") Long awardedBy,
                    @Param("awardReason") String awardReason);

    /**
     * 编辑询盘
     */
    int update(Inquiry inquiry);

    /**
     * 部分更新询盘（AI 工具 update_inquiry 用，只 set 非 null 字段，防清空）
     */
    int updateForAi(Inquiry inquiry);

    /**
     * 批量查哪些对接已有活跃询盘（open/quoting/awarded），按 对接ID → 询盘状态 归并
     * <p>
     * MP 等价 SQL：
     * SELECT DISTINCT c.id AS connectionId, i.id AS inquiryId, i.status AS inquiryStatus
     * FROM connection c
     * JOIN inquiry_invite ii ON ii.supplier_tenant_id = c.supplier_tenant_id AND ii.is_deleted = 0
     * JOIN inquiry i ON i.id = ii.inquiry_id AND i.is_deleted = 0
     * WHERE c.id IN (...) AND c.buyer_tenant_id = #{buyerTenantId}
     *   AND i.buyer_tenant_id = #{buyerTenantId} AND i.requirement_id = c.requirement_id
     *   AND i.status IN ('open', 'quoting', 'awarded')
     */
    List<ActiveConnectionInquiryRow> selectActiveByConnectionIds(@Param("connectionIds") List<Long> connectionIds,
                                                                 @Param("buyerTenantId") Long buyerTenantId);

    /**
     * 延长报价截止时间
     */
    int updateDeadline(@Param("id") Long id, @Param("quoteDeadline") java.time.LocalDateTime quoteDeadline, @Param("updateBy") Long updateBy);

    /**
     * 查某采购商下指定标题的进行中询盘（open/quoting，创建防重用）
     */
    List<Inquiry> selectActiveByBuyerAndTitle(@Param("tenantId") Long tenantId, @Param("title") String title);
}
