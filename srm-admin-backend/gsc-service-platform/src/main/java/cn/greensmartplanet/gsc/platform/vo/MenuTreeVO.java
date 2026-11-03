package cn.greensmartplanet.gsc.platform.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单树节点 — 递归结构，前端直接渲染成导航栏
 *
 * @author gsc
 */
@Data
@Schema(description = "菜单树节点")
public class MenuTreeVO {

    @Schema(description = "菜单ID")
    private Long id;

    @Schema(description = "父菜单ID")
    private Long parentId;

    @Schema(description = "菜单名称")
    private String menuName;

    @Schema(description = "图标")
    private String menuIcon;

    @Schema(description = "路由地址")
    private String menuPath;

    @Schema(description = "关联权限标识")
    private String permCode;

    @Schema(description = "是否可见")
    private Boolean isVisible;

    @Schema(description = "是否缓存")
    private Boolean isCache;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "子菜单列表")
    private List<MenuTreeVO> children = new ArrayList<>();
}
