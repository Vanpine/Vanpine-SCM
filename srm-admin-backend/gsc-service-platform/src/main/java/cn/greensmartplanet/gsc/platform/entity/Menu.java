package cn.greensmartplanet.gsc.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 菜单实体 — 前端左侧导航栏的每一级菜单
 *
 * @author gsc
 */
@Data
@TableName("menu")
public class Menu {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long parentId;      // 父菜单ID（0 = 一级菜单）
    private String menuType;    // menu / button
    private String menuName;    // 菜单名称
    private String menuPath;    // 前端路由地址
    private String menuIcon;    // 图标
    private String permCode;    // 关联权限标识
    private Boolean isVisible;  // 是否可见
    private Boolean isCache;    // 是否缓存
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;
}
