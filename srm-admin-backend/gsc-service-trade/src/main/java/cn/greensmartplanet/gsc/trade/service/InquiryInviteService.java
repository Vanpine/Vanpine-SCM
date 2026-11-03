package cn.greensmartplanet.gsc.trade.service;

import cn.greensmartplanet.gsc.trade.entity.Inquiry;

import java.util.List;

/**
 * 询盘邀请服务接口 — 供应商响应邀请 / 采购方追加、重邀供应商
 * <p>
 * 邀请是询盘与供应商之间的桥梁：采购方批量建邀请（pending），供应商接受/拒绝（accepted/declined），
 * 采购方可在报价截止前追加供应商、重邀已拒绝的供应商。
 *
 * @author gsc
 */
public interface InquiryInviteService {

    /**
     * 供应商接受邀请：pending → accepted，站内消息通知采购方
     */
    void acceptInvite(Long inquiryId, Long inviteId, Long tenantId, Long userId);

    /**
     * 供应商拒绝邀请：pending → declined（附原因），站内消息通知采购方
     */
    void declineInvite(Long inquiryId, Long inviteId, Long tenantId, Long userId, String declineReason);

    /**
     * 追加供应商到已有询盘（仅 open/quoting 且报价未截止），过滤已受邀，通知新增供应商
     */
    void addSuppliers(Long inquiryId, Long tenantId, Long userId, List<Long> supplierTenantIds);

    /**
     * 重新邀请已拒绝的供应商：declined → pending（仅报价未截止），通知该供应商
     */
    void reinvite(Long inquiryId, Long inviteId, Long tenantId, Long userId);

    /**
     * 批量建邀请（创建询盘时调用）：校验受邀供应商合法性后批量插入，返回邀请数量
     *
     * @param inquiry           已插入的询盘（取 buyerTenantId/requirementId 做合法性校验）
     * @param supplierTenantIds 受邀供应商租户ID
     * @param userId            创建人
     */
    int batchInvite(Inquiry inquiry, List<Long> supplierTenantIds, Long userId);
}
