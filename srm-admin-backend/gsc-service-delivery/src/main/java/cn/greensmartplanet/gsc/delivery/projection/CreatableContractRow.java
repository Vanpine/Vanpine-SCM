package cn.greensmartplanet.gsc.delivery.projection;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 可建交付单合同投影 — selectCreatableContracts 的类型安全返回
 * <p>
 * ID 字段保留 Long→String 序列化（雪花主键精度），其余数值用真实类型（JSON number）。
 *
 * @author gsc
 */
@Data
public class CreatableContractRow {

    /** 合同ID（雪花主键，序列化为字符串） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contractId;

    /** 合同编号 */
    private String contractNo;

    /** 合同标题 */
    private String title;

    /** 合同总数量 */
    private Integer contractQty;

    /** 合同金额 */
    private BigDecimal amount;

    /** 币种 */
    private String currency;

    /** 交期 */
    private LocalDate deliveryDate;

    /** 交货地 */
    private String deliveryPlace;

    /** 已分配数量（交付单累计） */
    private Long allocatedQty;

    /** 待发货批次最大可让出数量 */
    private Long maxPendingQty;
}
