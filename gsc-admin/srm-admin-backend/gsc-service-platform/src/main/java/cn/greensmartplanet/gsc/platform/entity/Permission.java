package cn.greensmartplanet.gsc.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 权限实体 — 平台级定义，所有租户共享
 *
 * @author gsc
 */
@Data
@TableName("permission")
public class Permission {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;            // 主键

    private String permCode;    // 权限标识，如 user:create
    private String permName;    // 权限名称
    private Integer sortOrder;  // 排序

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;   // 创建时间

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;   // 更新时间

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;          // 逻辑删除 0=未删 1=已删
}
