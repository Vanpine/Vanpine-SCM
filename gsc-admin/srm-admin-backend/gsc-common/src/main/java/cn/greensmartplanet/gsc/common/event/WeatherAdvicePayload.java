package cn.greensmartplanet.gsc.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 天气风险 AI 建议任务（delivery 自产自销）。
 * 消费端重查风险是否仍为天气异常，载荷只作触达。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherAdvicePayload {

    private Long riskId;
    private Long deliveryId;
    private String deliveryNo;
    private String place;
    private String evidence;
    private String summary;
    private String level;
    private String shipCompany;
    private String deliveryPlace;
    private String eta;
    private String promisedDate;
    private Integer impactQty;
}
