package cn.greensmartplanet.gsc.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 合同分批计划实体
 * <p>
 * 采购方创建合同时预设的分批交付计划，合同签署后按此生成对应批次的交付单。
 * 一个合同多条记录，以 batch_no 从 1 递增区分，各批 quantity 之和等于合同履约数量。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("contract_batch_plan")
public class ContractBatchPlan {

    @TableId(type = IdType.ASSIGN_ID)
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

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;
}
