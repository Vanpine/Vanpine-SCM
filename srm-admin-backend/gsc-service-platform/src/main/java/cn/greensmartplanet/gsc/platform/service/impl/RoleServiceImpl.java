package cn.greensmartplanet.gsc.platform.service.impl;

import cn.greensmartplanet.gsc.common.constant.SystemConstants;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.platform.dto.CreateRoleDTO;
import cn.greensmartplanet.gsc.platform.dto.UpdateRoleDTO;
import cn.greensmartplanet.gsc.platform.entity.Menu;
import cn.greensmartplanet.gsc.platform.entity.Permission;
import cn.greensmartplanet.gsc.platform.entity.Role;
import cn.greensmartplanet.gsc.platform.mapper.MenuMapper;
import cn.greensmartplanet.gsc.platform.mapper.PermissionMapper;
import cn.greensmartplanet.gsc.platform.mapper.RoleMapper;
import cn.greensmartplanet.gsc.platform.mapper.UserMapper;
import cn.greensmartplanet.gsc.framework.security.PermissionCache;
import cn.greensmartplanet.gsc.framework.security.RefreshTokenStore;
import cn.greensmartplanet.gsc.platform.service.RoleService;
import cn.greensmartplanet.gsc.platform.support.MenuTreeCache;
import cn.greensmartplanet.gsc.platform.support.TenantGuard;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final UserMapper userMapper;
    private final MenuMapper menuMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final PermissionCache permissionCache;
    private final RefreshTokenStore refreshTokenStore;
    private final MenuTreeCache menuTreeCache;

    @Override
    public IPage<Role> listRoles(int pageNum, int pageSize, Long tenantId, String roleName, String roleCode) {
        IPage<Role> page = roleMapper.selectPageByTenant(
                new Page<>(pageNum, pageSize), tenantId, roleName, roleCode, null);
        // 为每个角色填充已分配的菜单ID
        page.getRecords().forEach(role ->
            role.setMenuIds(roleMapper.selectMenuIdsByRoleId(role.getId()))
        );
        return page;
    }

    /** 查租户下所有角色（下拉选择用） */
    @Override
    public List<Role> listAll(Long tenantId) {
        return roleMapper.selectByTenant(tenantId, null);
    }

    /** 角色详情（含permIds和menuIds） */
    @Override
    public Map<String, Object> getDetail(Long roleId) {
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException(ErrorCode.ROLE_NOT_FOUND);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("id", role.getId());
        result.put("tenantId", role.getTenantId());
        result.put("roleCode", role.getRoleCode());
        result.put("roleName", role.getRoleName());
        result.put("roleDesc", role.getRoleDesc());
        result.put("roleType", role.getRoleType());
        result.put("isSystem", role.getIsSystem());
        result.put("sortOrder", role.getSortOrder());
        result.put("permIds", roleMapper.selectPermIdsByRoleId(roleId));
        result.put("menuIds", roleMapper.selectMenuIdsByRoleId(roleId));
        return result;
    }

    /** 新增角色（校验编码租户内唯一） */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Role create(Long tenantId, Long createBy, CreateRoleDTO dto) {
        Role exist = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getTenantId, tenantId)  // 租户隔离
                .eq(Role::getRoleCode, dto.getRoleCode()));  // 校验编码唯一
        if (exist != null) {
            throw new BizException(ErrorCode.ROLE_CODE_EXISTS);  // 角色编码已被占用
        }
        Role role = Role.builder()
                .tenantId(tenantId)
                .roleCode(dto.getRoleCode())
                .roleName(dto.getRoleName())
                .roleDesc(dto.getRoleDesc())
                .roleType(dto.getRoleType() != null ? dto.getRoleType() : "custom")
                .isSystem(false)
                .createBy(createBy)
                .build();
        roleMapper.insert(role);
        return role;
    }

    /** 编辑角色（仅可修改名称和描述，编码不可改） */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long roleId, UpdateRoleDTO dto) {
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException(ErrorCode.ROLE_NOT_FOUND);
        }
        role.setRoleName(dto.getRoleName());
        role.setRoleDesc(dto.getRoleDesc());
        roleMapper.updateById(role);
    }

    /**
     * 删除角色（系统内置角色不可删）
     * <p>
     * 必须连带清理关联表：只软删 role 而留下 user_role/role_permission/role_menu，
     * 会导致该角色的权限继续对持有者生效（权限 SQL 若不过滤 role.is_deleted 就会复现）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long roleId) {
        Role role = roleMapper.selectById(roleId);  // 查角色是否存在
        if (role == null) {
            throw new BizException(ErrorCode.ROLE_NOT_FOUND);  // 角色不存在
        }
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new BizException(ErrorCode.SYSTEM_ROLE_CANNOT_DELETE);  // 系统内置角色不可删除
        }
        // 先收集持有者，删关联后据此清缓存、踢下线
        List<Long> userIds = userMapper.selectUserIdsByRoleId(roleId);
        roleMapper.deleteUserRolesByRoleId(roleId);  // 解除用户关联
        roleMapper.deleteRolePerms(roleId);          // 解除权限关联
        roleMapper.deleteRoleMenus(roleId);          // 解除菜单关联
        roleMapper.deleteById(roleId);               // 逻辑删除角色
        // 持有者权限与菜单已变，清缓存并强制重新登录
        userIds.forEach(uid -> {
            menuTreeCache.evict(uid);
            TenantGuard.forceLogout(redisTemplate, permissionCache, refreshTokenStore, uid);
        });
    }

    /** 分配权限（先删后插，全量替换，校验可分配范围，清除缓存并强制相关用户重新登录） */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(Long roleId, List<Long> permIds, Long excludeUserId, boolean superAdmin) {
        if (!superAdmin && permIds != null && !permIds.isEmpty()) {
            assertPermIdsAssignable(permIds);  // 非超管不得分配平台专属权限码，防提权
        }
        roleMapper.deleteRolePerms(roleId);
        if (permIds != null && !permIds.isEmpty()) {
            roleMapper.insertRolePerms(roleId, permIds);
        }
        clearPermissionCache(roleId, excludeUserId);
    }

    /** 分配菜单（先删后插，全量替换，校验可分配范围，清除缓存并强制相关用户重新登录） */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(Long roleId, List<Long> menuIds, Long excludeUserId, boolean superAdmin) {
        if (!superAdmin && menuIds != null && !menuIds.isEmpty()) {
            assertMenuIdsAssignable(menuIds, excludeUserId);  // 非超管不得分配自己没有的菜单
        }
        roleMapper.deleteRoleMenus(roleId);
        if (menuIds != null && !menuIds.isEmpty()) {
            roleMapper.insertRoleMenus(roleId, menuIds);
        }
        clearPermissionCache(roleId, excludeUserId);
    }

    /**
     * 校验传入的 permIds 是否都可分配：不含平台专属权限码（*:*:* / admin / tenant / menu）
     * <p>
     * 不信任前端传入的权限ID —— 否则租户管理员可自建角色并授予通配码，实现提权。
     */
    private void assertPermIdsAssignable(List<Long> permIds) {
        List<Permission> all = permissionMapper.selectAll();
        Map<Long, String> codeById = all.stream()
                .collect(Collectors.toMap(Permission::getId, Permission::getPermCode));
        boolean hasPlatformOnly = permIds.stream()
                .map(codeById::get)
                .anyMatch(code -> code != null && SystemConstants.PLATFORM_ONLY_PERM_CODES.contains(code));
        if (hasPlatformOnly) {
            throw new BizException(ErrorCode.FORBIDDEN);  // 含平台专属权限码
        }
    }

    /**
     * 校验传入的 menuIds 是否都可分配：必须落在当前操作者自己有权限的菜单集合内
     * <p>
     * 复用 listTreeByUserId 的口径（非超管只能看到自己有权限的菜单），
     * 防止前端伪造菜单ID越权分配。
     */
    private void assertMenuIdsAssignable(List<Long> menuIds, Long operatorId) {
        Set<Long> assignable = menuMapper.selectByUserId(operatorId).stream()
                .map(Menu::getId).collect(Collectors.toSet());
        if (!assignable.containsAll(menuIds)) {
            throw new BizException(ErrorCode.FORBIDDEN);  // 含自己无权限的菜单
        }
    }

    /** 查单个角色 */
    @Override
    public Role getById(Long roleId) {
        return roleMapper.selectById(roleId);
    }

    /** 查所有权限列表 — 非超管过滤掉平台专属权限码，避免暴露且不可分配 */
    @Override
    public List<Permission> listPermissions(boolean superAdmin) {
        List<Permission> all = permissionMapper.selectAll();
        if (superAdmin) {
            return all;
        }
        return all.stream()
                .filter(p -> !SystemConstants.PLATFORM_ONLY_PERM_CODES.contains(p.getPermCode()))
                .toList();
    }

    /**
     * 清除拥有该角色的所有用户的权限缓存与菜单树缓存；
     * 并删除其 Token 白名单，强制重新登录后拿到最新权限。
     * 当前操作者（excludeUserId）不踢，避免超管保存后自己被登出。
     */
    private void clearPermissionCache(Long roleId, Long excludeUserId) {
        List<Long> userIds = userMapper.selectUserIdsByRoleId(roleId);
        userIds.forEach(uid -> {
            menuTreeCache.evict(uid);  // 角色菜单可能变了，菜单树必须重建
            if (uid.equals(excludeUserId)) {
                // 操作者改自己所属角色：不能踢自己，但权限缓存必须失效
                permissionCache.invalidate(uid);
            } else {
                TenantGuard.forceLogout(redisTemplate, permissionCache, refreshTokenStore, uid);
            }
        });
    }
}
