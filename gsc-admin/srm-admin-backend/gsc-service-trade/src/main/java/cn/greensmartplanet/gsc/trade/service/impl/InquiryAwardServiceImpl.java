package cn.greensmartplanet.gsc.trade.service.impl;

import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.trade.dto.AwardItemDTO;
import cn.greensmartplanet.gsc.trade.entity.Inquiry;
import cn.greensmartplanet.gsc.trade.entity.InquiryAward;
import cn.greensmartplanet.gsc.trade.entity.Notification;
import cn.greensmartplanet.gsc.trade.entity.Quote;
import cn.greensmartplanet.gsc.trade.enums.InquiryStatus;
import cn.greensmartplanet.gsc.trade.enums.QuoteStatus;
import cn.greensmartplanet.gsc.trade.mapper.InquiryAwardMapper;
import cn.greensmartplanet.gsc.trade.mapper.InquiryMapper;
import cn.greensmartplanet.gsc.trade.mapper.QuoteMapper;
import cn.greensmartplanet.gsc.trade.service.InquiryAwardService;
import cn.greensmartplanet.gsc.trade.service.NotificationService;
import cn.greensmartplanet.gsc.trade.support.ProfileNameHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 询盘授标服务实现 — 分量授标 / 主动完成授标
 * <p>
 * 授标规则（红线 2 已覆盖）：
 * 只按采购数量判断是否结束，与需求「目标供应商数」无关；
 * 分配量凑满需求量 → awarded；未满保持 quoting/expired，可继续授标。
 * 已中标必须原样提交（不可漏掉、不可改数量），只能追加未中标报价。
 * 授标写库在同一事务内，通知走 MQ 异步（失败不影响主业务）。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InquiryAwardServiceImpl implements InquiryAwardService {

    private final InquiryMapper inquiryMapper;
    private final QuoteMapper quoteMapper;
    private final InquiryAwardMapper inquiryAwardMapper;
    private final NotificationService notificationService;
    private final ProfileNameHelper profileNameHelper;

    /**
     * 分量授标：校验报价/供应能力/总量不超采购量，标记中标，凑满需求量才翻已授标
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> award(Long id, Long tenantId, Long userId, List<AwardItemDTO> awards, String awardReason) {
        // 1. 询盘存在 + 采购方归属校验（红线 2）
        Inquiry inquiry = inquiryMapper.selectById(id);
        if (inquiry == null) {
            throw new BizException(ErrorCode.PROCUREMENT_INQUIRY_NOT_FOUND);
        }
        if (!tenantId.equals(inquiry.getBuyerTenantId())) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }
        // 1b. 报价中或已过期均可分量授标（过期后仍可确认已有报价，未满量不结束）
        if (!InquiryStatus.QUOTING.getCode().equals(inquiry.getStatus()) && !InquiryStatus.EXPIRED.getCode().equals(inquiry.getStatus())) {
            throw new BizException(ErrorCode.PROCUREMENT_STATUS_INVALID);
        }
        if (awards == null || awards.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "至少选择一个中标供应商");
        }

        // 2. 校验每个报价 + 汇总授标总量（含去重、供应能力、已中标锁定）
        int totalQty = 0;
        Map<Long, Long> quoteSupplierMap = new LinkedHashMap<>();
        for (AwardItemDTO item : awards) {
            if (item.getQuoteId() == null) {
                throw new BizException(ErrorCode.BAD_REQUEST, "报价ID不能为空");
            }
            if (item.getAwardedQuantity() == null || item.getAwardedQuantity() <= 0) {
                throw new BizException(ErrorCode.PROCUREMENT_AWARD_QUANTITY_REQUIRED);
            }
            if (quoteSupplierMap.containsKey(item.getQuoteId())) {
                throw new BizException(ErrorCode.BAD_REQUEST, "同一报价不能重复授标");
            }
            // 2a. 报价必须存在且属于该询盘（红线 2：关联 ID 归属校验）
            Quote quote = quoteMapper.selectById(item.getQuoteId());
            if (quote == null || !id.equals(quote.getInquiryId())) {
                throw new BizException(ErrorCode.PROCUREMENT_QUOTE_NOT_FOUND);
            }
            // 2b. 已撤回报价不可授标
            if (QuoteStatus.WITHDRAWN.getCode().equals(quote.getStatus())) {
                throw new BizException(ErrorCode.PROCUREMENT_STATUS_INVALID, "已撤回的报价不可授标");
            }
            // 2c. 分配数量不能超过供应商供应能力
            if (quote.getSupplyQuantity() != null && item.getAwardedQuantity() > quote.getSupplyQuantity()) {
                throw new BizException(ErrorCode.PROCUREMENT_STATUS_INVALID,
                        "分配数量(" + item.getAwardedQuantity() + ")超过供应商供应能力(" + quote.getSupplyQuantity() + ")");
            }
            totalQty += item.getAwardedQuantity();
            quoteSupplierMap.put(item.getQuoteId(), quote.getSupplierTenantId());
        }
        // 2d. 已中标必须原样出现在本次提交中（不可漏掉/改数量）
        assertExistingAwardsLocked(id, awards);
        // 2e. 授标总量不能超过询盘采购数量
        if (inquiry.getQuantity() != null && totalQty > inquiry.getQuantity().intValue()) {
            throw new BizException(ErrorCode.PROCUREMENT_AWARD_QUANTITY_EXCEED);
        }

        // 3. 清除旧授标明细 + 旧中标标记（本次提交以全量重建方式落库）
        inquiryAwardMapper.deleteByInquiryId(id);
        quoteMapper.clearWinningByInquiryId(id, userId);

        // 4. 插入新授标明细 + 批量标记中标
        for (AwardItemDTO item : awards) {
            Long supplierId = quoteSupplierMap.get(item.getQuoteId());
            InquiryAward award = InquiryAward.builder()
                    .inquiryId(id)
                    .quoteId(item.getQuoteId())
                    .supplierTenantId(supplierId)
                    .awardedQuantity(item.getAwardedQuantity())
                    .awardedBy(userId)
                    .awardedAt(LocalDateTime.now())
                    .build();
            inquiryAwardMapper.insert(award);
            quoteMapper.markWinning(item.getQuoteId(), userId);
        }

        // 5. 只按采购数量判断是否结束，与需求「目标供应商数」无关；凑满才翻已授标
        int demand = inquiry.getQuantity() != null ? inquiry.getQuantity().intValue() : totalQty;
        boolean completed = totalQty >= demand;
        if (completed) {
            Long firstQuoteId = awards.get(0).getQuoteId();
            inquiryMapper.updateAward(id, tenantId, firstQuoteId, userId, awardReason);
        }
        log.info("分量授标: inquiryId={}, awards={}, totalQty={}, demand={}, completed={}, awardedBy={}",
                id, awards.size(), totalQty, demand, completed, userId);

        // 6. 站内消息：通知各中标供应商（buyer 公司名提到循环外一次，减少事务内 Feign 调用）
        String buyerCompanyName = profileNameHelper.getCompanyName(inquiry.getBuyerTenantId());
        Set<Long> winnerIds = new LinkedHashSet<>(quoteSupplierMap.values());
        for (Long sid : winnerIds) {
            notificationService.notify(Notification.builder()
                    .type("INQUIRY_AWARD")
                    .title("中标通知")
                    .content("【" + buyerCompanyName + "】已选定您为中标供应商（询盘《" + inquiry.getTitle() + "》）")
                    .receiverTenantId(sid)
                    .relatedType("inquiry")
                    .relatedId(id)
                    .fromTenantId(inquiry.getBuyerTenantId())
                    .fromUserId(userId)
                    .build());
        }

        // 7. 组装返回：是否结束 + 剩余未分配数量
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("completed", completed);
        result.put("remaining", Math.max(demand - totalQty, 0));
        return result;
    }

    /**
     * 主动完成授标：未凑满量时手动结束询盘（quoting/expired → awarded）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeAward(Long id, Long tenantId, Long userId) {
        // 1. 询盘存在 + 采购方归属校验（红线 2）
        Inquiry inquiry = inquiryMapper.selectById(id);
        if (inquiry == null) {
            throw new BizException(ErrorCode.PROCUREMENT_INQUIRY_NOT_FOUND);
        }
        if (!tenantId.equals(inquiry.getBuyerTenantId())) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }
        // 1b. 仅 quoting/expired 可主动完成授标
        if (!InquiryStatus.QUOTING.getCode().equals(inquiry.getStatus()) && !InquiryStatus.EXPIRED.getCode().equals(inquiry.getStatus())) {
            throw new BizException(ErrorCode.PROCUREMENT_STATUS_INVALID);
        }
        // 1c. 必须已有授标明细才能完成
        List<InquiryAward> existing = inquiryAwardMapper.selectByInquiryId(id);
        if (existing == null || existing.isEmpty()) {
            throw new BizException(ErrorCode.PROCUREMENT_STATUS_INVALID, "请先选定中标供应商");
        }
        // 2. 翻已授标（未满量结束询盘的唯一入口）
        inquiryMapper.updateAward(id, tenantId, existing.get(0).getQuoteId(), userId, inquiry.getAwardReason());
        log.info("主动完成授标: inquiryId={}, awards={}, awardedBy={}", id, existing.size(), userId);
    }

    /**
     * 已中标必须原样出现在本次提交中：不可漏掉、不可改数量
     */
    private void assertExistingAwardsLocked(Long inquiryId, List<AwardItemDTO> awards) {
        List<InquiryAward> existing = inquiryAwardMapper.selectByInquiryId(inquiryId);
        if (existing == null || existing.isEmpty()) {
            return;
        }
        // 1. 本次提交的 quoteId → 数量 映射（校验旧中标都在其中且数量一致）
        Map<Long, Integer> submittedQty = new LinkedHashMap<>();
        for (AwardItemDTO item : awards) {
            submittedQty.put(item.getQuoteId(), item.getAwardedQuantity());
        }
        for (InquiryAward locked : existing) {
            Integer qty = submittedQty.get(locked.getQuoteId());
            if (qty == null) {
                throw new BizException(ErrorCode.PROCUREMENT_AWARD_WINNER_LOCKED, "已中标供应商不可取消");
            }
            if (locked.getAwardedQuantity() != null && !locked.getAwardedQuantity().equals(qty)) {
                throw new BizException(ErrorCode.PROCUREMENT_AWARD_WINNER_LOCKED, "已中标数量不可修改");
            }
        }
    }
}
