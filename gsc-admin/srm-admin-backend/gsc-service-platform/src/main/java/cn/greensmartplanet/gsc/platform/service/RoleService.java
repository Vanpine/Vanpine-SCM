package cn.greensmartplanet.gsc.platform.service;

import cn.greensmartplanet.gsc.platform.dto.CreateRoleDTO;
import cn.greensmartplanet.gsc.platform.dto.UpdateRoleDTO;
import cn.greensmartplanet.gsc.platform.entity.Permission;
import cn.greensmartplanet.gsc.platform.entity.Role;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;
import java.util.Map;

public interface RoleService {

    /**
     * 分页列表
     */
    IPage<Role> listRoles(int pageNum, int pageSize, Long tenantId, String roleName, String roleCode);

    /**
     * 全部角色
     */
    List<Role> listAll(Long tenantId);

    /**
     * 详情（含 permIds + menuIds）
     */
    Map<String, Object> getDetail(Long roleId);

    /**
     * 新增
     */
    Role create(Long tenantId, Long createBy, CreateRoleDTO dto);

    /**
     * 编辑
     */
    void update(Long roleId, UpdateRoleDTO dto);

    /**
     * 删除
     */
    void delete(Long roleId);

    /**
     * 分配权限（全量替换），校验可分配范围，变更后强制拥有该角色的用户重新登录
     *
     * @param roleId        角色ID
     * @param permIds       权限ID列表
     * @param excludeUserId 当前操作者，不踢
     * @param superAdmin    当前操作者是否超管（决定可分配范围）
     */
    void assignPermissions(Long roleId, List<Long> permIds, Long excludeUserId, boolean superAdmin);

    /**
     * 分配菜单（全量替换），校验可分配范围，变更后强制拥有该角色的用户重新登录
     *
     * @param roleId        角色ID
     * @param menuIds       菜单ID列表
     * @param excludeUserId 当前操作者，不踢
     * @param superAdmin    当前操作者是否超管（决定可分配范围）
     */
    void assignMenus(Long roleId, List<Long> menuIds, Long excludeUserId, boolean superAdmin);

    /**
     * 权限列表 — 非超管只返回可分配的权限（排除 *:*:* / admin / tenant / menu）
     *
     * @param superAdmin 是否超管
     */
    List<Permission> listPermissions(boolean superAdmin);

    /**
     * 查单个角色（不加租户过滤，供 Controller 校验用）
     */
    Role getById(Long roleId);
}
