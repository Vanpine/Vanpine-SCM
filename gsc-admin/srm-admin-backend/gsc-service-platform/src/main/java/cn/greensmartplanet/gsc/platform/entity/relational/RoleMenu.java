package cn.greensmartplanet.gsc.platform.entity.relational;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("role_menu")
public class RoleMenu {

    private Long roleId;
    private Long menuId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
