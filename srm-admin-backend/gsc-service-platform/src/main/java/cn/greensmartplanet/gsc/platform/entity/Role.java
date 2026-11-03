package cn.greensmartplanet.gsc.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色实体
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("role")
public class Role {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;      // 所属租户ID
    private String roleCode;    // 角色编码（租户内唯一）
    private String roleName;    // 角色名称
    private String roleDesc;    // 角色描述
    private Boolean isSystem;   // 是否系统内置（不可删）
    private String roleType;    // 角色身份：buyer/supplier/both/platform
    private Integer sortOrder;  // 排序
    private Long createBy;      // 创建人ID
    private Long updateBy;      // 更新人ID

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;   // 创建时间

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;   // 更新时间

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;          // 逻辑删除 0=未删 1=已删

    /**
     * 已分配的菜单ID列表（非数据库字段，列表查询时填充）
     */
    @TableField(exist = false)
    private List<Long> menuIds;
}
