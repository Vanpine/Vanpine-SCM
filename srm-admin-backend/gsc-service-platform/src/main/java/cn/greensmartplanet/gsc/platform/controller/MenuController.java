package cn.greensmartplanet.gsc.platform.controller;

import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.framework.audit.AuditLog;
import cn.greensmartplanet.gsc.framework.security.JwtUserDetails;
import cn.greensmartplanet.gsc.platform.entity.Menu;
import cn.greensmartplanet.gsc.platform.service.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 菜单管理控制器
 * 菜单是全局资源（无 tenant_id），仅超管可管理
 *
 * @author gsc
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/menu-manage")
@PreAuthorize("hasAuthority('*:*:*')")
@Tag(name = "菜单管理", description = "后台菜单树的增删改查")
public class MenuController {

    private final MenuService menuService;

    /**
     * 菜单详情
     */
    @Operation(summary = "菜单详情")
    @GetMapping("/{id}")
    public R<Menu> detail(@PathVariable Long id) {
        return R.ok(menuService.getById(id));
    }

    /**
     * 新增菜单
     */
    @Operation(summary = "新增菜单")
    @AuditLog(module = "菜单管理", operation = "CREATE", desc = "新增菜单", targetType = "Menu")
    @PostMapping("")
    public R<Menu> create(@RequestBody Menu menu) {
        return R.ok(menuService.create(menu));
    }

    /**
     * 编辑菜单
     */
    @Operation(summary = "编辑菜单")
    @AuditLog(module = "菜单管理", operation = "UPDATE", desc = "编辑菜单", targetType = "Menu")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Menu menu) {
        menu.setId(id);  // 设置路径中的ID
        menuService.update(menu);  // 更新菜单
        return R.ok();
    }

    /**
     * 删除菜单
     */
    @Operation(summary = "删除菜单")
    @AuditLog(module = "菜单管理", operation = "DELETE", desc = "删除菜单", targetType = "Menu")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        menuService.delete(id);  // 删除菜单
        return R.ok();
    }

    /**
     * 菜单树列表（超管看全部，企业管理员只看自己有权限的）
     */
    @Operation(summary = "菜单树列表")
    @GetMapping("/tree")
    public R<List<Map<String, Object>>> list(@AuthenticationPrincipal JwtUserDetails user) {
        // 超管（平台租户）看全部菜单，企业管理员只能管理自己有的菜单
        if (user.getTenantId() != null && user.getTenantId() == 1) {
            return R.ok(menuService.listTree());  // 超管 → 全部菜单
        }
        return R.ok(menuService.listTreeByUserId(user.getUserId()));  // 企业管理员 → 过滤
    }
}
