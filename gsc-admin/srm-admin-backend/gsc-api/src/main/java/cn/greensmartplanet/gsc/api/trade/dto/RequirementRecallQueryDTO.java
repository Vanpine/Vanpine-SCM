package cn.greensmartplanet.gsc.api.trade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 找需求 SQL 召回入参 — 供应商域一次只带一个维度（品类或地域）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequirementRecallQueryDTO {

    /** 排除的发布方（当前供应商自己发的需求） */
    private Long excludeTenantId;
    /** 品类 token；空则不按品类滤 */
    private List<String> categoryTokens;
    /** 供应商所在省/市；空则不按地域滤 */
    private String region;
    /** 条数上限 */
    private Integer limit;
}
