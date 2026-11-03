package cn.greensmartplanet.gsc.supplier.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 尽调核查清单 — 事务所对企业尽调项的逐项核查记录
 *
 * @author gsc
 */
@Data
@TableName("dd_checklist")
public class DdChecklist {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联 due_diligence.id */
    private Long ddId;

    /** 核查项编码 */
    private String itemCode;

    /** 核查项名称 */
    private String itemName;

    /** 核查项分组名（对齐诺臣 8 大类，如"一、公司基本情况"） */
    private String groupName;

    /** 是否通过 */
    private Boolean checked;

    /** 核查备注 */
    private String remark;

    /** 创建人用户ID */
    private Long createBy;

    /** 更新人用户ID */
    private Long updateBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;
}
