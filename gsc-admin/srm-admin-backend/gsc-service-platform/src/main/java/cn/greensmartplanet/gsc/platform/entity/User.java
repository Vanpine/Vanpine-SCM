package cn.greensmartplanet.gsc.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户实体 — 每个登录账号对应一条记录
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user")
public class User {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;          // 所属租户ID
    private String passportUuid;    // AI护照UUID（可空）
    private String passportName;    // AI护照实名
    private String passportEmail;   // AI护照邮箱
    private String passportPhone;   // AI护照手机号
    private Long firmId;            // 事务所ID（角色为 FIRM 时有值）
    private String username;        // 登录用户名（租户内唯一）
    private String passwordHash;    // BCrypt 密文
    private String realName;        // 真实姓名
    private String email;           // 邮箱
    private String phone;           // 手机号
    private String avatarKey;       // 头像 OSS 对象路径（访问时现签 URL）
    private Long deptId;            // 部门ID
    private String status;          // active / disabled / locked
    private String lastLoginIp;     // 最后登录IP
    private LocalDateTime lastLoginTime; // 最后登录时间
    private Integer sortOrder;      // 排序
    private Long createBy;          // 创建人
    private Long updateBy;          // 更新人

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;

    /**
     * 角色名称列表（非数据库字段，列表查询时填充）
     */
    @TableField(exist = false)
    private List<String> roleNames;
}
