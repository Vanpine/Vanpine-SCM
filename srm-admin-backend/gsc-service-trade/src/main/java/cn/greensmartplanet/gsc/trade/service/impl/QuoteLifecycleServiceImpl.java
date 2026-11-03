package cn.greensmartplanet.gsc.trade.service.impl;

import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.trade.dto.CreateQuoteDTO;
import cn.greensmartplanet.gsc.trade.entity.Inquiry;
import cn.greensmartplanet.gsc.trade.entity.InquiryInvite;
import cn.greensmartplanet.gsc.trade.entity.Notification;
import cn.greensmartplanet.gsc.trade.entity.Quote;
import cn.greensmartplanet.gsc.trade.enums.InquiryStatus;
import cn.greensmartplanet.gsc.trade.enums.InviteStatus;
import cn.greensmartplanet.gsc.trade.enums.QuoteStatus;
import cn.greensmartplanet.gsc.trade.mapper.InquiryInviteMapper;
import cn.greensmartplanet.gsc.trade.mapper.InquiryMapper;
import cn.greensmartplanet.gsc.trade.mapper.QuoteMapper;
import cn.greensmartplanet.gsc.trade.service.NotificationService;
import cn.greensmartplanet.gsc.trade.service.QuoteLifecycleService;
import cn.greensmartplanet.gsc.trade.support.ProfileNameHelper;
import cn.greensmartplanet.gsc.trade.support.TradeNoGenerator;
import cn.greensmartplanet.gsc.trade.vo.QuoteVO;
import cn.greensmartplanet.gsc.framework.oss.OssService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 报价生命周期服务实现 — 提交 / 撤回报价（写操作）
 * <p>
 * 提交报价校验链（红线 2 已覆盖）：
 * 询盘存在且状态 open/quoting → 报价未截止 → 币种锁定 → 供应商已接受邀请 → 已中标不可改 →
 * pending 原地更新或新建 → 首次报价询盘 open→quoting → 通知采购方。
 * 报价编号统一走 TradeNoGenerator，企业名走 ProfileNameHelper。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuoteLifecycleServiceImpl implements QuoteLifecycleService {

    private final QuoteMapper quoteMapper;
    private final InquiryMapper inquiryMapper;
    private final InquiryInviteMapper inviteMapper;
    private final TradeNoGenerator tradeNoGenerator;
    private final ProfileNameHelper profileNameHelper;
    private final OssService ossService;  // 组装 VO 时按 oss_key 现签附件 URL
    private final NotificationService notificationService;

    /**
     * 提交报价：校验询盘状态/截止时间/币种/受邀关系，已有 pending 报价原地更新，首次报价把询盘 open→quoting
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuoteVO submit(Long tenantId, Long userId, CreateQuoteDTO dto) {
        // 1. 校验询盘存在且状态允许报价（红线 2）
        Inquiry inquiry = inquiryMapper.selectById(dto.getInquiryId());
        if (inquiry == null) {
            throw new BizException(ErrorCode.PROCUREMENT_INQUIRY_NOT_FOUND);
        }
        if (!InquiryStatus.OPEN.getCode().equals(inquiry.getStatus()) && !InquiryStatus.QUOTING.getCode().equals(inquiry.getStatus())) {
            throw new BizException(ErrorCode.PROCUREMENT_STATUS_INVALID);
        }
        // 1b. 报价截止时间校验
        if (inquiry.getQuoteDeadline() != null && LocalDateTime.now().isAfter(inquiry.getQuoteDeadline())) {
            throw new BizException(ErrorCode.PROCUREMENT_STATUS_INVALID, "报价已截止");
        }
        // 1c. 币种锁定：报价币种必须与询盘一致
        if (inquiry.getCurrency() != null && dto.getCurrency() != null
                && !inquiry.getCurrency().equals(dto.getCurrency())) {
            throw new BizException(ErrorCode.PROCUREMENT_STATUS_INVALID, "报价币种必须与询盘一致：" + inquiry.getCurrency());
        }

        // 2. 校验供应商已接受邀请（必须 invite_status = accepted）
        List<InquiryInvite> invites = inviteMapper.selectByInquiryId(dto.getInquiryId());
        InquiryInvite myInvite = invites.stream()
                .filter(inv -> inv.getSupplierTenantId().equals(tenantId) && inv.getIsDeleted() == 0)
                .findFirst()
                .orElse(null);
        if (myInvite == null) {
            throw new BizException(ErrorCode.PROCUREMENT_NOT_INVITED);
        }
        if (!InviteStatus.ACCEPTED.getCode().equals(myInvite.getInviteStatus())) {
            throw new BizException(ErrorCode.PROCUREMENT_STATUS_INVALID);
        }

        // 3. 已有 pending 报价 → 原地更新，保持编号不变；已中标报价不可修改
        List<Quote> existingQuotes = quoteMapper.selectByInquiryId(dto.getInquiryId(), tenantId);
        boolean alreadyWon = existingQuotes.stream()
                .anyMatch(q -> q.getIsWinning() != null && q.getIsWinning() == 1);
        if (alreadyWon) {
            throw new BizException(ErrorCode.PROCUREMENT_STATUS_INVALID, "已中标报价不可修改");
        }
        Quote existingPending = existingQuotes.stream()
                .filter(q -> QuoteStatus.PENDING.getCode().equals(q.getStatus())).findFirst().orElse(null);

        String quoteNo;
        Quote quote;
        if (existingPending != null) {
            // 3a. 更新现有报价（覆盖可修改字段，保留编号）
            existingPending.setUnitPrice(dto.getUnitPrice());
            existingPending.setTotalPrice(dto.getTotalPrice());
            existingPending.setSupplyQuantity(dto.getSupplyQuantity());
            existingPending.setCurrency(dto.getCurrency() != null ? dto.getCurrency() : "CNY");
            existingPending.setDeliveryDays(dto.getDeliveryDays());
            existingPending.setDeliveryTerms(dto.getDeliveryTerms());
            existingPending.setValidUntil(dto.getValidUntil());
            existingPending.setRemark(dto.getRemark());
            existingPending.setAttachmentKey(dto.getAttachmentKey());
            existingPending.setUpdateBy(userId);
            quoteMapper.updateFields(existingPending);
            quoteNo = existingPending.getQuoteNo();
            quote = existingPending;
            log.info("报价已更新: id={}, quoteNo={}", quote.getId(), quoteNo);
        } else {
            // 3b. 新建报价（统一编号生成器，前缀 QTE）
            quoteNo = tradeNoGenerator.generate("QTE", "quote");
            quote = Quote.builder()
                    .quoteNo(quoteNo)
                    .inquiryId(dto.getInquiryId())
                    .buyerTenantId(inquiry.getBuyerTenantId())
                    .supplierTenantId(tenantId)
                    .unitPrice(dto.getUnitPrice())
                    .totalPrice(dto.getTotalPrice())
                    .supplyQuantity(dto.getSupplyQuantity())
                    .currency(dto.getCurrency() != null ? dto.getCurrency() : "CNY")
                    .deliveryDays(dto.getDeliveryDays())
                    .deliveryTerms(dto.getDeliveryTerms())
                    .validUntil(dto.getValidUntil())
                    .remark(dto.getRemark())
                    .attachmentKey(dto.getAttachmentKey())
                    .status(QuoteStatus.PENDING.getCode())
                    .submittedAt(LocalDateTime.now())
                    .createBy(userId)
                    .build();
            quoteMapper.insert(quote);
            log.info("报价提交成功: id={}, quoteNo={}, inquiryId={}, supplier={}", quote.getId(), quoteNo, dto.getInquiryId(), tenantId);
        }

        // 5. 首次报价 → 询盘状态 open→quoting
        if (InquiryStatus.OPEN.getCode().equals(inquiry.getStatus())) {
            inquiryMapper.updateStatus(inquiry.getId(), inquiry.getBuyerTenantId(), InquiryStatus.QUOTING.getCode(), userId);
        }

        // 6. 站内消息：通知采购方有新报价
        notificationService.notify(Notification.builder()
                .type("QUOTE_SUBMIT")
                .title("新报价")
                .content("【" + profileNameHelper.getCompanyName(tenantId) + "】已提交报价（询盘《" + inquiry.getTitle() + "》）")
                .receiverTenantId(inquiry.getBuyerTenantId())
                .relatedType("quote")
                .relatedId(quote.getId())
                .fromTenantId(tenantId)
                .fromUserId(userId)
                .build());
        QuoteVO vo = QuoteVO.fromEntity(quote, ossService);
        vo.setSupplierCompanyName(profileNameHelper.getCompanyName(tenantId));
        return vo;
    }

    /**
     * 撤回报价：中标或截止后不可撤回，成功后通知采购方
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long id, Long tenantId, Long userId) {
        // 1. 报价必须存在 + 供应商归属校验（红线 2）
        Quote quote = quoteMapper.selectById(id);
        if (quote == null) {
            throw new BizException(ErrorCode.PROCUREMENT_QUOTE_NOT_FOUND);
        }
        if (!tenantId.equals(quote.getSupplierTenantId())) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }
        // 2. 中标报价不可撤回（已进入合同环节）
        if (quote.getIsWinning() != null && quote.getIsWinning() == 1) {
            throw new BizException(ErrorCode.PROCUREMENT_STATUS_INVALID, "中标报价不可撤回");
        }
        // 3. 询盘存在且状态允许撤回（open/quoting）
        Inquiry inquiry = inquiryMapper.selectById(quote.getInquiryId());
        if (inquiry == null) {
            throw new BizException(ErrorCode.PROCUREMENT_INQUIRY_NOT_FOUND);
        }
        if (!InquiryStatus.OPEN.getCode().equals(inquiry.getStatus()) && !InquiryStatus.QUOTING.getCode().equals(inquiry.getStatus())) {
            throw new BizException(ErrorCode.PROCUREMENT_STATUS_INVALID);
        }
        // 4. 报价截止后不可撤回
        if (inquiry.getQuoteDeadline() != null && LocalDateTime.now().isAfter(inquiry.getQuoteDeadline())) {
            throw new BizException(ErrorCode.PROCUREMENT_STATUS_INVALID, "报价已截止，不可撤回");
        }
        // 5. 撤回（状态置 withdrawn）
        quoteMapper.withdraw(id, tenantId, userId);
        log.info("报价撤回: id={}, supplier={}", id, tenantId);

        // 6. 站内消息：通知采购方报价被撤回
        notificationService.notify(Notification.builder()
                .type("QUOTE_WITHDRAW")
                .title("报价已撤回")
                .content("【" + profileNameHelper.getCompanyName(tenantId) + "】撤回了报价（询盘《" + inquiry.getTitle() + "》）")
                .receiverTenantId(inquiry.getBuyerTenantId())
                .relatedType("quote")
                .relatedId(quote.getId())
                .fromTenantId(tenantId)
                .fromUserId(userId)
                .build());
    }
}
