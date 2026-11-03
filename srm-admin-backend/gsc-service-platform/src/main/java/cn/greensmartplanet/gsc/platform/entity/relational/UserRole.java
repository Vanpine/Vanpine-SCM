package cn.greensmartplanet.gsc.platform.entity.relational;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户角色关联实体
 *
 * @author gsc
 */
@Data
@TableName("user_role")
public class UserRole {

    private Long userId;
    private Long roleId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
