package cn.greensmartplanet.gsc.platform.service;

import cn.greensmartplanet.gsc.platform.entity.Menu;

import java.util.List;
import java.util.Map;

/**
 * 菜单管理服务接口
 *
 * @author gsc
 */
public interface MenuService {

    /** 菜单树列表（全部） */
    List<Map<String, Object>> listTree();

    /** 菜单树列表（按用户权限过滤，企业管理员只能看到自己有的菜单） */
    List<Map<String, Object>> listTreeByUserId(Long userId);

    /** 菜单详情 */
    Menu getById(Long id);

    /** 新增菜单 */
    Menu create(Menu menu);

    /** 编辑菜单 */
    void update(Menu menu);

    /** 删除菜单（有子菜单则拒绝） */
    void delete(Long id);
}
