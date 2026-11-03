package cn.greensmartplanet.gsc.delivery.vo;

import cn.greensmartplanet.gsc.delivery.entity.Delivery;
import cn.greensmartplanet.gsc.framework.oss.OssService;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 交付单 VO
 *
 * @author gsc
 */
@Data
@Schema(description = "交付单")
public class DeliveryVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "交付单号")
    private String deliveryNo;

    @Schema(description = "关联合同ID")
    private Long contractId;

    @Schema(description = "合同编号")
    private String contractNo;

    @Schema(description = "合同标题")
    private String contractTitle;

    @Schema(description = "关联询盘ID")
    private Long inquiryId;

    @Schema(description = "询盘编号")
    private String inquiryNo;

    @Schema(description = "批次号，同一合同下从 1 递增")
    private Integer batchNo;

    @Schema(description = "采购方租户ID")
    private Long buyerTenantId;

    @Schema(description = "采购方企业名")
    private String buyerCompanyName;

    @Schema(description = "供应方租户ID")
    private Long supplierTenantId;

    @Schema(description = "供应方企业名")
    private String supplierCompanyName;

    @Schema(description = "本批应发数量")
    private Integer quantity;

    @Schema(description = "本批金额")
    private BigDecimal amount;

    @Schema(description = "币种")
    private String currency;

    @Schema(description = "交付条款")
    private String deliveryTerms;

    @Schema(description = "交货地点")
    private String deliveryPlace;

    @Schema(description = "交付日期")
    private LocalDate deliveryDate;

    @Schema(description = "已确认承诺交期（考核基准；无则 null，展示回退合同交期）")
    private LocalDate confirmedDeliveryDate;

    @Schema(description = "违约金延迟天数（验收时计算，≤0 不罚）")
    private Integer penaltyDays;

    @Schema(description = "违约金金额")
    private BigDecimal penaltyAmount;

    @Schema(description = "违约金计算时间")
    private LocalDateTime penaltyCalculatedAt;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "物流公司")
    private String shipCompany;

    @Schema(description = "运单号")
    private String shipTrackingNo;

    @Schema(description = "发货数量")
    private Integer shipQty;

    @Schema(description = "发货日期")
    private LocalDate shipDate;

    @Schema(description = "发货凭证访问 URL（由 shipAttachmentKey 现签）")
    private String shipAttachmentUrl;

    @Schema(description = "发货时间")
    private LocalDateTime shipTime;

    @Schema(description = "收货时间")
    private LocalDateTime receiveTime;

    @Schema(description = "实收数量")
    private Integer receiveQty;

    @Schema(description = "收货差异说明")
    private String receiveDiffRemark;

    @Schema(description = "到货照片访问 URL（由 receiveAttachmentKey 逗号分隔现签）")
    private String receiveAttachmentUrl;

    @Schema(description = "验收结果")
    private String acceptResult;

    @Schema(description = "验收时间")
    private LocalDateTime acceptTime;

    @Schema(description = "验收备注")
    private String acceptRemark;

    @Schema(description = "残次数量（验收部分残次时原批记录）")
    private Integer defectiveQty;

    @Schema(description = "补发来源交付单ID（补发批指向原批，普通批为 NULL）")
    private Long reshipFrom;

    @Schema(description = "退回原因")
    private String returnReason;

    @Schema(description = "退回凭证访问 URL（由 returnAttachmentKey 现签）")
    private String returnAttachmentUrl;

    @Schema(description = "退货物流公司")
    private String returnShipCompany;

    @Schema(description = "退货单号")
    private String returnShipTrackingNo;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "最新物流事件类型（物流追踪列表列展示）")
    private String latestLogisticsEvent;

    @Schema(description = "最新预计到货时间 ETA（物流追踪列表列展示）")
    private LocalDateTime latestEta;

    @Schema(description = "待确认承诺记录ID（无则 null，采购方物流追踪列表标记用）")
    private Long pendingPromiseId;

    @Schema(description = "待确认承诺交期")
    private LocalDate pendingPromisedDate;

    /** 实体 → VO（不签名版）：附件字段保留库中原值。需要可访问地址请用 {@link #fromEntity(Delivery, OssService)}。 */
    public static DeliveryVO fromEntity(Delivery d) {
        return fromEntity(d, null);
    }

    /**
     * 实体 → VO（签名版）：按 oss_key 现签短效 URL 填充附件字段。
     * <p>库里存 key —— 签名会过期，持久化后必然变死链；每次查询现签，前端始终拿到新鲜地址。
     *
     * @param d          Delivery 实体
     * @param ossService OSS 服务；为 null 时不签名
     */
    public static DeliveryVO fromEntity(Delivery d, OssService ossService) {
        DeliveryVO vo = new DeliveryVO();
        vo.setId(d.getId());
        vo.setDeliveryNo(d.getDeliveryNo());
        vo.setContractId(d.getContractId());
        vo.setInquiryId(d.getInquiryId());
        vo.setBatchNo(d.getBatchNo());
        vo.setBuyerTenantId(d.getBuyerTenantId());
        vo.setSupplierTenantId(d.getSupplierTenantId());
        vo.setQuantity(d.getQuantity());
        vo.setAmount(d.getAmount());
        vo.setCurrency(d.getCurrency());
        vo.setDeliveryTerms(d.getDeliveryTerms());
        vo.setDeliveryPlace(d.getDeliveryPlace());
        vo.setDeliveryDate(d.getDeliveryDate());
        vo.setConfirmedDeliveryDate(d.getConfirmedDeliveryDate());
        vo.setPenaltyDays(d.getPenaltyDays());
        vo.setPenaltyAmount(d.getPenaltyAmount());
        vo.setPenaltyCalculatedAt(d.getPenaltyCalculatedAt());
        vo.setStatus(d.getStatus());
        vo.setShipCompany(d.getShipCompany());
        vo.setShipTrackingNo(d.getShipTrackingNo());
        vo.setShipQty(d.getShipQty());
        vo.setShipDate(d.getShipDate());
        vo.setShipAttachmentUrl(signAttachment(d.getShipAttachmentKey(), ossService));
        vo.setShipTime(d.getShipTime());
        vo.setReceiveTime(d.getReceiveTime());
        vo.setReceiveQty(d.getReceiveQty());
        vo.setReceiveDiffRemark(d.getReceiveDiffRemark());
        vo.setReceiveAttachmentUrl(signAttachmentList(d.getReceiveAttachmentKey(), ossService));
        vo.setAcceptResult(d.getAcceptResult());
        vo.setAcceptTime(d.getAcceptTime());
        vo.setAcceptRemark(d.getAcceptRemark());
        vo.setDefectiveQty(d.getDefectiveQty());
        vo.setReshipFrom(d.getReshipFrom());
        vo.setReturnReason(d.getReturnReason());
        vo.setReturnAttachmentUrl(signAttachment(d.getReturnAttachmentKey(), ossService));
        vo.setReturnShipCompany(d.getReturnShipCompany());
        vo.setReturnShipTrackingNo(d.getReturnShipTrackingNo());
        vo.setCreateTime(d.getCreateTime());
        return vo;
    }

    /**
     * 附件现签：按 oss_key 生成短效 URL。
     * <p>库里只存 key —— 签名 URL 会过期，持久化后必然变死链。
     *
     * @param key        OSS 对象路径
     * @param ossService OSS 服务；为 null 时返回原 key
     * @return 可访问 URL；key 为空返回 null
     */
    private static String signAttachment(String key, OssService ossService) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return ossService != null ? ossService.generateGetUrl(key) : key;
    }

    /**
     * 多值附件现签：{@code receiveAttachmentKey} 是逗号分隔的多个 oss_key
     * （到货照片可传多张）。逐项现签后用逗号拼回，保持前端 getAttachmentList 的解析约定。
     *
     * @param keys       逗号分隔的 OSS key 串
     * @param ossService OSS 服务
     * @return 逗号分隔的签名 URL 串；为空返回 null
     */
    private static String signAttachmentList(String keys, OssService ossService) {
        if (keys == null || keys.isBlank()) {
            return null;
        }
        String[] parts = keys.split(",");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(signAttachment(parts[i].trim(), ossService));
        }
        return sb.toString();
    }
}
