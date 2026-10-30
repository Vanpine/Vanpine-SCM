package cn.greensmartplanet.gsc.platform.mapper;

import cn.greensmartplanet.gsc.platform.entity.Menu;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 菜单 Mapper
 *
 * @author gsc
 */
@Mapper
public interface MenuMapper extends BaseMapper<Menu> {

    /** 查用户有权限的所有菜单 */
    List<Menu> selectByUserId(@Param("userId") Long userId);

    /** 查所有未删除的菜单（按 parent_id + sort_order 排序） */
    List<Menu> selectAll();

    /** 新增菜单，返回自增主键 */
    int insertMenu(Menu menu);

    /** 更新菜单 */
    int updateMenu(Menu menu);

    /** 逻辑删除菜单 */
    int softDelete(@Param("id") Long id);

    /** 查某菜单下的子菜单数量 */
    int countByParentId(@Param("parentId") Long parentId);

    /** 自动给 SUPER_ADMIN 分配权限码（INSERT IGNORE，幂等） */
    int insertRolePermissionForSuperAdmin(@Param("permCode") String permCode);

    /** 自动给 SUPER_ADMIN 分配菜单（INSERT IGNORE，幂等） */
    int insertRoleMenuForSuperAdmin(@Param("menuId") Long menuId);
}
