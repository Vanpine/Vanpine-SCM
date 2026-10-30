package cn.greensmartplanet.gsc.trade.service.impl;

import cn.greensmartplanet.gsc.common.enums.ContractStatus;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.trade.dto.CreateContractDTO;
import cn.greensmartplanet.gsc.trade.entity.Contract;
import cn.greensmartplanet.gsc.trade.entity.Inquiry;
import cn.greensmartplanet.gsc.trade.entity.InquiryAward;
import cn.greensmartplanet.gsc.trade.entity.Notification;
import cn.greensmartplanet.gsc.trade.entity.Quote;
import cn.greensmartplanet.gsc.trade.enums.InquiryStatus;
import cn.greensmartplanet.gsc.trade.mapper.ContractMapper;
import cn.greensmartplanet.gsc.trade.mapper.InquiryMapper;
import cn.greensmartplanet.gsc.trade.mapper.QuoteMapper;
import cn.greensmartplanet.gsc.trade.service.ContractBatchPlanService;
import cn.greensmartplanet.gsc.trade.service.ContractLifecycleService;
import cn.greensmartplanet.gsc.trade.service.NotificationService;
import cn.greensmartplanet.gsc.trade.support.ContractAccessValidator;
import cn.greensmartplanet.gsc.trade.support.ContractAmountHelper;
import cn.greensmartplanet.gsc.trade.support.ProfileNameHelper;
import cn.greensmartplanet.gsc.trade.support.TradeNoGenerator;
import cn.greensmartplanet.gsc.trade.vo.ContractVO;
import cn.greensmartplanet.gsc.framework.oss.OssService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 合同生命周期服务实现 — 创建 / 编辑 / 发起 / 供应商确认/拒绝 / 采购方撤回
 * <p>
 * 早期状态机：draft(草稿) → pending_supplier(待供应商确认)，供应商拒绝退回 draft 可多轮往复（磋商）。
 * 创建时从中标报价/询盘自动推导供应商、金额、履约条款（DTO 未传时），批次计划委托 ContractBatchPlanService。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractLifecycleServiceImpl implements ContractLifecycleService {

    private final ContractMapper contractMapper;
    private final InquiryMapper inquiryMapper;
    private final QuoteMapper quoteMapper;
    private final ContractBatchPlanService contractBatchPlanService;
    private final ContractAccessValidator contractAccessValidator;
    private final ContractAmountHelper contractAmountHelper;
    private final TradeNoGenerator tradeNoGenerator;
    private final NotificationService notificationService;
    private final ProfileNameHelper profileNameHelper;
    private final OssService ossService;  // 组装 VO 时按 oss_key 现签附件 URL

    /**
     * 创建合同：校验询盘已授标+中标报价有效，防重，从报价/询盘推导供应商/金额/履约条款，初始状态 draft
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContractVO create(Long tenantId, Long userId, CreateContractDTO dto) {
        // 1. 生成合同编号（统一编号生成器，前缀 CTR）
        String contractNo = tradeNoGenerator.generate("CTR", "contract");

        // 2. 校验询盘存在且已授标（未授标不可创建合同）
        Inquiry inquiry = inquiryMapper.selectById(dto.getInquiryId());
        if (inquiry == null) {
            throw new BizException(ErrorCode.PROCUREMENT_INQUIRY_NOT_FOUND);
        }
        if (!InquiryStatus.AWARDED.getCode().equals(inquiry.getStatus())) {
            throw new BizException(ErrorCode.PROCUREMENT_CONTRACT_AWARD_REQUIRED);
        }

        // 3. 中标报价必传（分量授标：前端逐家带 quoteId，避免多授标歧义）+ 报价归属/中标校验（红线 2）
        Long winningQuoteId = dto.getWinningQuoteId();
        if (winningQuoteId == null) {
            throw new BizException(ErrorCode.PROCUREMENT_CONTRACT_WINNING_QUOTE_REQUIRED);
        }
        Quote quote = quoteMapper.selectById(winningQuoteId);
        if (quote == null) {
            throw new BizException(ErrorCode.PROCUREMENT_QUOTE_NOT_FOUND);
        }
        if (!dto.getInquiryId().equals(quote.getInquiryId())) {
            throw new BizException(ErrorCode.PROCUREMENT_QUOTE_NOT_FOUND);
        }
        if (!Integer.valueOf(1).equals(quote.getIsWinning())) {
            throw new BizException(ErrorCode.PROCUREMENT_CONTRACT_QUOTE_NOT_AWARDED);
        }

        // 4. 防重：同一询盘下每个中标报价只能建一份合同（不同中标报价各建一份）
        if (contractMapper.countByInquiryAndWinningQuote(dto.getInquiryId(), winningQuoteId) > 0) {
            throw new BizException(ErrorCode.PROCUREMENT_CONTRACT_DUPLICATE);
        }

        // 5. 从报价自动推导供应商/金额/币种/交付条款（DTO 未传时）
        Long supplierTenantId = quote.getSupplierTenantId();
        // 5a. 从授标带出履约数量（数量唯一事实源）
        InquiryAward award = contractAmountHelper.selectAwardByQuoteId(winningQuoteId);
        Integer contractQuantity = award != null ? award.getAwardedQuantity() : null;
        if (dto.getAmount() == null) {
            dto.setAmount(contractAmountHelper.deriveContractAmount(quote));
        }
        if (dto.getCurrency() == null && quote.getCurrency() != null) {
            dto.setCurrency(quote.getCurrency());
        }
        if (dto.getDeliveryTerms() == null && quote.getDeliveryTerms() != null) {
            dto.setDeliveryTerms(quote.getDeliveryTerms());
        }
        // 5b. 从询盘自动带出交付/付款信息（DTO 未传时）
        if (dto.getDeliveryPlace() == null && inquiry.getDeliveryPlace() != null) {
            dto.setDeliveryPlace(inquiry.getDeliveryPlace());
        }
        if (dto.getDeliveryDate() == null && inquiry.getDeliveryDate() != null) {
            dto.setDeliveryDate(inquiry.getDeliveryDate());
        }
        if (dto.getPaymentTerms() == null && inquiry.getPaymentTerms() != null) {
            dto.setPaymentTerms(inquiry.getPaymentTerms());
        }

        // 6. 落库合同（初始状态 draft）
        Contract contract = Contract.builder()
                .contractNo(contractNo)
                .inquiryId(dto.getInquiryId())
                .winningQuoteId(winningQuoteId)
                .buyerTenantId(tenantId)
                .supplierTenantId(supplierTenantId)
                .title(dto.getTitle())
                .amount(dto.getAmount())
                .quantity(contractQuantity)
                .currency(dto.getCurrency() != null ? dto.getCurrency() : "CNY")
                .signDate(dto.getSignDate())
                .validUntil(dto.getValidUntil())
                .deliveryPlace(dto.getDeliveryPlace())
                .deliveryDate(dto.getDeliveryDate())
                .paymentTerms(dto.getPaymentTerms())
                .deliveryTerms(dto.getDeliveryTerms())
                .productionNeedDate(dto.getProductionNeedDate())
                .materialCriticalLevel(dto.getMaterialCriticalLevel())
                .inventoryCoverDays(dto.getInventoryCoverDays())
                .plannedShipDate(dto.getPlannedShipDate())
                .allowedDelayDays(dto.getAllowedDelayDays())
                .partialDeliveryAllowed(dto.getPartialDeliveryAllowed())
                .substitutionAllowed(dto.getSubstitutionAllowed())
                .expediteAllowed(dto.getExpediteAllowed())
                .deliveryNoticeDays(dto.getDeliveryNoticeDays())
                .exceptionNoticeHours(dto.getExceptionNoticeHours())
                .penaltyDailyRate(dto.getPenaltyDailyRate())
                .penaltyMaxRate(dto.getPenaltyMaxRate())
                .status(ContractStatus.DRAFT.getCode())
                .attachmentKey(dto.getAttachmentKey())
                .createBy(userId)
                .build();
        contractMapper.insert(contract);
        contractBatchPlanService.saveBatchPlans(contract, dto.getBatchPlans());

        log.info("合同创建成功: id={}, contractNo={}, buyer={}, winningQuoteId={}, supplier={}",
                contract.getId(), contractNo, tenantId, winningQuoteId, supplierTenantId);

        // 7. 组装 VO 返回
        ContractVO vo = ContractVO.fromEntity(contract, ossService);
        vo.setBuyerCompanyName(profileNameHelper.getCompanyName(tenantId));
        vo.setSupplierCompanyName(profileNameHelper.getCompanyName(supplierTenantId));
        return vo;
    }

    /**
     * 编辑合同（仅采购方、draft 状态可编辑），重建分批计划
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, Long tenantId, Long userId, CreateContractDTO dto) {
        // 1. 校验存在 + draft 状态 + 采购方身份（红线 2）
        Contract contract = contractAccessValidator.getContract(id, tenantId);
        if (!ContractStatus.DRAFT.getCode().equals(contract.getStatus())) {
            throw new BizException(ErrorCode.PROCUREMENT_STATUS_INVALID);
        }

        // 2. 更新字段
        contract.setTitle(dto.getTitle());
        contract.setAmount(dto.getAmount());
        contract.setCurrency(dto.getCurrency());
        contract.setSignDate(dto.getSignDate());
        contract.setValidUntil(dto.getValidUntil());
        contract.setDeliveryPlace(dto.getDeliveryPlace());
        contract.setDeliveryDate(dto.getDeliveryDate());
        contract.setPaymentTerms(dto.getPaymentTerms());
        contract.setDeliveryTerms(dto.getDeliveryTerms());
        contract.setProductionNeedDate(dto.getProductionNeedDate());
        contract.setMaterialCriticalLevel(dto.getMaterialCriticalLevel());
        contract.setInventoryCoverDays(dto.getInventoryCoverDays());
        contract.setPlannedShipDate(dto.getPlannedShipDate());
        contract.setAllowedDelayDays(dto.getAllowedDelayDays());
        contract.setPartialDeliveryAllowed(dto.getPartialDeliveryAllowed());
        contract.setSubstitutionAllowed(dto.getSubstitutionAllowed());
        contract.setExpediteAllowed(dto.getExpediteAllowed());
        contract.setDeliveryNoticeDays(dto.getDeliveryNoticeDays());
        contract.setExceptionNoticeHours(dto.getExceptionNoticeHours());
        contract.setPenaltyDailyRate(dto.getPenaltyDailyRate());
        contract.setPenaltyMaxRate(dto.getPenaltyMaxRate());
        contract.setAttachmentKey(dto.getAttachmentKey());
        contract.setUpdateBy(userId);
        contractMapper.update(contract);

        // 3. 重建分批计划：先逻辑删除旧的，再按本次提交的重新写入（空则默认一批全量）
        contractBatchPlanService.rebuildBatchPlans(id, contract, dto.getBatchPlans());
        log.info("合同编辑成功: id={}", id);
    }

    /**
     * 发起合同待供应商确认（draft→pending_supplier），通知供应商
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id, Long tenantId, Long userId) {
        // 1. 校验采购方身份 + draft 状态，提交待供应商确认（红线 2/3）
        Contract contract = contractAccessValidator.getContract(id, tenantId);
        if (!ContractStatus.DRAFT.getCode().equals(contract.getStatus())) {
            throw new BizException(ErrorCode.PROCUREMENT_CONTRACT_CANNOT_SUBMIT);
        }
        int n = contractMapper.submit(id, tenantId, userId);
        if (n == 0) {
            throw new BizException(ErrorCode.PROCUREMENT_STATUS_INVALID);
        }
        log.info("合同已发起待供应商确认: id={}", id);

        // 2. 站内消息：通知供应商合同待确认（企业名降级"采购方"兜底）
        String buyerCompanyName = profileNameHelper.getCompanyName(contract.getBuyerTenantId());
        notificationService.notify(Notification.builder()
                .type("CONTRACT_SUBMIT")
                .title("合同待确认")
                .content("【" + (buyerCompanyName != null ? buyerCompanyName : "采购方") + "】向您发起了合同《" + contract.getTitle() + "》，待确认")
                .receiverTenantId(contract.getSupplierTenantId())
                .relatedType("contract")
                .relatedId(contract.getId())
                .fromTenantId(tenantId)
                .fromUserId(userId)
                .build());
    }

    /**
     * 供应商确认合同条款（pending_supplier 记确认标记，状态不变），通知采购方
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long id, Long tenantId, Long userId) {
        // 1. 任一方归属校验 + 校验供应商身份 + pending_supplier 状态（红线 2/3）
        Contract contract = contractAccessValidator.getContractAnyParty(id, tenantId);
        if (!tenantId.equals(contract.getSupplierTenantId())) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }
        if (!ContractStatus.PENDING_SUPPLIER.getCode().equals(contract.getStatus())) {
            throw new BizException(ErrorCode.PROCUREMENT_CONTRACT_CANNOT_CONFIRM);
        }
        int n = contractMapper.confirmSupplier(id, tenantId, userId);
        if (n == 0) {
            throw new BizException(ErrorCode.PROCUREMENT_STATUS_INVALID);
        }
        log.info("供应商已确认合同条款: id={}", id);

        // 2. 站内消息：通知采购方供应商已确认条款（企业名降级"供应商"兜底）
        String supplierCompanyName = profileNameHelper.getCompanyName(contract.getSupplierTenantId());
        notificationService.notify(Notification.builder()
                .type("CONTRACT_CONFIRM")
                .title("合同条款已确认")
                .content("【" + (supplierCompanyName != null ? supplierCompanyName : "供应商") + "】已确认合同《" + contract.getTitle() + "》条款")
                .receiverTenantId(contract.getBuyerTenantId())
                .relatedType("contract")
                .relatedId(contract.getId())
                .fromTenantId(tenantId)
                .fromUserId(userId)
                .build());
    }

    /**
     * 供应商拒绝合同并退回采购方，原因必填
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void decline(Long id, Long tenantId, Long userId, String reason) {
        // 1. 校验拒绝原因 + 任一方归属 + 供应商身份 + pending_supplier 状态（红线 2/3）
        if (reason == null || reason.trim().isEmpty()) {
            throw new BizException(ErrorCode.PROCUREMENT_CONTRACT_DECLINE_REASON_REQUIRED);
        }
        Contract contract = contractAccessValidator.getContractAnyParty(id, tenantId);
        if (!tenantId.equals(contract.getSupplierTenantId())) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }
        if (!ContractStatus.PENDING_SUPPLIER.getCode().equals(contract.getStatus())) {
            throw new BizException(ErrorCode.PROCUREMENT_CONTRACT_CANNOT_DECLINE);
        }
        int n = contractMapper.decline(id, tenantId, userId, reason.trim());
        if (n == 0) {
            throw new BizException(ErrorCode.PROCUREMENT_STATUS_INVALID);
        }
        log.info("供应商已拒绝合同并退回采购方: id={}, reason={}", id, reason);

        // 2. 站内消息：通知采购方供应商拒绝合同（含原因）
        String supplierCompanyName = profileNameHelper.getCompanyName(contract.getSupplierTenantId());
        notificationService.notify(Notification.builder()
                .type("CONTRACT_DECLINE")
                .title("合同被拒绝")
                .content("【" + (supplierCompanyName != null ? supplierCompanyName : "供应商") + "】拒绝了合同《" + contract.getTitle() + "》：" + reason.trim())
                .receiverTenantId(contract.getBuyerTenantId())
                .relatedType("contract")
                .relatedId(contract.getId())
                .fromTenantId(tenantId)
                .fromUserId(userId)
                .build());
    }

    /**
     * 采购方撤回待确认合同，退回修改（无需理由）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long id, Long tenantId, Long userId) {
        // 1. 校验采购方身份 + pending_supplier 状态，撤回待修改（红线 2/3）
        Contract contract = contractAccessValidator.getContract(id, tenantId);
        if (!ContractStatus.PENDING_SUPPLIER.getCode().equals(contract.getStatus())) {
            throw new BizException(ErrorCode.PROCUREMENT_CONTRACT_CANNOT_WITHDRAW);
        }
        int n = contractMapper.withdraw(id, tenantId, userId);
        if (n == 0) {
            throw new BizException(ErrorCode.PROCUREMENT_STATUS_INVALID);
        }
        log.info("采购方已撤回合同待修改: id={}", id);
    }
}
