package cn.greensmartplanet.gsc.trade.mapper;

import cn.greensmartplanet.gsc.trade.entity.Contract;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 合同 Mapper
 *
 * @author gsc
 */
public interface ContractMapper extends BaseMapper<Contract> {

    /** 采购方分页查合同 */
    List<Contract> selectByBuyer(@Param("tenantId") Long tenantId,
                                   @Param("status") String status,
                                   @Param("keyword") String keyword,
                                   @Param("offset") int offset,
                                   @Param("limit") int limit);

    /** 采购方统计 */
    long countByBuyer(@Param("tenantId") Long tenantId,
                       @Param("status") String status,
                       @Param("keyword") String keyword);

    /** 供应方分页查合同 */
    List<Contract> selectBySupplier(@Param("tenantId") Long tenantId,
                                      @Param("status") String status,
                                      @Param("keyword") String keyword,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    /** 供应方统计 */
    long countBySupplier(@Param("tenantId") Long tenantId,
                          @Param("status") String status,
                          @Param("keyword") String keyword);

    /** 按ID查单条 */
    Contract selectById(@Param("id") Long id);

    /** 插入合同 */
    int insert(Contract contract);

    /** 编辑合同（仅 draft） */
    int update(Contract contract);

    /** 更新合同状态 */
    int updateStatus(@Param("id") Long id,
                     @Param("tenantId") Long tenantId,
                     @Param("status") String status,
                     @Param("terminateReason") String terminateReason,
                     @Param("terminateType") String terminateType,
                     @Param("updateBy") Long updateBy);

    /** 按询盘ID统计合同数（防重） */
    int countByInquiryId(@Param("inquiryId") Long inquiryId);

    /** 按询盘+中标报价统计合同数（分量授标：每个中标报价各建一份合同） */
    int countByInquiryAndWinningQuote(@Param("inquiryId") Long inquiryId,
                                     @Param("winningQuoteId") Long winningQuoteId);

    /** 查某询盘下已有活跃（非终止）合同的 winning_quote_id 列表（前端禁选用） */
    List<Long> selectActiveWinningQuoteIdsByInquiryId(@Param("inquiryId") Long inquiryId);

    /** 发起合同：采购方 + draft → pending_supplier */
    int submit(@Param("id") Long id, @Param("tenantId") Long tenantId, @Param("updateBy") Long updateBy);

    /** 供应商确认条款：supplier + pending_supplier → 记 confirmed 标记 */
    int confirmSupplier(@Param("id") Long id, @Param("tenantId") Long tenantId, @Param("updateBy") Long updateBy);

    /** 供应商拒绝：supplier + pending_supplier → draft + 拒绝理由 */
    int decline(@Param("id") Long id, @Param("tenantId") Long tenantId,
                @Param("updateBy") Long updateBy, @Param("reason") String reason);

    /** 采购方撤回：buyer + pending_supplier → draft，清确认标记与拒绝理由，记撤回时间 */
    int withdraw(@Param("id") Long id, @Param("tenantId") Long tenantId, @Param("updateBy") Long updateBy);

    /** 上传盖章件：任一方 + pending_supplier/pending_confirm → pending_confirm + 上传方（存 OSS object key） */
    int uploadSign(@Param("id") Long id, @Param("tenantId") Long tenantId,
                   @Param("updateBy") Long updateBy, @Param("key") String key);

    /** 确认盖章件签署：任一方 + pending_confirm → signed + sign_date */
    int confirmSign(@Param("id") Long id, @Param("tenantId") Long tenantId, @Param("updateBy") Long updateBy);

    /** 交付域回写：signed → completed（末批验收自动完成，采购方 updateBy 取合同 buyer_tenant_id） */
    int updateCompleted(@Param("id") Long id, @Param("tenantId") Long tenantId, @Param("updateBy") Long updateBy);

    /** 按中标报价ID批量查合同（详情填充 contractId/contractStatus 用） */
    List<Contract> selectByWinningQuoteIds(@Param("winningQuoteIds") List<Long> winningQuoteIds);

    /** 按供应商租户查已签署合同（内部契约，交付域列可建交付单合同/发货排期用） */
    List<Contract> selectSignedBySupplier(@Param("supplierTenantId") Long supplierTenantId);
}
