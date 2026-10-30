package cn.greensmartplanet.gsc.delivery.vo;

import cn.greensmartplanet.gsc.delivery.entity.DeliveryRecoveryAction;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 补救工单 VO
 *
 * @author gsc
 */
@Data
@Schema(description = "补救工单")
public class RecoveryActionVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "关联风险ID")
    private Long riskId;

    @Schema(description = "动作类型")
    private String actionType;

    @Schema(description = "方案名称")
    private String actionName;

    @Schema(description = "方案说明")
    private String recommendation;

    @Schema(description = "预计恢复时间")
    private LocalDateTime expectedEta;

    @Schema(description = "预计成本")
    private BigDecimal expectedCost;

    @Schema(description = "可保障数量")
    private Integer expectedRecoveryQty;

    @Schema(description = "状态：pending/executing/completed/failed")
    private String status;

    @Schema(description = "责任人用户ID")
    private Long ownerUserId;

    @Schema(description = "截止时间")
    private LocalDateTime deadline;

    @Schema(description = "执行备注/回执")
    private String executionRemark;

    @Schema(description = "完成时间")
    private LocalDateTime completedTime;

    @Schema(description = "结果说明")
    private String result;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    public static RecoveryActionVO fromEntity(DeliveryRecoveryAction a) {
        RecoveryActionVO vo = new RecoveryActionVO();
        vo.setId(a.getId());
        vo.setRiskId(a.getRiskId());
        vo.setActionType(a.getActionType());
        vo.setActionName(a.getActionName());
        vo.setRecommendation(a.getRecommendation());
        vo.setExpectedEta(a.getExpectedEta());
        vo.setExpectedCost(a.getExpectedCost());
        vo.setExpectedRecoveryQty(a.getExpectedRecoveryQty());
        vo.setStatus(a.getStatus());
        vo.setOwnerUserId(a.getOwnerUserId());
        vo.setDeadline(a.getDeadline());
        vo.setExecutionRemark(a.getExecutionRemark());
        vo.setCompletedTime(a.getCompletedTime());
        vo.setResult(a.getResult());
        vo.setCreateTime(a.getCreateTime());
        return vo;
    }
}
