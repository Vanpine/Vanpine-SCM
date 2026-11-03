package cn.greensmartplanet.gsc.delivery.projection;

import lombok.Data;

/**
 * 履约统计投影 — selectPerformanceStat 的类型安全返回
 * <p>
 * 纯计数内部使用（回写企业画像），不经 Controller 出 JSON，无需 ID 字符串化。
 *
 * @author gsc
 */
@Data
public class PerformanceStatRow {

    /** 样本批次数（已验收非补发） */
    private Long sampleSize;

    /** 验收合格批次数（accepted/partial） */
    private Long passCount;

    /** 退回批次数 */
    private Long returnCount;

    /** 准时批次数（发货 ≤ 承诺/合同交期） */
    private Long onTimeCount;
}
