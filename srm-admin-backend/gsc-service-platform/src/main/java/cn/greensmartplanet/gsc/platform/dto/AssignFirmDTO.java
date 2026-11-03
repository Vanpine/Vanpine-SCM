package cn.greensmartplanet.gsc.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 分配事务所请求 DTO
 *
 * <p>⚠️ 迁移期桥接（微服务拆分）：本文件归属供应商域（阶段 7 gsc-service-supplier），platform 内为过渡副本，supplier 上线后随桥接收敛删除/改 Feign。</p>
 * @author gsc
 */
@Data
@Schema(description = "分配事务所")
public class AssignFirmDTO {

    @NotNull(message = "事务所ID不能为空")
    @Schema(description = "事务所ID（同事系统提供）")
    private Long firmId;

    @NotBlank(message = "事务所名称不能为空")
    @Schema(description = "事务所名称")
    private String firmName;

    @NotBlank(message = "联系人不能为空")
    @Schema(description = "事务所联系人")
    private String firmContactName;

    @NotBlank(message = "联系人邮箱不能为空")
    @Schema(description = "联系人邮箱（用于创建登录账号）")
    private String firmContactEmail;

    @Schema(description = "联系人电话")
    private String firmContactPhone;

    @Schema(description = "尽调截止日期")
    private LocalDate deadline;

    @Schema(description = "特殊要求/说明")
    private String instruction;
}
