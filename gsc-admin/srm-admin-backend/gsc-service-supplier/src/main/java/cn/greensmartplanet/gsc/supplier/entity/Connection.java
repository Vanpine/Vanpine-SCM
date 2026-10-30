package cn.greensmartplanet.gsc.supplier.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 对接意向记录实体
 * <p>
 * 采购方对匹配到的供应商发起对接意向，供应商可接受或拒绝。
 * 状态机：pending → accepted / rejected / closed
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("connection")
public class Connection {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联需求ID */
    private Long requirementId;

    /** 采购方租户ID */
    private Long buyerTenantId;

    /** 供应方租户ID */
    private Long supplierTenantId;

    /** 状态：pending/accepted/rejected/closed */
    private String status;

    /** 买方留言 */
    private String message;

    /** 供应商回复 */
    private String replyMessage;

    /** 发起人用户ID */
    private Long createBy;

    /** 更新人用户ID */
    private Long updateBy;

    // ---- 审计 ----
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;
}
