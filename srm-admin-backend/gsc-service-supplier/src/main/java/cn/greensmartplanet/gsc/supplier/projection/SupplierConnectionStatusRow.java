package cn.greensmartplanet.gsc.supplier.projection;

import lombok.Data;

/**
 * 供应商对接状态统计投影 — selectStatusBySupplierIds 的类型安全返回
 * <p>
 * 替代原 {@code List<Map<String,Object>>}（supplierTenantId → status/connectionCount）。
 *
 * @author gsc
 */
@Data
public class SupplierConnectionStatusRow {

    /** 供应商租户ID */
    private Long supplierTenantId;

    /** 最佳对接状态：accepted &gt; pending &gt; rejected；无记录时 null */
    private String status;

    /** 已接受对接数 */
    private Integer connectionCount;
}
