package cn.greensmartplanet.gsc.trade.vo;

import cn.greensmartplanet.gsc.framework.oss.OssService;
import cn.greensmartplanet.gsc.trade.dto.BatchPlanItem;
import cn.greensmartplanet.gsc.trade.entity.Contract;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 合同 VO
 *
 * @author gsc
 */
@Data
@Schema(description = "合同")
public class ContractVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "合同编号")
    private String contractNo;

    @Schema(description = "关联询盘ID")
    private Long inquiryId;

    @Schema(description = "中标报价ID")
    private Long winningQuoteId;

    @Schema(description = "关联询盘标题")
    private String inquiryTitle;

    @Schema(description = "中标报价金额")
    private BigDecimal quoteAmount;

    @Schema(description = "采购方租户ID")
    private Long buyerTenantId;

    @Schema(description = "采购方企业名")
    private String buyerCompanyName;

    @Schema(description = "供应方租户ID")
    private Long supplierTenantId;

    @Schema(description = "供应方企业名")
    private String supplierCompanyName;

    @Schema(description = "合同标题")
    private String title;

    @Schema(description = "合同金额")
    private BigDecimal amount;

    @Schema(description = "履约数量")
    private Integer quantity;

    @Schema(description = "分批计划")
    private List<BatchPlanItem> batchPlans;

    @Schema(description = "币种")
    private String currency;

    @Schema(description = "签署日期（即合同生效日期，签署即生效）")
    private LocalDate signDate;

    @Schema(description = "截止日期")
    private LocalDate validUntil;

    @Schema(description = "交货地点")
    private String deliveryPlace;

    @Schema(description = "交付日期")
    private LocalDate deliveryDate;

    @Schema(description = "付款条件")
    private String paymentTerms;

    @Schema(description = "交付条款")
    private String deliveryTerms;

    @Schema(description = "生产需求日期")
    private LocalDate productionNeedDate;

    @Schema(description = "物料关键等级：low/medium/high")
    private String materialCriticalLevel;

    @Schema(description = "库存覆盖天数")
    private Integer inventoryCoverDays;

    @Schema(description = "约定最晚发运日期")
    private LocalDate plannedShipDate;

    @Schema(description = "合同允许延期天数")
    private Integer allowedDelayDays;

    @Schema(description = "是否允许部分交付：0否 1是")
    private Integer partialDeliveryAllowed;

    @Schema(description = "是否允许替代物料：0否 1是")
    private Integer substitutionAllowed;

    @Schema(description = "是否允许加急运输：0否 1是")
    private Integer expediteAllowed;

    @Schema(description = "供应商需提前几天反馈发运计划")
    private Integer deliveryNoticeDays;

    @Schema(description = "发生异常后最晚通知小时数")
    private Integer exceptionNoticeHours;

    @Schema(description = "每日违约金率（万分之五=0.0005）")
    private BigDecimal penaltyDailyRate;

    @Schema(description = "违约金上限比例（10%=0.10）")
    private BigDecimal penaltyMaxRate;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "终止原因")
    private String terminateReason;

    @Schema(description = "终止类型：mutual|supplier_breach|buyer_breach|force_majeure")
    private String terminateType;

    @Schema(description = "合同附件URL（由 attachmentKey 现签，供展示）")
    private String attachmentUrl;

    @Schema(description = "合同附件 OSS 对象路径（供编辑回填后再次提交，非签名 URL）")
    private String attachmentKey;

    @Schema(description = "供应商是否已确认条款：0未确认 1已确认")
    private Integer supplierConfirmed;

    @Schema(description = "供应商确认时间")
    private LocalDateTime supplierConfirmedAt;

    @Schema(description = "供应商拒绝理由")
    private String declineReason;

    @Schema(description = "盖章件URL（历史字段）")
    private String signAttachmentUrl;

    @Schema(description = "盖章件上传方租户ID（历史字段）")
    private Long signUploadedByTenantId;

    @Schema(description = "采购方盖章件URL")
    private String buyerSignAttachmentUrl;

    @Schema(description = "供应方盖章件URL")
    private String supplierSignAttachmentUrl;

    @Schema(description = "采购方是否已确认对方盖章件：0未确认 1已确认")
    private Integer buyerSignConfirm;

    @Schema(description = "供应方是否已确认对方盖章件：0未确认 1已确认")
    private Integer supplierSignConfirm;

    @Schema(description = "父合同ID")
    private Long parentContractId;

    @Schema(description = "采购方撤回时间（draft 状态非空表示采购方撤回待修改）")
    private LocalDateTime withdrawnAt;

    @Schema(description = "关联询盘编号")
    private String inquiryNo;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    /**
     * 实体 → VO（不签名版：仅用于不需要附件 URL 的场景，如内部状态流转后的即时返回）。
     * <p>
     * 附件类字段会保留库中原始值（历史数据为已过期的签名 URL），
     * 需要可访问的附件地址时请用 {@link #fromEntity(Contract, OssService)}。
     */
    public static ContractVO fromEntity(Contract c) {
        return fromEntity(c, null);
    }

    /**
     * 实体 → VO（签名版）：按 oss_key 现签短效 URL 填充附件字段。
     * <p>
     * 存的是 key 而非 URL——签名 URL 会过期，持久化后必然变死链。
     * 每次查询现签，前端拿到的始终是新鲜可访问的地址。
     *
     * @param c         合同实体
     * @param ossService OSS 服务；传 null 时附件字段取库中原值（不做签名）
     */
    public static ContractVO fromEntity(Contract c, OssService ossService) {
        ContractVO vo = new ContractVO();
        vo.setId(c.getId());
        vo.setContractNo(c.getContractNo());
        vo.setInquiryId(c.getInquiryId());
        vo.setWinningQuoteId(c.getWinningQuoteId());
        vo.setBuyerTenantId(c.getBuyerTenantId());
        vo.setSupplierTenantId(c.getSupplierTenantId());
        vo.setTitle(c.getTitle());
        vo.setAmount(c.getAmount());
        vo.setQuantity(c.getQuantity());
        vo.setCurrency(c.getCurrency());
        vo.setSignDate(c.getSignDate());
        vo.setValidUntil(c.getValidUntil());
        vo.setDeliveryPlace(c.getDeliveryPlace());
        vo.setDeliveryDate(c.getDeliveryDate());
        vo.setPaymentTerms(c.getPaymentTerms());
        vo.setDeliveryTerms(c.getDeliveryTerms());
        vo.setProductionNeedDate(c.getProductionNeedDate());
        vo.setMaterialCriticalLevel(c.getMaterialCriticalLevel());
        vo.setInventoryCoverDays(c.getInventoryCoverDays());
        vo.setPlannedShipDate(c.getPlannedShipDate());
        vo.setAllowedDelayDays(c.getAllowedDelayDays());
        vo.setPartialDeliveryAllowed(c.getPartialDeliveryAllowed());
        vo.setSubstitutionAllowed(c.getSubstitutionAllowed());
        vo.setExpediteAllowed(c.getExpediteAllowed());
        vo.setDeliveryNoticeDays(c.getDeliveryNoticeDays());
        vo.setExceptionNoticeHours(c.getExceptionNoticeHours());
        vo.setPenaltyDailyRate(c.getPenaltyDailyRate());
        vo.setPenaltyMaxRate(c.getPenaltyMaxRate());
        vo.setStatus(c.getStatus());
        vo.setTerminateReason(c.getTerminateReason());
        vo.setTerminateType(c.getTerminateType());
        vo.setAttachmentUrl(signUrl(c.getAttachmentKey(), null, ossService));
        // 同时回传原始 key，前端编辑时回填可直接再次提交（URL 会过期，不能回传）
        vo.setAttachmentKey(c.getAttachmentKey());
        vo.setSupplierConfirmed(c.getSupplierConfirmed());
        vo.setSupplierConfirmedAt(c.getSupplierConfirmedAt());
        vo.setDeclineReason(c.getDeclineReason());
        vo.setSignAttachmentUrl(signUrl(c.getSignAttachmentKey(), c.getSignAttachmentUrl(), ossService));
        vo.setSignUploadedByTenantId(c.getSignUploadedByTenantId());
        vo.setBuyerSignAttachmentUrl(signUrl(c.getBuyerSignAttachmentKey(), c.getBuyerSignAttachmentUrl(), ossService));
        vo.setSupplierSignAttachmentUrl(signUrl(c.getSupplierSignAttachmentKey(), c.getSupplierSignAttachmentUrl(), ossService));
        vo.setBuyerSignConfirm(c.getBuyerSignConfirm());
        vo.setSupplierSignConfirm(c.getSupplierSignConfirm());
        vo.setParentContractId(c.getParentContractId());
        vo.setWithdrawnAt(c.getWithdrawnAt());
        vo.setCreateTime(c.getCreateTime());
        vo.setUpdateTime(c.getUpdateTime());
        return vo;
    }

    /**
     * 附件地址解析：优先按 oss_key 现签短效 URL；key 为空时回退历史 URL 字段。
     * <p>
     * 回退是为了兼容改造前的旧数据（其 URL 可能已过期，但至少不会比空值更差）；
     * 新数据一律走 key 现签，不存在过期问题。
     *
     * @param key        OSS 对象路径（新数据）
     * @param legacyUrl  历史签名 URL（旧数据）
     * @param ossService OSS 服务，为 null 时直接返回历史值（不签名）
     * @return 可直接访问的 URL；都为空时返回 null
     */
    private static String signUrl(String key, String legacyUrl, OssService ossService) {
        if (ossService != null && key != null && !key.isBlank()) {
            return ossService.generateGetUrl(key);
        }
        return legacyUrl;
    }
}
