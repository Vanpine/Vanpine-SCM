package cn.greensmartplanet.gsc.api.trade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 询盘摘要契约 DTO — 交易域内部接口返回给 AI 工具（list_inquiries）的字段子集
 * <p>
 * 供 AI 回答「我有哪些询盘 / 什么状态 / 何时截止」并据 inquiryId 定位后续写操作目标。
 * 雪花主键出参由全局 Long→String 配置统一序列化为字符串（与 {@link AiContractSummaryDTO} 一致）。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiInquirySummaryDTO {

    /** 询盘ID（雪花主键，序列化为字符串，写操作定位用） */
    private Long id;

    /** 询盘编号 RFQ20260803001 */
    private String inquiryNo;

    /** 询盘标题 */
    private String title;

    /** 采购品类 */
    private String category;

    /** 采购数量 */
    private BigDecimal quantity;

    /** 单位 */
    private String unit;

    /** 期望交付日期 */
    private LocalDate deliveryDate;

    /** 报价截止时间 */
    private LocalDateTime quoteDeadline;

    /** 付款条件 */
    private String paymentTerms;

    /** 状态：open/quoting/awarded/closed/expired/cancelled */
    private String status;

    /** 状态中文描述（待报价/报价中/已授标/已关闭/已过期/已取消），AI 回答展示用 */
    private String statusText;
}
