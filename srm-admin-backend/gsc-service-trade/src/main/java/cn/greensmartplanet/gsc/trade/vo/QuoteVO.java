package cn.greensmartplanet.gsc.trade.vo;

import cn.greensmartplanet.gsc.framework.oss.OssService;
import cn.greensmartplanet.gsc.trade.entity.Quote;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 报价 VO
 *
 * @author gsc
 */
@Data
@Schema(description = "报价")
public class QuoteVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "报价编号")
    private String quoteNo;

    @Schema(description = "询盘ID")
    private Long inquiryId;

    @Schema(description = "采购方租户ID")
    private Long buyerTenantId;

    @Schema(description = "报价方租户ID")
    private Long supplierTenantId;

    @Schema(description = "报价方企业名")
    private String supplierCompanyName;

    @Schema(description = "单价")
    private BigDecimal unitPrice;

    @Schema(description = "总价")
    private BigDecimal totalPrice;

    @Schema(description = "供应数量")
    private Integer supplyQuantity;

    @Schema(description = "币种")
    private String currency;

    @Schema(description = "交期天数")
    private Integer deliveryDays;

    @Schema(description = "交货条款")
    private String deliveryTerms;

    @Schema(description = "报价有效期")
    private LocalDateTime validUntil;

    @Schema(description = "报价说明")
    private String remark;

    @Schema(description = "附件URL（由 attachmentKey 现签，供展示）")
    private String attachmentUrl;

    @Schema(description = "附件 OSS 对象路径（供编辑回填后再次提交，非签名 URL）")
    private String attachmentKey;

    @Schema(description = "状态：pending/withdrawn")
    private String status;

    @Schema(description = "是否中标")
    private Integer isWinning;

    @Schema(description = "授标数量（来自 inquiry_award）")
    private Integer awardedQuantity;

    @Schema(description = "提交时间")
    private LocalDateTime submittedAt;

    @Schema(description = "中标金额 = 单价×授标数量（详情填充）")
    private BigDecimal awardedAmount;

    @Schema(description = "关联合同ID（详情填充，中标且有合同时有值）")
    private Long contractId;

    @Schema(description = "关联合同状态：draft/pending_supplier/signed/completed/terminated")
    private String contractStatus;

    @Schema(description = "供应商是否已确认合同条款：0未确认 1已确认")
    private Integer supplierConfirmed;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /** 实体 → VO（不签名版）：附件字段保留库中原值。需要可访问地址请用 {@link #fromEntity(Quote, OssService)}。 */
    public static QuoteVO fromEntity(Quote q) {
        return fromEntity(q, null);
    }

    /**
     * 实体 → VO（签名版）：按 oss_key 现签短效 URL 填充附件字段。
     * <p>库里存 key —— 签名会过期，持久化后必然变死链；每次查询现签，前端始终拿到新鲜地址。
     *
     * @param q        实体
     * @param ossService OSS 服务；为 null 时不签名
     */
    public static QuoteVO fromEntity(Quote q, OssService ossService) {
        QuoteVO vo = new QuoteVO();
        vo.setId(q.getId());
        vo.setQuoteNo(q.getQuoteNo());
        vo.setInquiryId(q.getInquiryId());
        vo.setBuyerTenantId(q.getBuyerTenantId());
        vo.setSupplierTenantId(q.getSupplierTenantId());
        vo.setUnitPrice(q.getUnitPrice());
        vo.setTotalPrice(q.getTotalPrice());
        vo.setSupplyQuantity(q.getSupplyQuantity());
        vo.setCurrency(q.getCurrency());
        vo.setDeliveryDays(q.getDeliveryDays());
        vo.setDeliveryTerms(q.getDeliveryTerms());
        vo.setValidUntil(q.getValidUntil());
        vo.setRemark(q.getRemark());
        vo.setAttachmentUrl(signAttachment(q.getAttachmentKey(), ossService));
        // 同时回传原始 key，前端编辑时回填可直接再次提交（URL 会过期，不能回传）
        vo.setAttachmentKey(q.getAttachmentKey());
        vo.setStatus(q.getStatus());
        vo.setIsWinning(q.getIsWinning());
        vo.setSubmittedAt(q.getSubmittedAt());
        vo.setCreateTime(q.getCreateTime());
        return vo;
    }

    /**
     * 附件地址现签：按 oss_key 生成短效 URL。
     * <p>
     * 库里只存 key —— 签名 URL 会过期，持久化后必然变死链；每次查询现签，前端始终拿到新鲜地址。
     *
     * @param key        OSS 对象路径
     * @param ossService OSS 服务；为 null 时返回原 key（不签名）
     * @return 可访问 URL；key 为空返回 null
     */
    private static String signAttachment(String key, OssService ossService) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return ossService != null ? ossService.generateGetUrl(key) : key;
    }
}
