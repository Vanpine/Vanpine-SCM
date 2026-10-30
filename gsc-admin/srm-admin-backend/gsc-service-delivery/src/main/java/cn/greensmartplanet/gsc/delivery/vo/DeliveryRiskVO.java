package cn.greensmartplanet.gsc.delivery.vo;

import cn.greensmartplanet.gsc.delivery.entity.DeliveryLogisticsEvent;
import cn.greensmartplanet.gsc.delivery.entity.DeliveryRisk;
import cn.greensmartplanet.gsc.delivery.entity.DeliveryRiskFactor;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 交付风险 VO — 列表/详情展示，含回填的合同/企业/交付单信息与物流事件时间线
 *
 * @author gsc
 */
@Data
@Schema(description = "交付风险")
public class DeliveryRiskVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "关联交付单ID")
    private Long deliveryId;

    @Schema(description = "交付单号")
    private String deliveryNo;

    @Schema(description = "关联合同ID")
    private Long contractId;

    @Schema(description = "合同编号")
    private String contractNo;

    @Schema(description = "合同标题")
    private String contractTitle;

    @Schema(description = "采购方企业名")
    private String buyerCompanyName;

    @Schema(description = "供应方企业名")
    private String supplierCompanyName;

    @Schema(description = "风险类型：late_shipment/logistics_stagnation/eta_delay/clearance_hold/quantity_shortage/weather_disruption/other")
    private String riskType;

    @Schema(description = "风险等级：low/medium/high")
    private String riskLevel;

    @Schema(description = "风险状态：active/warned/high/disposing/mitigated/closed")
    private String riskStatus;

    @Schema(description = "预计到货时间")
    private LocalDateTime estimatedArrivalTime;

    @Schema(description = "合同约定交期（快照）")
    private LocalDate promisedDeliveryDate;

    @Schema(description = "生产需求日期（快照）")
    private LocalDate productionNeedDate;

    @Schema(description = "影响数量（快照）")
    private Integer impactQty;

    @Schema(description = "影响金额（快照）")
    private BigDecimal impactAmount;

    @Schema(description = "风险分（0-100）")
    private Integer riskScore;

    @Schema(description = "触发证据")
    private String evidence;

    @Schema(description = "首次发现时间")
    private LocalDateTime firstDetectedTime;

    @Schema(description = "处置截止时间")
    private LocalDateTime dueTime;

    @Schema(description = "责任人用户ID")
    private Long ownerUserId;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "物流事件时间线（详情页展示，列表为空）")
    private List<DeliveryLogisticsEvent> events;

    @Schema(description = "风险因子历史（类型演变轨迹，详情页展示，列表为空）")
    private List<DeliveryRiskFactor> factors;

    @Schema(description = "天气 AI 建议状态：generating/ready/fallback，非天气风险为空")
    private String adviceStatus;

    @Schema(description = "天气 AI 处置建议")
    private List<String> adviceLines;

    public static DeliveryRiskVO fromEntity(DeliveryRisk r) {
        DeliveryRiskVO vo = new DeliveryRiskVO();
        vo.setId(r.getId());
        vo.setDeliveryId(r.getDeliveryId());
        vo.setContractId(r.getContractId());
        vo.setRiskType(r.getRiskType());
        vo.setRiskLevel(r.getRiskLevel());
        vo.setRiskStatus(r.getRiskStatus());
        vo.setEstimatedArrivalTime(r.getEstimatedArrivalTime());
        vo.setPromisedDeliveryDate(r.getPromisedDeliveryDate());
        vo.setProductionNeedDate(r.getProductionNeedDate());
        vo.setImpactQty(r.getImpactQty());
        vo.setImpactAmount(r.getImpactAmount());
        vo.setRiskScore(r.getRiskScore());
        vo.setEvidence(r.getEvidence());
        vo.setFirstDetectedTime(r.getFirstDetectedTime());
        vo.setDueTime(r.getDueTime());
        vo.setOwnerUserId(r.getOwnerUserId());
        vo.setCreateTime(r.getCreateTime());
        return vo;
    }
}
