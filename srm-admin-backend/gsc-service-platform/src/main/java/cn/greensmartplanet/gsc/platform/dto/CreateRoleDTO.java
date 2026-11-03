package cn.greensmartplanet.gsc.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "新增角色请求")
public class CreateRoleDTO {

    @NotBlank(message = "角色编码不能为空")
    @Size(min = 2, max = 32, message = "编码长度2-32位")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_:]*$", message = "以字母开头，可包含字母、数字、下划线和冒号")
    @Schema(description = "角色编码", example = "TENANT_ADMIN")
    private String roleCode;

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 32, message = "名称不超过32个字符")
    @Schema(description = "角色名称", example = "企业管理员")
    private String roleName;

    @Schema(description = "角色描述")
    private String roleDesc;

    @Schema(description = "角色身份：buyer/supplier/both/platform")
    private String roleType;
}
