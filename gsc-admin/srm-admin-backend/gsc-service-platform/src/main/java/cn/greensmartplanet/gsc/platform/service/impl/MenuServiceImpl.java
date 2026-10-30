package cn.greensmartplanet.gsc.platform.service.impl;

import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.platform.entity.Menu;
import cn.greensmartplanet.gsc.platform.entity.Permission;
import cn.greensmartplanet.gsc.platform.mapper.MenuMapper;
import cn.greensmartplanet.gsc.platform.mapper.PermissionMapper;
import cn.greensmartplanet.gsc.platform.service.MenuService;
import cn.greensmartplanet.gsc.platform.support.MenuTreeCache;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 菜单管理服务实现
 *
 * @author gsc
 */
@Service
@RequiredArgsConstructor
public class MenuServiceImpl implements MenuService {

    private final MenuMapper menuMapper;
    private final PermissionMapper permissionMapper;
    private final MenuTreeCache menuTreeCache;

    /** 菜单树列表（全部菜单） */
    @Override
    public List<Map<String, Object>> listTree() {
        return buildTree(menuMapper.selectAll());  // 查全部菜单 → 构建树
    }

    /** 菜单树列表（按用户权限过滤，企业管理员只能看到自己有的菜单） */
    @Override
    public List<Map<String, Object>> listTreeByUserId(Long userId) {
        return buildTree(menuMapper.selectByUserId(userId));  // 按用户查菜单 → 构建树
    }

    /** 平铺菜单列表 → 树形结构（公共方法） */
    private List<Map<String, Object>> buildTree(List<Menu> flatList) {
        // 1. Entity → Map 节点（Stream 转换）
        Map<Long, Map<String, Object>> nodeMap = flatList.stream()
                .collect(LinkedHashMap::new, (map, m) -> {
                    Map<String, Object> node = new LinkedHashMap<>();
                    node.put("id", m.getId());
                    node.put("parentId", m.getParentId());
                    node.put("menuType", m.getMenuType());
                    node.put("menuName", m.getMenuName());
                    node.put("menuPath", m.getMenuPath());
                    node.put("menuIcon", m.getMenuIcon());
                    node.put("permCode", m.getPermCode());
                    node.put("isVisible", m.getIsVisible());
                    node.put("isCache", m.getIsCache());
                    node.put("sortOrder", m.getSortOrder());
                    node.put("children", new ArrayList<>());
                    map.put(m.getId(), node);
                }, LinkedHashMap::putAll);
        // 2. 挂载子节点到父节点
        List<Map<String, Object>> roots = nodeMap.values().stream()
                .filter(node -> {
                    Long pid = (Long) node.get("parentId");
                    return pid == null || pid == 0;
                })
                .toList();
        nodeMap.values().stream()
                .filter(node -> {
                    Long pid = (Long) node.get("parentId");
                    return pid != null && pid != 0;
                })
                .forEach(node -> {
                    Map<String, Object> parent = nodeMap.get((Long) node.get("parentId"));
                    if (parent != null) {
                        ((List<Map<String, Object>>) parent.get("children")).add(node);
                    }
                });
        return roots;
    }

    /** 菜单详情 */
    @Override
    public Menu getById(Long id) {
        Menu menu = menuMapper.selectById(id);
        if (menu == null) {
            throw new BizException(ErrorCode.MENU_NOT_FOUND);  // 菜单不存在
        }
        return menu;
    }

    /** 新增菜单 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Menu create(Menu menu) {
        menu.setCreateTime(java.time.LocalDateTime.now());
        menuMapper.insertMenu(menu);
        // 自动同步权限码：permission 表不存在则创建，并分配给 SUPER_ADMIN
        syncPermission(menu.getPermCode(), menu.getMenuName());
        // 自动给超管分配菜单（INSERT IGNORE，已存在则跳过）
        menuMapper.insertRoleMenuForSuperAdmin(menu.getId());
        menuTreeCache.bumpGlobalVersion();  // 菜单是全局资源，所有用户菜单树缓存整体失效
        return menu;
    }

    /** 编辑菜单 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Menu menu) {
        Menu exist = menuMapper.selectById(menu.getId());
        if (exist == null) {
            throw new BizException(ErrorCode.MENU_NOT_FOUND);
        }
        menuMapper.updateMenu(menu);
        // 编辑时如果改了 permCode，同样自动同步
        syncPermission(menu.getPermCode(), menu.getMenuName());
        menuTreeCache.bumpGlobalVersion();  // 菜单是全局资源，所有用户菜单树缓存整体失效
    }

    /**
     * 自动同步权限码：permission 表不存在则插入，并自动分配给 SUPER_ADMIN
     */
    private void syncPermission(String permCode, String permName) {
        if (permCode == null || permCode.isBlank()) {
            return;
        }
        // 1. permission 表不存在则创建
        Permission existing = permissionMapper.selectOne(
                new LambdaQueryWrapper<Permission>().eq(Permission::getPermCode, permCode));
        if (existing == null) {
            Permission perm = new Permission();
            perm.setPermCode(permCode);
            perm.setPermName(permName);
            perm.setSortOrder(0);
            permissionMapper.insert(perm);
        }
        // 2. 自动分配给 SUPER_ADMIN（INSERT IGNORE，已存在则跳过）
        menuMapper.insertRolePermissionForSuperAdmin(permCode);
    }

    /** 删除菜单（有子菜单则拒绝） */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Menu menu = menuMapper.selectById(id);  // 查菜单是否存在
        if (menu == null) {
            throw new BizException(ErrorCode.MENU_NOT_FOUND);  // 菜单不存在
        }
        int children = menuMapper.countByParentId(id);  // 查是否存在子菜单
        if (children > 0) {
            throw new BizException(ErrorCode.MENU_HAS_CHILDREN);  // 有子菜单不能删
        }
        menuMapper.softDelete(id);
        menuTreeCache.bumpGlobalVersion();  // 菜单是全局资源，所有用户菜单树缓存整体失效
    }
}
