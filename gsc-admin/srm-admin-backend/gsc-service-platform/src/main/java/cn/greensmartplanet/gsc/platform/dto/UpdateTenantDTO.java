package cn.greensmartplanet.gsc.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 编辑租户请求 DTO
 */
@Data
@Schema(description = "编辑租户请求")
public class UpdateTenantDTO {

    /** 租户编码（全局唯一） */
    @NotBlank(message = "租户编码不能为空")
    @Size(max = 32)
    private String tenantCode;

    /** 企业全称 */
    @NotBlank(message = "企业名称不能为空")
    @Size(max = 128)
    private String tenantName;

    private String shortName;       // 企业简称
    private String country;         // 所在国家
    private String regNumber;       // 企业注册号
    private String contactName;     // 联系人
    private String contactEmail;    // 联系邮箱
    private String contactPhone;    // 联系电话
}
