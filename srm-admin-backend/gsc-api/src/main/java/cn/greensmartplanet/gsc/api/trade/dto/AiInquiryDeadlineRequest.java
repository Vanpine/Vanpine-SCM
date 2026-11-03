package cn.greensmartplanet.gsc.api.trade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 询盘延长截止请求契约 DTO — AI 写工具 extend_inquiry_deadline 二次确认后由 AI 域调用
 * <p>
 * 独立于 {@link AiInquiryUpdateRequest}：延长报价截止语义不同——它支持 open/quoting/expired
 * 三种状态（已过期询盘延长后按有无报价恢复 quoting/open），而修改字段仅限 open 状态。
 * 直接映射 trade 域的 extendDeadline 方法。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiInquiryDeadlineRequest {

    /** 询盘ID（来自二次确认提案） */
    private Long inquiryId;

    /** 操作者租户ID（AI 域从登录态注入，归属校验键） */
    private Long tenantId;

    /** 操作者用户ID（AI 域从登录态注入，审计归属） */
    private Long userId;

    /** 新的报价截止时间 */
    private LocalDateTime newDeadline;
}
