package cn.greensmartplanet.gsc.platform.mapper;

import cn.greensmartplanet.gsc.platform.entity.User;
import cn.greensmartplanet.gsc.platform.entity.Role;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户 Mapper — 简单CRUD用 BaseMapper，复杂查询写到 UserMapper.xml
 *
 * @author gsc
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 根据租户ID + 用户名查用户
     */
    User selectByTenantAndUsername(@Param("tenantId") Long tenantId,
                                   @Param("username") String username);

    /**
     * 根据租户ID + 邮箱查用户
     */
    User selectByTenantAndEmail(@Param("tenantId") Long tenantId,
                                @Param("email") String email);

    /** 按邮箱查用户（不限租户） */
    User selectByEmail(@Param("email") String email);

    /** 更新用户的 firmId */
    int updateFirmId(@Param("userId") Long userId, @Param("firmId") Long firmId);

    /**
     * 查用户的所有权限标识
     */
    List<String> selectPermissionsByUserId(@Param("userId") Long userId);

    /**
     * 查用户的所有角色
     */
    List<Role> selectRolesByUserId(@Param("userId") Long userId);

    /**
     * 更新最后登录IP和时间
     */
    void updateLoginInfo(@Param("userId") Long userId,
                         @Param("lastLoginIp") String lastLoginIp,
                         @Param("lastLoginTime") LocalDateTime lastLoginTime);

    /**
     * 更新密码
     */
    void updatePassword(@Param("userId") Long userId,
                        @Param("passwordHash") String passwordHash);

    /** 查用户的角色ID列表 */
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);

    /** 删除用户所有角色关联 */
    void deleteUserRoles(@Param("userId") Long userId);

    /** 批量插入用户角色关联 */
    void insertUserRoles(@Param("userId") Long userId, @Param("roleIds") List<Long> roleIds);

    /** 查拥有某角色的所有用户ID（用于权限变更后清除 Redis 缓存） */
    List<Long> selectUserIdsByRoleId(@Param("roleId") Long roleId);
}
