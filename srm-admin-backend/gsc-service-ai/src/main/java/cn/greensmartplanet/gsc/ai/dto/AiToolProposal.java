package cn.greensmartplanet.gsc.ai.dto;

import cn.greensmartplanet.gsc.api.trade.dto.AiInquiryUpdateRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * AI 写操作提案 — 模型发起写工具调用后，Java 不落库而是生成「待确认提案」暂存 Redis
 * <p>
 * 安全边界：模型只能生成提案，落库永远由用户在确认接口点击确认后触发；
 * tenantId/userId 由 Java 从登录态/tool token 注入，模型无法伪造归属。
 * 采用普通 DTO（非 record）便于 Jackson 直接序列化进 Redis。
 * <p>
 * 字段按工具分用：create_supplier_note 用 supplierTenantId/content；
 * update_inquiry 用 inquiryFields；extend_inquiry_deadline 用 newDeadline。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiToolProposal {

    /** 写工具名（如 create_supplier_note），确认时据此分发落库 */
    private String toolName;

    /** 发起提案的租户（归属校验键） */
    private Long tenantId;

    /** 发起提案的用户（归属校验键） */
    private Long userId;

    /** 被备注的供应商租户（create_supplier_note 专用） */
    private Long supplierTenantId;

    /** 备注内容（create_supplier_note 专用） */
    private String content;

    /** 被操作的询盘ID（extend_inquiry_deadline 专用） */
    private Long inquiryId;

    /** 询盘字段修改内容（update_inquiry 专用，部分更新字段子集） */
    private AiInquiryUpdateRequest inquiryFields;

    /** 新的报价截止时间（extend_inquiry_deadline 专用） */
    private LocalDateTime newDeadline;

    /** 被操作的交付单ID（submit_promise_date / fill_return_logistics 专用） */
    private Long deliveryId;

    /** 承诺交期（submit_promise_date 专用） */
    private LocalDate promisedDate;

    /** 承诺原因（submit_promise_date 专用，可空则交付域兜底默认值） */
    private String reason;

    /** 退货物流公司（fill_return_logistics 专用） */
    private String logisticsCompany;

    /** 退货单号（fill_return_logistics 专用） */
    private String trackingNo;

    /** 本轮 assistant 消息ID（流式阶段落库的占位，确认/取消后覆盖成最终文案） */
    private Long assistantMessageId;
}
