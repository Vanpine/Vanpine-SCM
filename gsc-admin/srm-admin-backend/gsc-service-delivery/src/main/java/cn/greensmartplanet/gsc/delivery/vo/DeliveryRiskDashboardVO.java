package cn.greensmartplanet.gsc.delivery.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 供应保障驾驶舱聚合 VO — 一行一个交付单
 * <p>
 * 驾驶舱回答「哪些交付单有风险、多严重」，因此按交付单聚合（而非逐风险行）：
 * 一个交付单的多条活跃风险合并为一行，风险类型用标签组展示、等级取最高、状态取最严重、
 * 风险分取最高。补救工作台仍按风险维度列表（工单/8D 按风险挂载），两者回答的问题不同。
 *
 * @author gsc
 */
@Data
@Schema(description = "供应保障驾驶舱聚合行（一行一个交付单）")
public class DeliveryRiskDashboardVO {

    @Schema(description = "主风险ID（等级最高那条，跳详情用）")
    private Long id;

    @Schema(description = "关联交付单ID")
    private Long deliveryId;

    @Schema(description = "交付单号")
    private String deliveryNo;

    @Schema(description = "关联合同ID")
    private Long contractId;

    @Schema(description = "合同标题")
    private String contractTitle;

    @Schema(description = "采购方企业名")
    private String buyerCompanyName;

    @Schema(description = "供应方企业名")
    private String supplierCompanyName;

    @Schema(description = "风险等级（取最高：high > medium > low）")
    private String riskLevel;

    @Schema(description = "风险状态（取最严重那条）")
    private String riskStatus;

    @Schema(description = "风险分（取最高）")
    private Integer riskScore;

    @Schema(description = "预计到货时间（主风险快照）")
    private LocalDateTime estimatedArrivalTime;

    @Schema(description = "合同约定交期（主风险快照）")
    private LocalDate promisedDeliveryDate;

    @Schema(description = "活跃风险条数")
    private Integer riskCount;

    @Schema(description = "活跃风险类型列表（去重，标签组展示）")
    private List<String> riskTypes;

    /** SQL GROUP_CONCAT 原始逗号串（服务层拆分为 riskTypes 后不再对外输出） */
    @JsonIgnore
    private String riskTypesRaw;
}
