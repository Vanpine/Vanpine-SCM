package cn.greensmartplanet.gsc.trade.service.impl;

import cn.greensmartplanet.gsc.api.trade.dto.AiInquiryUpdateRequest;
import cn.greensmartplanet.gsc.common.enums.RequirementStatus;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.trade.dto.CreateInquiryDTO;
import cn.greensmartplanet.gsc.trade.entity.Inquiry;
import cn.greensmartplanet.gsc.trade.entity.InquiryAward;
import cn.greensmartplanet.gsc.trade.entity.InquiryInvite;
import cn.greensmartplanet.gsc.trade.entity.Notification;
import cn.greensmartplanet.gsc.trade.entity.ProcurementRequirement;
import cn.greensmartplanet.gsc.trade.entity.Quote;
import cn.greensmartplanet.gsc.trade.enums.InquiryStatus;
import cn.greensmartplanet.gsc.trade.enums.InviteStatus;
import cn.greensmartplanet.gsc.trade.mapper.InquiryAwardMapper;
import cn.greensmartplanet.gsc.trade.mapper.InquiryInviteMapper;
import cn.greensmartplanet.gsc.trade.mapper.InquiryMapper;
import cn.greensmartplanet.gsc.trade.mapper.ProcurementRequirementMapper;
import cn.greensmartplanet.gsc.trade.mapper.QuoteMapper;
import cn.greensmartplanet.gsc.trade.service.InquiryInviteService;
import cn.greensmartplanet.gsc.trade.service.InquiryLifecycleService;
import cn.greensmartplanet.gsc.trade.service.NotificationService;
import cn.greensmartplanet.gsc.trade.support.ProfileNameHelper;
import cn.greensmartplanet.gsc.trade.vo.InquiryVO;
import cn.greensmartplanet.gsc.framework.oss.OssService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 询盘生命周期服务实现 — 创建 / 编辑 / 关闭 / 取消 / 延长报价截止
 * <p>
 * 生命周期状态机（来自 InquiryStatus）：
 * open(待报价) → quoting(报价中) → awarded(已授标) / closed(已关闭) / expired(已过期) / cancelled(已取消)
 * <p>
 * 创建询盘委托 InquiryInviteService.batchInvite 建邀请；
 * 关闭/取消/延长截止后通过站内消息通知所有未拒绝的受邀供应商。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InquiryLifecycleServiceImpl implements InquiryLifecycleService {

    private final InquiryMapper inquiryMapper;
    private final InquiryInviteMapper inviteMapper;
    private final InquiryAwardMapper inquiryAwardMapper;
    private final QuoteMapper quoteMapper;
    private final ProcurementRequirementMapper requirementMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final InquiryInviteService inviteService;
    private final NotificationService notificationService;
    private final ProfileNameHelper profileNameHelper;
    private final OssService ossService;  // 组装 VO 时按 oss_key 现签附件 URL

    /**
     * 创建询盘：校验时间/关联需求/防重 → 插入询盘 → 批量邀请供应商（委托邀请服务），初始状态 open
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public InquiryVO create(Long tenantId, Long userId, CreateInquiryDTO dto) {
        // 1. 生成询盘编号 RFQ + 日期 + 6位Redis自增序号
        String inquiryNo = generateInquiryNo();

        // 2. 时间校验：报价截止 > 当前时间，交付日期 > 报价截止
        validateInquiryTime(dto.getQuoteDeadline(), dto.getDeliveryDate());

        // 3. 强关联采购需求：需求必须存在、归属当前租户、且为 open 状态
        validateRequirement(dto.getRequirementId(), tenantId);

        // 4. 防重：同采购商同标题已有进行中询盘（open/quoting）→ 拒绝重复创建，引导追加供应商
        if (dto.getTitle() != null && !dto.getTitle().isBlank()) {
            List<Inquiry> dup = inquiryMapper.selectActiveByBuyerAndTitle(tenantId, dto.getTitle());
            if (dup != null && !dup.isEmpty()) {
                throw new BizException(ErrorCode.PROCUREMENT_INQUIRY_DUPLICATE_TITLE);
            }
        }

        // 5. 插入询盘（初始状态 open）
        Inquiry inquiry = Inquiry.builder()
                .inquiryNo(inquiryNo)
                .requirementId(dto.getRequirementId())
                .connectionId(dto.getConnectionId())
                .buyerTenantId(tenantId)
                .title(dto.getTitle())
                .currency(dto.getCurrency() != null ? dto.getCurrency() : "CNY")
                .category(dto.getCategory())
                .quantity(dto.getQuantity())
                .unit(dto.getUnit())
                .deliveryPlace(dto.getDeliveryPlace())
                .deliveryDate(dto.getDeliveryDate())
                .quoteDeadline(dto.getQuoteDeadline())
                .paymentTerms(dto.getPaymentTerms())
                .specText(dto.getSpecText())
                .attachmentKey(dto.getAttachmentKey())
                .status(InquiryStatus.OPEN.getCode())
                .createBy(userId)
                .build();
        inquiryMapper.insert(inquiry);

        // 6. 批量邀请供应商（委托邀请服务：内含合法性校验 + 批量插邀请，返回邀请数量）
        int inviteCount = inviteService.batchInvite(inquiry, dto.getSupplierTenantIds(), userId);

        log.info("询盘创建成功: id={}, inquiryNo={}, buyer={}, 邀请 {} 家供应商",
                inquiry.getId(), inquiryNo, tenantId, inviteCount);

        // 7. 组装 VO 返回
        InquiryVO vo = InquiryVO.fromEntity(inquiry, ossService);
        vo.setSupplierCount(inviteCount);
        vo.setBuyerCompanyName(profileNameHelper.getCompanyName(tenantId));
        return vo;
    }

    /**
     * 编辑询盘：仅采购方、open 且无供应商接受邀请时可编辑
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, Long tenantId, Long userId, CreateInquiryDTO dto) {
        // 1. 询盘必须存在
        Inquiry inquiry = inquiryMapper.selectById(id);
        if (inquiry == null) {
            throw new BizException(ErrorCode.PROCUREMENT_INQUIRY_NOT_FOUND);
        }
        // 2. 仅 open 状态可编辑
        if (!InquiryStatus.OPEN.getCode().equals(inquiry.getStatus())) {
            throw new BizException(ErrorCode.PROCUREMENT_STATUS_INVALID);
        }
        // 3. 只允许采购方自己编辑（红线 2）
        if (!tenantId.equals(inquiry.getBuyerTenantId())) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }
        // 4. 已有供应商接受邀请 → 不可编辑（避免改动影响已承诺的供应商）
        List<InquiryInvite> invites = inviteMapper.selectByInquiryId(id);
        boolean hasAccepted = invites.stream().anyMatch(inv -> InviteStatus.ACCEPTED.getCode().equals(inv.getInviteStatus()));
        if (hasAccepted) {
            throw new BizException(ErrorCode.PROCUREMENT_STATUS_INVALID);
        }
        // 5. 时间校验
        validateInquiryTime(dto.getQuoteDeadline(), dto.getDeliveryDate());

        // 6. 更新可编辑字段
        inquiry.setTitle(dto.getTitle());
        inquiry.setCategory(dto.getCategory());
        inquiry.setQuantity(dto.getQuantity());
        inquiry.setUnit(dto.getUnit());
        inquiry.setDeliveryPlace(dto.getDeliveryPlace());
        inquiry.setDeliveryDate(dto.getDeliveryDate());
        inquiry.setQuoteDeadline(dto.getQuoteDeadline());
        inquiry.setPaymentTerms(dto.getPaymentTerms());
        inquiry.setSpecText(dto.getSpecText());
        inquiry.setAttachmentKey(dto.getAttachmentKey());
        inquiry.setUpdateBy(userId);
        inquiryMapper.update(inquiry);
        log.info("询盘编辑成功: id={}", id);
    }

    /**
     * 部分更新询盘字段（AI 工具 update_inquiry 用）
     * <p>
     * 校验与 update 一致（仅采购方、open、无供应商接受），但只 set 非 null 字段，
     * 防止模型只说「改交期」却把标题/品类/数量等字段清空。
     * 时间校验用「更新后」的合成值：交期/截止任一被改则以新值为准，未改则沿用旧值。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateForAi(AiInquiryUpdateRequest request) {
        Long id = request.getInquiryId();
        Long tenantId = request.getTenantId();
        Long userId = request.getUserId();
        // 1. 询盘必须存在
        Inquiry inquiry = inquiryMapper.selectById(id);
        if (inquiry == null) {
            throw new BizException(ErrorCode.PROCUREMENT_INQUIRY_NOT_FOUND);
        }
        // 2. 仅 open 状态可编辑
        if (!InquiryStatus.OPEN.getCode().equals(inquiry.getStatus())) {
            throw new BizException(ErrorCode.PROCUREMENT_STATUS_INVALID);
        }
        // 3. 只允许采购方自己编辑（红线 2）
        if (!tenantId.equals(inquiry.getBuyerTenantId())) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }
        // 4. 已有供应商接受邀请 → 不可编辑（避免改动影响已承诺的供应商）
        List<InquiryInvite> invites = inviteMapper.selectByInquiryId(id);
        boolean hasAccepted = invites.stream().anyMatch(inv -> InviteStatus.ACCEPTED.getCode().equals(inv.getInviteStatus()));
        if (hasAccepted) {
            throw new BizException(ErrorCode.PROCUREMENT_STATUS_INVALID);
        }
        // 5. 时间校验：截止时间不放开（用旧值），交期改了则校验新交期 > 报价截止
        LocalDate newDeliveryDate = request.getDeliveryDate() != null ? request.getDeliveryDate() : inquiry.getDeliveryDate();
        validateInquiryTime(inquiry.getQuoteDeadline(), newDeliveryDate);

        // 6. 部分更新：只 set 非 null 字段（requirementId/connectionId/attachmentKey/quoteDeadline 不放开）
        Inquiry patch = Inquiry.builder()
                .id(id)
                .buyerTenantId(tenantId)
                .title(request.getTitle())
                .category(request.getCategory())
                .quantity(request.getQuantity())
                .unit(request.getUnit())
                .deliveryPlace(request.getDeliveryPlace())
                .deliveryDate(request.getDeliveryDate())
                .paymentTerms(request.getPaymentTerms())
                .specText(request.getSpecText())
                .updateBy(userId)
                .build();
        inquiryMapper.updateForAi(patch);
        log.info("AI 询盘部分更新成功: id={}", id);
    }

    /**
     * 关闭询盘：open/quoting/expired 可关，已有授标须走完成授标
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id, Long tenantId, Long userId) {
        // 1. 询盘必须存在 + 采购方归属（红线 2）
        Inquiry inquiry = inquiryMapper.selectById(id);
        if (inquiry == null) {
            throw new BizException(ErrorCode.PROCUREMENT_INQUIRY_NOT_FOUND);
        }
        if (!tenantId.equals(inquiry.getBuyerTenantId())) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }
        // 2. 允许 open / quoting / expired 关闭；已有授标必须走「完成授标」
        if (!InquiryStatus.OPEN.getCode().equals(inquiry.getStatus()) && !InquiryStatus.QUOTING.getCode().equals(inquiry.getStatus())
                && !InquiryStatus.EXPIRED.getCode().equals(inquiry.getStatus())) {
            throw new BizException(ErrorCode.PROCUREMENT_STATUS_INVALID);
        }
        List<InquiryAward> existingAwards = inquiryAwardMapper.selectByInquiryId(id);
        if (existingAwards != null && !existingAwards.isEmpty()) {
            throw new BizException(ErrorCode.PROCUREMENT_STATUS_INVALID, "已有授标记录，请在比价页使用「完成授标」结束询盘");
        }
        // 3. 更新状态为 closed
        inquiryMapper.updateStatus(id, tenantId, InquiryStatus.CLOSED.getCode(), userId);
        log.info("询盘关闭: id={}", id);

        // 4. 站内消息：通知所有未拒绝的受邀供应商询盘已关闭
        notifyInvitees(inquiry, "INQUIRY_CLOSE", "询盘已关闭",
                "【" + profileNameHelper.getCompanyName(inquiry.getBuyerTenantId()) + "】关闭了询盘《" + inquiry.getTitle() + "》", userId);
    }

    /**
     * 取消询盘：仅 open 状态可取消（尚无报价；已邀请/已接受均可取消）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, Long tenantId, Long userId) {
        // 1. 询盘必须存在 + 采购方归属（红线 2）
        Inquiry inquiry = inquiryMapper.selectById(id);
        if (inquiry == null) {
            throw new BizException(ErrorCode.PROCUREMENT_INQUIRY_NOT_FOUND);
        }
        if (!tenantId.equals(inquiry.getBuyerTenantId())) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }
        // 2. 只允许 open 状态取消（尚无报价；已邀请/已接受不影响）
        if (!InquiryStatus.OPEN.getCode().equals(inquiry.getStatus())) {
            throw new BizException(ErrorCode.PROCUREMENT_STATUS_INVALID);
        }
        // 3. 更新状态为 cancelled
        inquiryMapper.updateStatus(id, tenantId, InquiryStatus.CANCELLED.getCode(), userId);
        log.info("询盘取消: id={}", id);

        // 4. 站内消息：通知所有未拒绝的受邀供应商询盘已取消
        notifyInvitees(inquiry, "INQUIRY_CANCEL", "询盘已取消",
                "【" + profileNameHelper.getCompanyName(inquiry.getBuyerTenantId()) + "】取消了询盘《" + inquiry.getTitle() + "》", userId);
    }

    /**
     * 延长报价截止：更新截止时间，已过期询盘有报价拉回报价中、无报价拉回询价中，通知受邀方
     * <p>⚠️ 两次写库（updateDeadline + 过期恢复状态）必须在同一事务，避免截止时间已更新但状态没恢复（红线 3）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void extendDeadline(Long inquiryId, Long tenantId, Long userId, LocalDateTime newDeadline) {
        // 1. 询盘必须存在 + 采购方归属（红线 2）
        Inquiry inquiry = inquiryMapper.selectById(inquiryId);
        if (inquiry == null) throw new BizException(ErrorCode.PROCUREMENT_INQUIRY_NOT_FOUND);
        if (!tenantId.equals(inquiry.getBuyerTenantId())) throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        // 2. 仅 open/quoting/expired 可延长
        String status = inquiry.getStatus();
        if (!InquiryStatus.OPEN.getCode().equals(status) && !InquiryStatus.QUOTING.getCode().equals(status) && !InquiryStatus.EXPIRED.getCode().equals(status)) {
            throw new BizException(ErrorCode.PROCUREMENT_STATUS_INVALID);
        }
        // 3. 新截止时间不能早于当前时间
        if (newDeadline.isBefore(LocalDateTime.now()))
            throw new BizException(ErrorCode.PROCUREMENT_STATUS_INVALID, "新截止时间不能早于当前时间");
        // 4. 更新截止时间
        inquiryMapper.updateDeadline(inquiryId, newDeadline, userId);
        // 5. 已过期延长：有报价拉回报价中，从未报过价则拉回询价中
        if (InquiryStatus.EXPIRED.getCode().equals(status)) {
            List<Quote> quotes = quoteMapper.selectByInquiryId(inquiryId, tenantId);
            boolean hasQuote = quotes != null && !quotes.isEmpty();
            inquiryMapper.updateStatus(inquiryId, tenantId, hasQuote ? InquiryStatus.QUOTING.getCode() : InquiryStatus.OPEN.getCode(), userId);
        }

        // 6. 站内消息：通知所有未拒绝的受邀供应商截止时间延长
        notifyInvitees(inquiry, "INQUIRY_DEADLINE", "截止时间延长",
                "【" + profileNameHelper.getCompanyName(inquiry.getBuyerTenantId()) + "】将询盘《" + inquiry.getTitle() + "》报价截止时间延长至 "
                        + newDeadline.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")), userId);
    }

    /**
     * 校验询盘时间：报价截止 > 当前时间，交付日期 > 报价截止
     */
    private void validateInquiryTime(LocalDateTime quoteDeadline, LocalDate deliveryDate) {
        if (quoteDeadline != null && quoteDeadline.isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.PROCUREMENT_STATUS_INVALID, "报价截止时间不能早于当前时间");
        }
        if (deliveryDate != null && quoteDeadline != null
                && deliveryDate.isBefore(quoteDeadline.toLocalDate())) {
            throw new BizException(ErrorCode.PROCUREMENT_STATUS_INVALID, "交付日期不能早于报价截止时间");
        }
    }

    /**
     * 强关联采购需求：需求必须存在、归属当前租户、且为 open 状态
     */
    private void validateRequirement(Long requirementId, Long tenantId) {
        if (requirementId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请选择关联采购需求");
        }
        ProcurementRequirement req = requirementMapper.selectById(requirementId);
        if (req == null) {
            throw new BizException(ErrorCode.MATCH_REQUIREMENT_NOT_FOUND);
        }
        if (!req.getTenantId().equals(tenantId)) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }
        if (!RequirementStatus.OPEN.getCode().equals(req.getStatus())) {
            throw new BizException(ErrorCode.MATCH_INVALID_STATE, "该需求已关闭/完成，不可发起询盘");
        }
    }

    /**
     * 生成询盘编号 RFQ + yyyyMMdd + 6位Redis自增序号（每日自增键，两天后过期）
     */
    private String generateInquiryNo() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String key = "inquiry:seq:" + date;
        Long seq = stringRedisTemplate.opsForValue().increment(key);
        if (seq == 1) {
            stringRedisTemplate.expire(key, Duration.ofDays(2));
        }
        return "RFQ" + date + String.format("%06d", seq);
    }

    /**
     * 通知所有未拒绝的受邀供应商（关闭/取消/延长截止等询盘级事件）
     */
    private void notifyInvitees(Inquiry inquiry, String type, String title, String content, Long fromUserId) {
        List<InquiryInvite> invites = inviteMapper.selectByInquiryId(inquiry.getId());
        if (invites == null || invites.isEmpty()) return;
        for (InquiryInvite inv : invites) {
            // 只通知未删除且未拒绝的受邀方（已拒绝的不再打扰）
            if (inv.getIsDeleted() == 0 && !InviteStatus.DECLINED.getCode().equals(inv.getInviteStatus())) {
                notificationService.notify(Notification.builder()
                        .type(type)
                        .title(title)
                        .content(content)
                        .receiverTenantId(inv.getSupplierTenantId())
                        .relatedType("inquiry")
                        .relatedId(inquiry.getId())
                        .fromTenantId(inquiry.getBuyerTenantId())
                        .fromUserId(fromUserId)
                        .build());
            }
        }
    }
}
