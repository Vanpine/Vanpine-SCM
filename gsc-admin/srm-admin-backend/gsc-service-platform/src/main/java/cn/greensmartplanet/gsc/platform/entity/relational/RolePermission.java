package cn.greensmartplanet.gsc.platform.entity.relational;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("role_permission")
public class RolePermission {

    private Long roleId;
    private Long permId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
