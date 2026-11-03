package cn.greensmartplanet.gsc.api.trade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 合同分批计划契约 DTO — 交易域内部接口返回给交付域的分批计划字段子集
 * <p>
 * 交付域使用场景（Feign 契约）：
 * <ul>
 *   <li>发货排期列表（id→batchPlanId、contractId/batchNo/quantity/deliveryDate/remark）</li>
 *   <li>合同完成聚合判断（batchNo 对比交付单已发批次）</li>
 *   <li>供方手动建单额度核算（分批计划应发数量）</li>
 * </ul>
 * <p>
 * 交易域现有 BatchPlanItem 缺少 batchNo（创建/编辑请求粒度），故独立定义契约 DTO，
 * batchNo 为交付域幂等/拼装必需字段。id 为分批计划雪花主键（交付域发货排期关联用，
 * 出参序列化由交付域 ShipmentPlanRow 承担，本 DTO 为内部传输载体不注解）。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractBatchPlanDTO {

    /** 分批计划ID（雪花主键 Long，交付域发货排期 batchPlanId 用） */
    private Long id;

    /** 关联合同ID */
    private Long contractId;

    /** 批次号，同一合同下从 1 递增 */
    private Integer batchNo;

    /** 本批数量 */
    private Integer quantity;

    /** 本批交货日期 */
    private LocalDate deliveryDate;

    /** 备注 */
    private String remark;
}
