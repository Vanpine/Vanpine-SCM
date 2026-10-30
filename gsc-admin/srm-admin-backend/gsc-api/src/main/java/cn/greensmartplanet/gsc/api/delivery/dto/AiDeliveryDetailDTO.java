package cn.greensmartplanet.gsc.api.delivery.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 交付单详情契约 DTO — 交付域内部接口返回给 AI 工具（get_delivery_detail）的完整字段
 * <p>
 * 在摘要字段基础上追加履约时间线 / 物流事件 / 承诺记录 / 退货信息，
 * 供 AI 回答「这单履约走到哪一步 / 有无退货 / 承诺交期历史」。
 *
 * @author gsc
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AiDeliveryDetailDTO extends AiDeliverySummaryDTO {

    /** 履约时间线（append-only 操作日志，时间正序） */
    private List<AiDeliveryTraceDTO> traces;

    /** 物流事件（append-only 物流节点） */
    private List<AiDeliveryLogisticsEventDTO> logisticsEvents;

    /** 承诺交期记录（append-only 全量历史） */
    private List<AiDeliveryPromiseDateDTO> promiseDates;

    /** 退货原因（验收不合格时） */
    private String returnReason;

    /** 退货物流公司 */
    private String returnShipCompany;

    /** 退货单号 */
    private String returnShipTrackingNo;

    /** 残次数量（部分残次时） */
    private Integer defectiveQty;

    /** 补发来源交付单ID（补发批指向原批） */
    private Long reshipFrom;

    /** 验收备注 */
    private String acceptRemark;

    /** 验收时间 */
    private java.time.LocalDateTime acceptTime;
}
