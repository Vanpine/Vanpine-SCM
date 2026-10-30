package cn.greensmartplanet.gsc.trade.service.impl;

import cn.greensmartplanet.gsc.api.supplier.ProfileClient;
import cn.greensmartplanet.gsc.api.supplier.dto.ProfileInfoDTO;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.trade.entity.Inquiry;
import cn.greensmartplanet.gsc.trade.entity.InquiryInvite;
import cn.greensmartplanet.gsc.trade.entity.Notification;
import cn.greensmartplanet.gsc.trade.enums.InquiryStatus;
import cn.greensmartplanet.gsc.trade.enums.InviteStatus;
import cn.greensmartplanet.gsc.trade.mapper.InquiryInviteMapper;
import cn.greensmartplanet.gsc.trade.mapper.InquiryMapper;
import cn.greensmartplanet.gsc.trade.service.InquiryInviteService;
import cn.greensmartplanet.gsc.trade.service.NotificationService;
import cn.greensmartplanet.gsc.trade.support.ProfileNameHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 询盘邀请服务实现
 * <p>
 * 邀请状态机：pending(待响应) → accepted(已接受) / declined(已拒绝)，
 * 采购方可在 open/quoting 且未过报价截止时追加供应商、重邀 declined 供应商。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InquiryInviteServiceImpl implements InquiryInviteService {

    private final InquiryInviteMapper inviteMapper;
    private final InquiryMapper inquiryMapper;
    // 画像归供应商域，Feign 查询（供应商身份校验 / 通知文案企业名）
    private final ProfileClient profileClient;
    private final ProfileNameHelper profileNameHelper;
    private final NotificationService notificationService;

    /**
     * 供应商接受邀请：pending → accepted，站内消息通知采购方
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void acceptInvite(Long inquiryId, Long inviteId, Long tenantId, Long userId) {
        // 1. 校验邀请记录存在、归属当前供应商、状态为 pending、询盘未关闭且未过报价截止
        getAndValidateInvite(inquiryId, inviteId, tenantId);

        // 2. 更新邀请状态 pending → accepted（记录处理人）
        inviteMapper.updateInviteStatus(inviteId, tenantId, InviteStatus.ACCEPTED.getCode(), null, userId);
        log.info("供应商接受邀请: inquiryId={}, inviteId={}, supplier={}", inquiryId, inviteId, tenantId);

        // 3. 站内消息：通知采购商供应商已接受邀请（企业名降级为空不影响主流程）
        Inquiry inquiry = inquiryMapper.selectById(inquiryId);
        if (inquiry != null) {
            notificationService.notify(Notification.builder()
                    .type("INQUIRY_INVITE_ACCEPT")
                    .title("邀请已接受")
                    .content("【" + profileNameHelper.getCompanyName(tenantId) + "】接受了您的询盘邀请（《" + inquiry.getTitle() + "》）")
                    .receiverTenantId(inquiry.getBuyerTenantId())
                    .relatedType("inquiry")
                    .relatedId(inquiryId)
                    .fromTenantId(tenantId)
                    .fromUserId(userId)
                    .build());
        }
    }

    /**
     * 供应商拒绝邀请：pending → declined（附原因），站内消息通知采购方
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void declineInvite(Long inquiryId, Long inviteId, Long tenantId, Long userId, String declineReason) {
        // 1. 校验邀请记录存在、归属当前供应商、状态为 pending、询盘未关闭且未过报价截止
        getAndValidateInvite(inquiryId, inviteId, tenantId);

        // 2. 更新邀请状态 pending → declined（记录拒绝原因与处理人）
        inviteMapper.updateInviteStatus(inviteId, tenantId, InviteStatus.DECLINED.getCode(), declineReason, userId);
        log.info("供应商拒绝邀请: inquiryId={}, inviteId={}, supplier={}", inquiryId, inviteId, tenantId);

        // 3. 站内消息：通知采购商供应商已拒绝邀请（含原因）
        Inquiry inquiry = inquiryMapper.selectById(inquiryId);
        if (inquiry != null) {
            notificationService.notify(Notification.builder()
                    .type("INQUIRY_INVITE_DECLINE")
                    .title("邀请已拒绝")
                    .content("【" + profileNameHelper.getCompanyName(tenantId) + "】拒绝了您的询盘邀请（《" + inquiry.getTitle() + "》）"
                            + (declineReason != null && !declineReason.isBlank() ? "：" + declineReason : ""))
                    .receiverTenantId(inquiry.getBuyerTenantId())
                    .relatedType("inquiry")
                    .relatedId(inquiryId)
                    .fromTenantId(tenantId)
                    .fromUserId(userId)
                    .build());
        }
    }

    /**
     * 追加供应商到已有询盘：仅 open/quoting 且未截止，过滤已受邀，通知新增供应商
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addSuppliers(Long inquiryId, Long tenantId, Long userId, List<Long> supplierTenantIds) {
        // 1. 校验询盘存在、采购方归属、状态 open/quoting、报价未截止
        Inquiry inquiry = inquiryMapper.selectById(inquiryId);
        if (inquiry == null) throw new BizException(ErrorCode.PROCUREMENT_INQUIRY_NOT_FOUND);
        if (!tenantId.equals(inquiry.getBuyerTenantId())) throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        if (!InquiryStatus.OPEN.getCode().equals(inquiry.getStatus()) && !InquiryStatus.QUOTING.getCode().equals(inquiry.getStatus()))
            throw new BizException(ErrorCode.PROCUREMENT_STATUS_INVALID);
        assertDeadlineNotPassed(inquiry, "报价已截止，无法追加供应商");

        // 2. 过滤已受邀的供应商，只对新增名单建邀请
        List<InquiryInvite> existing = inviteMapper.selectByInquiryId(inquiryId);
        Set<Long> existingIds = existing.stream().map(InquiryInvite::getSupplierTenantId).collect(Collectors.toSet());
        List<Long> newIds = supplierTenantIds.stream().filter(id -> !existingIds.contains(id)).collect(Collectors.toList());
        if (newIds.isEmpty()) return;

        // 3. 防重复校验：本公司/纯买方/该需求已授标/该需求进行中询盘已邀请 → 拒绝追加
        validateInviteSuppliers(tenantId, inquiry.getRequirementId(), newIds);

        // 4. 批量插入新增邀请（初始状态 pending）
        List<InquiryInvite> invites = new ArrayList<>();
        for (Long sid : newIds) {
            invites.add(InquiryInvite.builder()
                    .inquiryId(inquiryId).supplierTenantId(sid)
                    .inviteStatus(InviteStatus.PENDING.getCode()).invitedAt(LocalDateTime.now())
                    .createBy(userId)
                    .build());
        }
        inviteMapper.insertBatch(invites);
        log.info("追加供应商: inquiryId={}, count={}", inquiryId, newIds.size());

        // 5. 站内消息：通知每个新增供应商被邀请
        String buyerCompanyName = profileNameHelper.getCompanyName(inquiry.getBuyerTenantId());
        for (Long sid : newIds) {
            notificationService.notify(Notification.builder()
                    .type("INQUIRY_INVITE")
                    .title("新的询盘邀请")
                    .content("【" + buyerCompanyName + "】邀请您参与询盘《" + inquiry.getTitle() + "》")
                    .receiverTenantId(sid)
                    .relatedType("inquiry")
                    .relatedId(inquiryId)
                    .fromTenantId(inquiry.getBuyerTenantId())
                    .fromUserId(userId)
                    .build());
        }
    }

    /**
     * 重新邀请已拒绝的供应商：declined → pending（仅报价未截止），通知该供应商
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reinvite(Long inquiryId, Long inviteId, Long tenantId, Long userId) {
        // 1. 校验询盘存在、采购方归属、状态 open/quoting、报价未截止
        Inquiry inquiry = inquiryMapper.selectById(inquiryId);
        if (inquiry == null) throw new BizException(ErrorCode.PROCUREMENT_INQUIRY_NOT_FOUND);
        if (!tenantId.equals(inquiry.getBuyerTenantId())) throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        if (!InquiryStatus.OPEN.getCode().equals(inquiry.getStatus()) && !InquiryStatus.QUOTING.getCode().equals(inquiry.getStatus()))
            throw new BizException(ErrorCode.PROCUREMENT_STATUS_INVALID);
        assertDeadlineNotPassed(inquiry, "报价已截止，无法重新邀请");

        // 2. 校验邀请记录存在且属于该询盘、状态为 declined（只有被拒过的才能重邀）
        InquiryInvite invite = inviteMapper.selectById(inviteId);
        if (invite == null || !inquiryId.equals(invite.getInquiryId()))
            throw new BizException(ErrorCode.PROCUREMENT_INVITE_NOT_FOUND);
        if (!InviteStatus.DECLINED.getCode().equals(invite.getInviteStatus()))
            throw new BizException(ErrorCode.PROCUREMENT_STATUS_INVALID);

        // 3. 重置邀请状态 declined → pending
        inviteMapper.resetToPending(inviteId, userId);
        log.info("重新邀请: inquiryId={}, inviteId={}", inquiryId, inviteId);

        // 4. 站内消息：通知该供应商被重新邀请
        notificationService.notify(Notification.builder()
                .type("INQUIRY_REINVITE")
                .title("询盘重新邀请")
                .content("【" + profileNameHelper.getCompanyName(inquiry.getBuyerTenantId()) + "】重新邀请您参与询盘《" + inquiry.getTitle() + "》")
                .receiverTenantId(invite.getSupplierTenantId())
                .relatedType("inquiry")
                .relatedId(inquiryId)
                .fromTenantId(inquiry.getBuyerTenantId())
                .fromUserId(userId)
                .build());
    }

    /**
     * 批量建邀请（创建询盘时调用）：校验受邀供应商合法性后批量插入，返回邀请数量
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchInvite(Inquiry inquiry, List<Long> supplierTenantIds, Long userId) {
        // 1. 校验受邀供应商合法性：本公司/纯买方/该需求已授标/该需求进行中询盘已邀请
        validateInviteSuppliers(inquiry.getBuyerTenantId(), inquiry.getRequirementId(), supplierTenantIds);

        // 2. 批量插入受邀供应商（初始状态 pending）
        List<InquiryInvite> invites = new ArrayList<>();
        for (Long supplierId : supplierTenantIds) {
            invites.add(InquiryInvite.builder()
                    .inquiryId(inquiry.getId())
                    .supplierTenantId(supplierId)
                    .inviteStatus(InviteStatus.PENDING.getCode())
                    .invitedAt(LocalDateTime.now())
                    .build());
        }
        inviteMapper.insertBatch(invites);
        log.info("批量建邀请: inquiryId={}, count={}", inquiry.getId(), invites.size());
        return invites.size();
    }

    /**
     * 校验邀请记录存在、归属当前供应商、状态为 pending、询盘未关闭且未过报价截止
     */
    private InquiryInvite getAndValidateInvite(Long inquiryId, Long inviteId, Long tenantId) {
        // 1. 邀请记录必须存在
        InquiryInvite invite = inviteMapper.selectById(inviteId);
        if (invite == null || !inviteId.equals(invite.getId())) {
            throw new BizException(ErrorCode.PROCUREMENT_INVITE_NOT_FOUND);
        }
        // 2. 邀请必须归属当前供应商（不能替他人响应）
        if (!tenantId.equals(invite.getSupplierTenantId())) {
            throw new BizException(ErrorCode.PROCUREMENT_NOT_INVITED);
        }
        // 3. 邀请状态必须为 pending（已响应过不可重复操作）
        if (!InviteStatus.PENDING.getCode().equals(invite.getInviteStatus())) {
            throw new BizException(ErrorCode.PROCUREMENT_INVITE_ALREADY_RESPONDED);
        }
        // 4. 询盘必须存在且状态允许响应（open/quoting）
        Inquiry inquiry = inquiryMapper.selectById(inquiryId);
        if (inquiry == null) {
            throw new BizException(ErrorCode.PROCUREMENT_INQUIRY_NOT_FOUND);
        }
        if (!InquiryStatus.OPEN.getCode().equals(inquiry.getStatus()) && !InquiryStatus.QUOTING.getCode().equals(inquiry.getStatus())) {
            throw new BizException(ErrorCode.PROCUREMENT_STATUS_INVALID);
        }
        // 5. 报价必须未截止（截止后不可接受/拒绝邀请）
        if (inquiry.getQuoteDeadline() != null && LocalDateTime.now().isAfter(inquiry.getQuoteDeadline())) {
            throw new BizException(ErrorCode.PROCUREMENT_STATUS_INVALID, "报价已截止，无法接受或拒绝邀请");
        }
        return invite;
    }

    /**
     * 校验报价截止未过，否则抛异常（追加供应商/重邀前调用）
     */
    private void assertDeadlineNotPassed(Inquiry inquiry, String message) {
        if (inquiry.getQuoteDeadline() != null && LocalDateTime.now().isAfter(inquiry.getQuoteDeadline())) {
            throw new BizException(ErrorCode.PROCUREMENT_STATUS_INVALID, message);
        }
    }

    /**
     * 校验受邀供应商合法性：本公司 / 纯买方 / 该需求已授标 / 该需求进行中询盘已邀请
     */
    private void validateInviteSuppliers(Long buyerTenantId, Long requirementId, List<Long> supplierTenantIds) {
        if (supplierTenantIds == null || supplierTenantIds.isEmpty()) return;

        // 1. 不能邀请本公司
        if (supplierTenantIds.contains(buyerTenantId)) {
            throw new BizException(ErrorCode.PROCUREMENT_INQUIRY_SUPPLIER_ALREADY_INVITED, "不能邀请本公司报价");
        }

        // 2. 仅可邀请供应商身份（supplier / both）——画像归供应商域，Feign 批量查询消 N+1
        Map<Long, ProfileInfoDTO> profiles = profileClient.getByTenantIds(supplierTenantIds).getData();
        for (Long sid : supplierTenantIds) {
            ProfileInfoDTO p = profiles != null ? profiles.get(sid) : null;
            if (p == null || (!"supplier".equals(p.getProfileType()) && !"both".equals(p.getProfileType()))) {
                throw new BizException(ErrorCode.PROCUREMENT_INQUIRY_SUPPLIER_ALREADY_INVITED, "仅可邀请供应商报价");
            }
        }
        if (requirementId == null) return;

        // 3. 该需求下已授标过的供应商不可重复邀请
        List<Long> awarded = inviteMapper.selectInvitedSupplierIdsByBuyerAndSuppliers(buyerTenantId, requirementId, supplierTenantIds);
        if (awarded != null && !awarded.isEmpty()) {
            throw new BizException(ErrorCode.PROCUREMENT_INQUIRY_SUPPLIER_ALREADY_INVITED);
        }

        // 4. 该需求下进行中询盘（open/quoting）已邀请的供应商不可重复邀请
        List<Long> active = inviteMapper.selectActiveInquirySupplierIdsByRequirementAndSuppliers(buyerTenantId, requirementId, supplierTenantIds);
        if (active != null && !active.isEmpty()) {
            throw new BizException(ErrorCode.PROCUREMENT_INQUIRY_SUPPLIER_ALREADY_INVITED, "该供应商正在其他询盘报价中，请勿重复邀请");
        }
    }
}
