package cn.greensmartplanet.gsc.platform.service;

import cn.greensmartplanet.gsc.framework.security.JwtUserDetails;
import cn.greensmartplanet.gsc.platform.dto.CreateUserDTO;
import cn.greensmartplanet.gsc.platform.dto.UpdateUserDTO;
import cn.greensmartplanet.gsc.platform.entity.User;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;
import java.util.Map;

public interface UserService {

    /** 分页查用户列表（租户隔离，支持用户名/姓名/状态筛选） */
    IPage<User> listUsers(int pageNum, int pageSize, Long tenantId, String username, String realName, String status);

    /** 用户详情（含roleIds），校验目标用户归属当前租户 */
    Map<String, Object> getDetail(Long userId, JwtUserDetails currentUser);

    /** 新增用户：校验用户名租户内唯一→加密密码→入库 */
    User create(Long tenantId, Long createBy, CreateUserDTO dto);

    /** 编辑用户（校验归属；用户名修改时校验租户内唯一） */
    void update(Long userId, UpdateUserDTO dto, JwtUserDetails currentUser);

    /** 逻辑删除用户（校验归属），删除后强制该用户下线 */
    void delete(Long userId, JwtUserDetails currentUser);

    /** 分配角色（先删后插，全量替换），校验归属与 roleIds 合法性，变更后强制该用户重新登录 */
    void assignRoles(Long userId, List<Long> roleIds, JwtUserDetails currentUser);

    /** 重置密码（管理员强制设置新密码），校验归属，重置后强制该用户下线 */
    void resetPassword(Long userId, String password, JwtUserDetails currentUser);

    /** 状态切换（active/disabled/locked），校验归属 */
    void updateStatus(Long userId, String status, JwtUserDetails currentUser);
}
