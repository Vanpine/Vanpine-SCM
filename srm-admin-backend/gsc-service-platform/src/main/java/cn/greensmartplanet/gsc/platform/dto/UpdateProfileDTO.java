package cn.greensmartplanet.gsc.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 更新个人信息请求 DTO
 *
 * @author gsc
 */
@Data
@Schema(description = "更新个人信息")
public class UpdateProfileDTO {

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "头像 OSS key（上传接口返回的 key 字段）")
    private String avatarKey;
}
