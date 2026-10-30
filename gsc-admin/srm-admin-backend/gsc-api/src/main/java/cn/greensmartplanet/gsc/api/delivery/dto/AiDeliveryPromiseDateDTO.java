package cn.greensmartplanet.gsc.api.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 交付承诺交期契约 DTO — AI 工具 get_delivery_detail 的 promiseDates 元素
 * <p>
 * 供应商提交新承诺交期的历史记录（append-only），采购方确认/拒绝。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDeliveryPromiseDateDTO {

    /** 承诺记录ID */
    private Long id;

    /** 承诺交期 */
    private LocalDate promisedDate;

    /** 原因 */
    private String reason;

    /** 状态：pending 待确认 / accepted 已确认 / rejected 已拒绝 */
    private String status;

    /** 提交时间 */
    private LocalDateTime createTime;
}
