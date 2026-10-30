package cn.greensmartplanet.gsc.delivery.projection;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 发货排期投影 — selectShipmentPlan 的类型安全返回
 * <p>
 * ID 字段保留 Long→String 序列化（雪花主键精度），其余数值用真实类型（JSON number）。
 * 买方企业名由 Service 层 Feign 批量回填（buyerCompanyName 非 DB 列）。
 *
 * @author gsc
 */
@Data
public class ShipmentPlanRow {

    /** 分批计划ID（雪花主键，序列化为字符串） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long batchPlanId;

    /** 合同ID（雪花主键，序列化为字符串） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contractId;

    /** 批次号 */
    private Integer batchNo;

    /** 计划数量 */
    private Integer planQty;

    /** 计划交期 */
    private LocalDate planDeliveryDate;

    /** 计划备注 */
    private String planRemark;

    /** 合同编号 */
    private String contractNo;

    /** 合同标题 */
    private String contractTitle;

    /** 采购方租户ID（雪花主键，序列化为字符串） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long buyerTenantId;

    /** 交货地 */
    private String deliveryPlace;

    /** 交货条款 */
    private String deliveryTerms;

    /** 币种 */
    private String currency;

    /** 合同金额 */
    private BigDecimal contractAmount;

    /** 交付单ID（雪花主键，序列化为字符串；未发货时 null） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long deliveryId;

    /** 交付单号 */
    private String deliveryNo;

    /** 交付单状态（pending_shipment/shipped/received/...；未发货时 null） */
    private String deliveryStatus;

    /** 本批发货数量 */
    private Integer deliveryQty;

    /** 发货物流公司 */
    private String shipCompany;

    /** 运单号 */
    private String shipTrackingNo;

    /** 实发数量 */
    private Integer shipQty;

    /** 发货日期 */
    private LocalDate shipDate;

    /** 发货凭证 */
    private String shipAttachmentKey;

    /** 实收数量 */
    private Integer receiveQty;

    /** 退回原因 */
    private String returnReason;

    /** 交付单创建时间 */
    private LocalDateTime deliveryCreateTime;

    /** 采购方企业名（Service 层 Feign 回填） */
    private String buyerCompanyName;
}
