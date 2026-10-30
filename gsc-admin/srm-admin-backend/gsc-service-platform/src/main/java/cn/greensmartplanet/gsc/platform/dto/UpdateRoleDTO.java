package cn.greensmartplanet.gsc.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "编辑角色请求")
public class UpdateRoleDTO {

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 32, message = "名称不超过32个字符")
    @Schema(description = "角色名称")
    private String roleName;

    @Schema(description = "角色描述")
    private String roleDesc;
}
