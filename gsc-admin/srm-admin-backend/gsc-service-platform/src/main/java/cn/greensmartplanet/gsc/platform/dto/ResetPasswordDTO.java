package cn.greensmartplanet.gsc.platform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 忘记密码 - 验证码重置密码请求
 */
@Data
public class ResetPasswordDTO {

    /** 租户编码 */
    @NotBlank(message = "租户编码不能为空")
    private String tenantCode;

    /** 邮箱 */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    /** 邮箱收到的验证码 */
    @NotBlank(message = "验证码不能为空")
    private String code;

    /** 新密码 */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, message = "密码不能少于6位")
    private String newPassword;
}
