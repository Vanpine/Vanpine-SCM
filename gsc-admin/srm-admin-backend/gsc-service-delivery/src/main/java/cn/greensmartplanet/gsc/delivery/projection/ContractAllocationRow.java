package cn.greensmartplanet.gsc.delivery.projection;

import lombok.Data;

/**
 * 合同分配量聚合投影 — 可建交付单合同判定的本域半成品
 * <p>
 * selectCreatableContracts 原 SQL 直接 JOIN 交易域 contract 表算「每个合同的已分配量/待发批可让出量」，
 * 属跨域违规。改造后 delivery 只对本域 delivery 表做聚合（按合同ID分组），
 * 合同信息由 trade 的 {@code listSignedBySupplier} Feign 契约提供，在 service 层 Java 组装。
 * <p>
 * 字段名与 SQL 别名一致（驼峰），无雪花主键，无需 @JsonSerialize。
 *
 * @author gsc
 */
@Data
public class ContractAllocationRow {

    /** 合同ID（仅作聚合分组键与 Feign 结果关联用，不直接出参） */
    private Long contractId;

    /** 该合同已分配数量（delivery 表 quantity 累计，排除补发批） */
    private Long allocatedQty;

    /** 该合同待发货批次的最大可让出数量（status=pending_shipment 的 quantity 最大值） */
    private Long maxPendingQty;
}
