package cn.greensmartplanet.gsc.platform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 忘记密码 - 发送验证码请求
 */
@Data
public class ForgotPasswordDTO {

    /** 租户编码 */
    @NotBlank(message = "租户编码不能为空")
    private String tenantCode;

    /** 邮箱（接收验证码） */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;
}
