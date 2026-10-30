package cn.greensmartplanet.gsc.api.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 交付履约时间线契约 DTO — AI 工具 get_delivery_detail 的 traces 元素
 * <p>
 * 记录交付单每次状态流转（发货/收货/验收/退回），是谁在何时做了什么。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDeliveryTraceDTO {

    /** 操作类型：create/ship/receive/accept/return/correct_tracking */
    private String action;

    /** 操作描述 */
    private String actionDesc;

    /** 操作方租户ID */
    private Long operatorTenantId;

    /** 备注（退回原因/验收意见） */
    private String remark;

    /** 操作时间 */
    private LocalDateTime createTime;
}
