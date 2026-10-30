package cn.greensmartplanet.gsc.api.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 交付写操作请求契约 DTO — AI 写工具二次确认后由 AI 域调用
 * <p>
 * 字段按工具分用：submit_promise_date 用 promisedDate/reason；
 * fill_return_logistics 用 logisticsCompany/trackingNo。
 * 安全边界：deliveryId 来自二次确认提案，tenantId / userId 由 AI 域从登录态注入
 * （模型不可伪造），交付域据此校验归属 + 状态前置后落库。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDeliveryOperateRequest {

    /** 交付单ID（来自二次确认提案） */
    private Long deliveryId;

    /** 操作者租户ID（AI 域从登录态注入，归属校验键） */
    private Long tenantId;

    /** 操作者用户ID（AI 域从登录态注入，审计归属） */
    private Long userId;

    /** 承诺交期（仅 submit_promise_date 用） */
    private LocalDate promisedDate;

    /** 承诺原因（仅 submit_promise_date 用，可空则适配层兜底默认值） */
    private String reason;

    /** 退货物流公司（仅 fill_return_logistics 用） */
    private String logisticsCompany;

    /** 退货单号（仅 fill_return_logistics 用） */
    private String trackingNo;
}
