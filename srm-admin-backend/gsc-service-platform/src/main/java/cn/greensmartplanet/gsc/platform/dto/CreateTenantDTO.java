package cn.greensmartplanet.gsc.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增租户请求 DTO
 * 超管为入驻企业开辟租户空间时使用
 */
@Data
@Schema(description = "新增租户请求")
public class CreateTenantDTO {

    @NotBlank(message = "租户编码不能为空")
    @Size(min = 2, max = 32, message = "编码长度2-32位")
    @Schema(description = "租户编码（全局唯一，登录时使用）", example = "huawei")
    private String tenantCode;

    @NotBlank(message = "企业名称不能为空")
    @Size(max = 128, message = "名称不超过128个字符")
    @Schema(description = "企业全称", example = "华为技术有限公司")
    private String tenantName;

    @Schema(description = "企业简称")
    private String shortName;

    @Schema(description = "所在国家/地区")
    private String country;

    @Schema(description = "企业注册号")
    private String regNumber;

    @Schema(description = "联系人姓名")
    private String contactName;

    @Schema(description = "联系人邮箱")
    private String contactEmail;

    @Schema(description = "联系人电话")
    private String contactPhone;

    // ---- 可选：同时创建该租户的管理员账号 ----

    @Size(min = 2, max = 64, message = "管理员用户名长度2-64位")
    @Schema(description = "（可选）管理员用户名，填写则自动创建企业管理员账号", example = "admin")
    private String adminUsername;

    @Size(min = 6, message = "密码至少6位")
    @Schema(description = "（可选）管理员密码，与用户名同时填写")
    private String adminPassword;

    @Schema(description = "（可选）管理员真实姓名")
    private String adminRealName;
}
