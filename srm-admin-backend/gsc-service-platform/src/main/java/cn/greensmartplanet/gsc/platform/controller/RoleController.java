package cn.greensmartplanet.gsc.platform.controller;

import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.framework.audit.AuditLog;
import cn.greensmartplanet.gsc.framework.security.JwtUserDetails;
import cn.greensmartplanet.gsc.platform.dto.CreateRoleDTO;
import cn.greensmartplanet.gsc.platform.dto.UpdateRoleDTO;
import cn.greensmartplanet.gsc.platform.entity.Role;
import cn.greensmartplanet.gsc.api.supplier.ProfileClient;
import cn.greensmartplanet.gsc.api.supplier.dto.ProfileInfoDTO;
import cn.greensmartplanet.gsc.platform.service.MenuService;
import cn.greensmartplanet.gsc.platform.service.RoleService;
import cn.greensmartplanet.gsc.platform.support.TenantGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('*:*:*', 'admin', 'role')")
@Tag(name = "角色权限管理", description = "角色 CRUD、权限/菜单分配")
public class RoleController {

    /**
     * 角色管理页面对非超管完全隐藏（预审/事务所角色租户永不可见）
     */
    private static final Set<String> HIDDEN_FROM_TENANT =
            Set.of("DUE_DILIGENCE", "FIRM");
    private final RoleService roleService;  // 角色业务
    private final MenuService menuService;  // 菜单业务

    // ==================== 角色 CRUD ====================
    // 画像归供应商域，Feign 查询（角色按身份类型过滤）
    private final ProfileClient profileClient;

    /**
     * 角色分页列表（租户隔离，支持名称/编码筛选）
     * 预审用户(DUE_DILIGENCE)角色仅超管可见
     */
    @Operation(summary = "角色分页列表")
    @GetMapping("/roles")
    public R<Map<String, Object>> list(@RequestParam(defaultValue = "1") int pageNum,
                                       @RequestParam(defaultValue = "10") int pageSize,
                                       @RequestParam(required = false) String roleName,
                                       @RequestParam(required = false) String roleCode,
                                       @AuthenticationPrincipal JwtUserDetails user) {
        var page = roleService.listRoles(pageNum, pageSize, user.getTenantId(), roleName, roleCode);
        // 非超管过滤掉平台专属角色 + 单身份不显示其他身份角色
        boolean isSuperAdmin = user.getTenantId() == 1;
        if (!isSuperAdmin) {
            String pt = getProfileType(user.getTenantId());
            page.setRecords(page.getRecords().stream()
                    .filter(r -> r.getRoleCode() == null || !HIDDEN_FROM_TENANT.contains(r.getRoleCode()))
                    .filter(r -> !"TENANT_ADMIN".equals(r.getRoleCode()) || "both".equals(pt))
                    .filter(r -> !"BUYER_ADMIN".equals(r.getRoleCode()) || "buyer".equals(pt))
                    .filter(r -> !"SUPPLIER_ADMIN".equals(r.getRoleCode()) || "supplier".equals(pt))
                    .filter(r -> !"BUYER".equals(r.getRoleCode()) || "buyer".equals(pt) || "both".equals(pt))
                    .filter(r -> !"SUPPLIER".equals(r.getRoleCode()) || "supplier".equals(pt) || "both".equals(pt))
                    .toList());
        }
        Map<String, Object> result = new HashMap<>();
        result.put("records", page.getRecords());
        result.put("total", page.getTotal());
        result.put("page", page.getCurrent());
        result.put("pageSize", page.getSize());
        return R.ok(result);
    }

    /**
     * 全部角色（用户分配角色下拉选择用）
     * 预审用户(DUE_DILIGENCE)角色仅超管可见
     */
    @Operation(summary = "全部角色（下拉用）")
    @GetMapping("/roles/all")
    public R<List<Map<String, Object>>> listAll(@AuthenticationPrincipal JwtUserDetails user) {
        boolean isSuperAdmin = user.getTenantId() == 1;
        String profileType = isSuperAdmin ? "both" : getProfileType(user.getTenantId());
        var roles = roleService.listAll(user.getTenantId()).stream()
                .filter(r -> isSuperAdmin || isAssignableRole(r, profileType))
                .map(r -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", r.getId());
                    m.put("roleCode", r.getRoleCode());
                    m.put("roleName", r.getRoleName());
                    return m;
                }).toList();
        return R.ok(roles);
    }

    /**
     * 企业管理员可分配的角色：排除平台专属角色 + 管理员角色（仅超管可分配管理员）
     */
    private boolean isAssignableRole(Role r, String profileType) {
        String code = r.getRoleCode();
        if (code == null) return true;
        // 平台专属角色永不可分配
        if ("DUE_DILIGENCE".equals(code) || "FIRM".equals(code)) return false;
        // 管理员角色仅超管可分配（企业管理员不能提别人为管理员）
        if ("BUYER_ADMIN".equals(code) || "SUPPLIER_ADMIN".equals(code) || "TENANT_ADMIN".equals(code))
            return false;
        // BUYER → 采购方 + 双身份可见
        if ("BUYER".equals(code))
            return "buyer".equals(profileType) || "both".equals(profileType);
        // SUPPLIER → 供应方 + 双身份可见
        if ("SUPPLIER".equals(code))
            return "supplier".equals(profileType) || "both".equals(profileType);
        return true; // 自定义角色任何身份可见
    }

    /**
     * 校验当前用户是否有权操作该角色：
     * <ol>
     *   <li>角色必须存在</li>
     *   <li>必须同租户（超管可跨租户）—— 平台系统角色 tenantId=1，租户管理员天然被拒</li>
     *   <li>系统内置角色仅超管可改</li>
     * </ol>
     */
    private Role assertRoleAccessible(Long roleId, JwtUserDetails user) {
        Role role = roleService.getById(roleId);
        if (role == null) {
            throw new BizException(ErrorCode.ROLE_NOT_FOUND);
        }
        TenantGuard.assertSameTenant(user, role.getTenantId());  // 跨租户越权拦截
        if (!TenantGuard.isSuperAdmin(user) && Boolean.TRUE.equals(role.getIsSystem())) {
            throw new BizException(ErrorCode.SYSTEM_ROLE_CANNOT_MODIFY);  // 系统内置角色仅超管可改
        }
        return role;
    }

    private String getProfileType(Long tenantId) {
        // 画像归供应商域，Feign 查询身份类型（降级 null → 按双身份放行，与原直查为空兜底一致）
        ProfileInfoDTO profile = profileClient.getByTenantId(tenantId).getData();
        if (profile == null || profile.getProfileType() == null) return "both";
        return profile.getProfileType();
    }

    /**
     * 角色详情（含permIds和menuIds）
     */
    @Operation(summary = "角色详情")
    @GetMapping("/roles/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id,
                                         @AuthenticationPrincipal JwtUserDetails user) {
        assertRoleAccessible(id, user);
        return R.ok(roleService.getDetail(id));
    }

    /**
     * 新增角色（校验编码租户内唯一）
     */
    @Operation(summary = "新增角色")
    @AuditLog(module = "角色管理", operation = "CREATE", desc = "新增角色", targetType = "Role")
    @PostMapping("/roles")
    public R<Void> create(@Valid @RequestBody CreateRoleDTO dto,
                          @AuthenticationPrincipal JwtUserDetails user) {
        roleService.create(user.getTenantId(), user.getUserId(), dto);
        return R.ok();
    }

    /**
     * 编辑角色（编码不可修改，系统内置角色仅超管可改）
     */
    @Operation(summary = "编辑角色")
    @AuditLog(module = "角色管理", operation = "UPDATE", desc = "编辑角色", targetType = "Role")
    @PutMapping("/roles/{id}")
    public R<Void> update(@PathVariable Long id,
                          @Valid @RequestBody UpdateRoleDTO dto,
                          @AuthenticationPrincipal JwtUserDetails user) {
        assertRoleAccessible(id, user);
        roleService.update(id, dto);
        return R.ok();
    }

    /**
     * 删除角色（系统内置角色不可删）
     */
    @Operation(summary = "删除角色")
    @AuditLog(module = "角色管理", operation = "DELETE", desc = "删除角色", targetType = "Role")
    @DeleteMapping("/roles/{id}")
    public R<Void> delete(@PathVariable Long id,
                          @AuthenticationPrincipal JwtUserDetails user) {
        assertRoleAccessible(id, user);
        roleService.delete(id);
        return R.ok();
    }

    // ==================== 权限/菜单分配 ====================

    /**
     * 分配权限（全量替换角色权限关联，系统内置角色仅超管可改）
     */
    @Operation(summary = "分配权限")
    @AuditLog(module = "角色管理", operation = "UPDATE", desc = "分配权限", targetType = "Role")
    @PutMapping("/roles/{id}/permissions")
    public R<Void> assignPermissions(@PathVariable Long id,
                                     @RequestBody Map<String, List<Long>> body,
                                     @AuthenticationPrincipal JwtUserDetails user) {
        assertRoleAccessible(id, user);
        roleService.assignPermissions(id, body.get("permIds"), user.getUserId(), TenantGuard.isSuperAdmin(user));
        return R.ok();
    }

    /**
     * 分配菜单（全量替换角色菜单关联，系统内置角色仅超管可改）
     */
    @Operation(summary = "分配菜单")
    @AuditLog(module = "角色管理", operation = "UPDATE", desc = "分配菜单", targetType = "Role")
    @PutMapping("/roles/{id}/menus")
    public R<Void> assignMenus(@PathVariable Long id,
                               @RequestBody Map<String, List<Long>> body,
                               @AuthenticationPrincipal JwtUserDetails user) {
        assertRoleAccessible(id, user);
        roleService.assignMenus(id, body.get("menuIds"), user.getUserId(), TenantGuard.isSuperAdmin(user));
        return R.ok();
    }

    // ==================== 权限/菜单查询 ====================

    /**
     * 权限列表（平铺，非超管只返回可分配的权限，排除 *:*:* / admin / tenant / menu）
     */
    @Operation(summary = "权限列表")
    @GetMapping("/permissions")
    public R<List<Map<String, Object>>> permissions(@AuthenticationPrincipal JwtUserDetails user) {
        var list = roleService.listPermissions(TenantGuard.isSuperAdmin(user)).stream().map(p -> {
            Map<String, Object> node = new HashMap<>();
            node.put("id", p.getId());
            node.put("permCode", p.getPermCode());
            node.put("permName", p.getPermName());
            return node;
        }).toList();
        return R.ok(list);
    }

    /**
     * 菜单树（超管看全部，企业管理员只看自己有权限的）
     */
    @Operation(summary = "菜单树")
    @GetMapping("/menus")
    public R<List<Map<String, Object>>> menus(@AuthenticationPrincipal JwtUserDetails user) {
        // 超管（平台租户）看全部菜单，企业管理员只能分配自己有的菜单
        if (user.getTenantId() != null && user.getTenantId() == 1) {
            return R.ok(menuService.listTree());  // 超管 → 全部菜单
        }
        return R.ok(menuService.listTreeByUserId(user.getUserId()));  // 企业管理员 → 过滤
    }
}
