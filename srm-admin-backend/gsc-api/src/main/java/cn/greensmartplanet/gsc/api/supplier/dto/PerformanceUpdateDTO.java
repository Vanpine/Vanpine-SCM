package cn.greensmartplanet.gsc.api.supplier.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 履约表现回写契约 DTO — 交付域回写供应商画像履约字段的请求体
 * <p>
 * 交付终态触发，仅更新画像 5 个履约字段（准时率/验收合格率/退回批次数/综合履约分/样本批次数），
 * 避免全量覆盖画像其他字段。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceUpdateDTO {

    /** 供应商租户ID */
    private Long tenantId;
    /** 准时率（%） */
    private BigDecimal onTimeRate;
    /** 验收合格率（%） */
    private BigDecimal acceptPassRate;
    /** 退回批次数 */
    private Integer returnCount;
    /** 综合履约分（0-100） */
    private BigDecimal perfScore;
    /** 履约样本批次数（已验收普通批数） */
    private Integer perfSampleSize;
}
