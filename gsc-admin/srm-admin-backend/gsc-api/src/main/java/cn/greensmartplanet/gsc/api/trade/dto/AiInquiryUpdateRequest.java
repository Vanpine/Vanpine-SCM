package cn.greensmartplanet.gsc.api.trade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 询盘字段修改请求契约 DTO — AI 写工具 update_inquiry 二次确认后由 AI 域调用
 * <p>
 * 部分更新语义——只 set 非 null 字段，
 * 防止模型只说「改交期」却把其他字段清空（现有 update 是全覆盖语义，不可直接复用）。
 * <p>
 * 安全边界：只放开交期/数量/付款条件/规格/标题/品类/单位等业务字段；
 * requirementId / connectionId / attachmentKey 等结构性/关联字段不放开，AI 不可乱改。
 * 报价截止时间（quoteDeadline）不在此列，改截止时间请用 extend_inquiry_deadline 工具
 * （走 extendDeadline，支持 open/quoting/expired，语义与「延长报价截止」一致）。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiInquiryUpdateRequest {

    /** 询盘ID（来自二次确认提案） */
    private Long inquiryId;

    /** 操作者租户ID（AI 域从登录态注入，归属校验键） */
    private Long tenantId;

    /** 操作者用户ID（AI 域从登录态注入，审计归属） */
    private Long userId;

    /** 询盘标题（可空=不改） */
    private String title;

    /** 采购品类（可空=不改） */
    private String category;

    /** 采购数量（可空=不改） */
    private BigDecimal quantity;

    /** 单位（可空=不改） */
    private String unit;

    /** 交货地点（可空=不改） */
    private String deliveryPlace;

    /** 期望交付日期（可空=不改） */
    private LocalDate deliveryDate;

    /** 付款条件（可空=不改） */
    private String paymentTerms;

    /** 规格条款（可空=不改） */
    private String specText;
}
