package cn.greensmartplanet.gsc.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 审核入驻申请 DTO — 超管审批入驻申请
 *
 * @author gsc
 */
@Data
@Schema(description = "审核入驻申请")
public class ReviewApplicationDTO {

    @NotBlank(message = "审核结果不能为空")
    @Schema(description = "审核结果 approved / rejected", example = "approved")
    private String status;

    @Schema(description = "审核意见（驳回时必填）")
    private String reviewComment;

    @Schema(description = "租户编码（通过时填写）")
    private String tenantCode;

    @Schema(description = "管理员账号（通过时填写，默认 admin）")
    private String adminUsername;

    @Schema(description = "管理员密码（通过时填写，默认 admin123）")
    private String adminPassword;
}
