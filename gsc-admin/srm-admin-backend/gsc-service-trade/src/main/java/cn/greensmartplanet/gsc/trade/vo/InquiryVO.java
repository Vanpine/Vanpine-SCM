package cn.greensmartplanet.gsc.trade.vo;

import cn.greensmartplanet.gsc.framework.oss.OssService;
import cn.greensmartplanet.gsc.trade.entity.Inquiry;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 询盘 VO
 *
 * @author gsc
 */
@Data
@Schema(description = "询盘")
public class InquiryVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "询盘编号")
    private String inquiryNo;

    @Schema(description = "关联需求ID")
    private Long requirementId;

    @Schema(description = "关联对接记录ID")
    private Long connectionId;

    @Schema(description = "采购方租户ID")
    private Long buyerTenantId;

    @Schema(description = "采购方企业名")
    private String buyerCompanyName;

    @Schema(description = "询盘标题")
    private String title;

    @Schema(description = "币种")
    private String currency;

    @Schema(description = "采购品类")
    private String category;

    @Schema(description = "数量")
    private BigDecimal quantity;

    @Schema(description = "单位")
    private String unit;

    @Schema(description = "交货地点")
    private String deliveryPlace;

    @Schema(description = "期望交付日期")
    private LocalDate deliveryDate;

    @Schema(description = "报价截止时间")
    private LocalDateTime quoteDeadline;

    @Schema(description = "付款条件")
    private String paymentTerms;

    @Schema(description = "规格条款")
    private String specText;

    @Schema(description = "附件URL（由 attachmentKey 现签，供展示）")
    private String attachmentUrl;

    @Schema(description = "附件 OSS 对象路径（供编辑回填后再次提交，非签名 URL）")
    private String attachmentKey;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "中标报价ID")
    private Long winningQuoteId;

    @Schema(description = "选定中标时间")
    private LocalDateTime awardedAt;

    @Schema(description = "中标理由")
    private String awardReason;

    @Schema(description = "已接受邀请的供应商数")
    private Integer acceptedSupplierCount;

    @Schema(description = "已拒绝邀请的供应商数")
    private Integer declinedSupplierCount;

    @Schema(description = "受邀供应商数")
    private Integer supplierCount;

    @Schema(description = "报价数")
    private Integer quoteCount;

    @Schema(description = "已授标数量合计（采购数量口径，未授为0）")
    private Integer awardedQuantityTotal;

    @Schema(description = "已授标供应商家数")
    private Integer awardedSupplierCount;

    @Schema(description = "是否已创建合同")
    private Boolean contractExists;

    @Schema(description = "受邀供应商名单")
    private List<InquiryInviteVO> invites;

    @Schema(description = "报价列表（采购方可见全部，供应商可见自己）")
    private List<QuoteVO> quotes;

    @Schema(description = "当前用户对该询盘的邀请状态（供应商视角填充）")
    private String myInviteStatus;

    @Schema(description = "当前用户的邀请记录ID（供应商视角填充）")
    private Long myInviteId;

    @Schema(description = "我的报价总价（供应商视角列表填充）")
    private java.math.BigDecimal myQuotePrice;

    @Schema(description = "我的报价币种（供应商视角列表填充）")
    private String myQuoteCurrency;

    @Schema(description = "我的报价ID（供应商视角列表填充）")
    private Long myQuoteId;

    @Schema(description = "我是否中标（供应商视角列表填充）")
    private Boolean myQuoteIsWinning;

    @Schema(description = "我的报价状态（供应商视角列表填充）")
    private String myQuoteStatus;

    @Schema(description = "我的报价单价（供应商视角列表填充）")
    private BigDecimal myQuoteUnitPrice;

    @Schema(description = "我的授标数量（供应商视角列表填充，未中为null）")
    private Integer myAwardedQuantity;

    @Schema(description = "我的中标金额 = 单价×授标数量（供应商视角列表填充）")
    private BigDecimal myAwardedAmount;

    @Schema(description = "我的中标金额币种（供应商视角列表填充）")
    private String myAwardedCurrency;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    /** 实体 → VO（不签名版）：附件字段保留库中原值。需要可访问地址请用 {@link #fromEntity(Inquiry, OssService)}。 */
    public static InquiryVO fromEntity(Inquiry i) {
        return fromEntity(i, null);
    }

    /**
     * 实体 → VO（签名版）：按 oss_key 现签短效 URL 填充附件字段。
     * <p>库里存 key —— 签名会过期，持久化后必然变死链；每次查询现签，前端始终拿到新鲜地址。
     *
     * @param i        实体
     * @param ossService OSS 服务；为 null 时不签名
     */
    public static InquiryVO fromEntity(Inquiry i, OssService ossService) {
        InquiryVO vo = new InquiryVO();
        vo.setId(i.getId());
        vo.setInquiryNo(i.getInquiryNo());
        vo.setRequirementId(i.getRequirementId());
        vo.setConnectionId(i.getConnectionId());
        vo.setBuyerTenantId(i.getBuyerTenantId());
        vo.setTitle(i.getTitle());
        vo.setCurrency(i.getCurrency());
        vo.setCategory(i.getCategory());
        vo.setQuantity(i.getQuantity());
        vo.setUnit(i.getUnit());
        vo.setDeliveryPlace(i.getDeliveryPlace());
        vo.setDeliveryDate(i.getDeliveryDate());
        vo.setQuoteDeadline(i.getQuoteDeadline());
        vo.setPaymentTerms(i.getPaymentTerms());
        vo.setSpecText(i.getSpecText());
        vo.setAttachmentUrl(signAttachment(i.getAttachmentKey(), ossService));
        // 同时回传原始 key，前端编辑时回填可直接再次提交（URL 会过期，不能回传）
        vo.setAttachmentKey(i.getAttachmentKey());
        vo.setStatus(i.getStatus());
        vo.setWinningQuoteId(i.getWinningQuoteId());
        vo.setAwardedAt(i.getAwardedAt());
        vo.setAwardReason(i.getAwardReason());
        vo.setCreateTime(i.getCreateTime());
        vo.setUpdateTime(i.getUpdateTime());
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
