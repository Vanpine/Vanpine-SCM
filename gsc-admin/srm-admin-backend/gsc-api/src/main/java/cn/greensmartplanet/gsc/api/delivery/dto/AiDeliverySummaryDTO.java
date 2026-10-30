package cn.greensmartplanet.gsc.api.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 交付单摘要契约 DTO — 交付域内部接口返回给 AI 工具（list_deliveries）的字段子集
 * <p>
 * 供 AI 回答「我有哪些交付单 / 什么状态 / 何时到货」并据 id 定位后续写操作目标。
 * 雪花主键出参由全局 Long→String 配置统一序列化为字符串（与 {@link AiSupplierSummaryDTO} 一致）。
 * status 为英文 code（系统可读），statusText 为中文描述（AI 回答展示用，由交付域字典填充）。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDeliverySummaryDTO {

    /** 交付单ID（雪花主键，序列化为字符串，写操作定位用） */
    private Long id;

    /** 交付单号 */
    private String deliveryNo;

    /** 关联合同ID */
    private Long contractId;

    /** 合同编号 */
    private String contractNo;

    /** 合同标题 */
    private String contractTitle;

    /** 询盘编号 */
    private String inquiryNo;

    /** 采购方租户ID */
    private Long buyerTenantId;

    /** 采购方企业名 */
    private String buyerCompanyName;

    /** 供应方租户ID */
    private Long supplierTenantId;

    /** 供应方企业名 */
    private String supplierCompanyName;

    /** 状态：pending_shipment/shipped/received/accepted/return_pending/return_confirmed/return_shipping/returned/reship_pending */
    private String status;

    /** 状态中文描述（待发货/待收货/待验收/已验收/待供应商确认退货/待退回货物/退货中/已退回/待补单），AI 回答展示用 */
    private String statusText;

    /** 本批应发数量 */
    private Integer quantity;

    /** 物流公司 */
    private String shipCompany;

    /** 运单号 */
    private String shipTrackingNo;

    /** 合同交付日期 */
    private LocalDate deliveryDate;

    /** 已确认承诺交期（考核基准；无则 null） */
    private LocalDate confirmedDeliveryDate;

    /** 发货日期 */
    private LocalDate shipDate;

    /** 收货时间 */
    private LocalDateTime receiveTime;

    /** 验收结果 */
    private String acceptResult;

    /** 最新物流事件类型 */
    private String latestLogisticsEvent;

    /** 最新预计到货时间 ETA */
    private LocalDateTime latestEta;

    /** 待确认承诺记录ID（无则 null） */
    private Long pendingPromiseId;

    /** 待确认承诺交期 */
    private LocalDate pendingPromisedDate;

    /** 创建时间 */
    private LocalDateTime createTime;
}
