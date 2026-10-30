package cn.greensmartplanet.gsc.supplier.projection;

import lombok.Data;

/**
 * 对接数统计投影 — countGroupByRequirementIds 的类型安全返回
 * <p>
 * 替代原 {@code List<Map<String,Object>>}（requirementId → cnt）。
 *
 * @author gsc
 */
@Data
public class ConnectionCountRow {

    /** 需求ID */
    private Long requirementId;

    /** 已接受对接数 */
    private Long cnt;
}
