package cn.greensmartplanet.gsc.platform.mapper;

import cn.greensmartplanet.gsc.platform.entity.Role;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {

    /** 分页查租户下的角色（支持名称+编码模糊搜索 + role_type 过滤） */
    IPage<Role> selectPageByTenant(Page<Role> page,
                                   @Param("tenantId") Long tenantId,
                                   @Param("roleName") String roleName,
                                   @Param("roleCode") String roleCode,
                                   @Param("allowedRoleTypes") List<String> allowedRoleTypes);

    /** 查租户下所有角色（支持 role_type 过滤） */
    List<Role> selectByTenant(@Param("tenantId") Long tenantId,
                              @Param("allowedRoleTypes") List<String> allowedRoleTypes);

    /** 查角色已分配的权限ID列表 */
    List<Long> selectPermIdsByRoleId(@Param("roleId") Long roleId);

    /** 查角色已分配的菜单ID列表 */
    List<Long> selectMenuIdsByRoleId(@Param("roleId") Long roleId);

    /** 删除角色的所有权限关联 */
    void deleteRolePerms(@Param("roleId") Long roleId);

    /** 批量插入角色权限关联 */
    void insertRolePerms(@Param("roleId") Long roleId, @Param("permIds") List<Long> permIds);

    /** 删除角色的所有菜单关联 */
    void deleteRoleMenus(@Param("roleId") Long roleId);

    /** 删除角色的所有用户关联（删角色时调用，防止 user_role 残留导致权限复现） */
    void deleteUserRolesByRoleId(@Param("roleId") Long roleId);

    /** 批量插入角色菜单关联 */
    void insertRoleMenus(@Param("roleId") Long roleId, @Param("menuIds") List<Long> menuIds);
}
