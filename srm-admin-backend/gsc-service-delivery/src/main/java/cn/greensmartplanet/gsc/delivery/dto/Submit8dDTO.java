package cn.greensmartplanet.gsc.delivery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 提交 8D 整改报告请求 DTO（供应商，D1-D8 八步全必填）
 *
 * @author gsc
 */
@Data
@Schema(description = "提交 8D 整改报告")
public class Submit8dDTO {

    @NotBlank(message = "D1 组建团队不能为空")
    @Schema(description = "D1 组建团队")
    private String d1Team;

    @NotBlank(message = "D2 问题描述不能为空")
    @Schema(description = "D2 问题描述")
    private String d2Desc;

    @NotBlank(message = "D3 临时围堵措施不能为空")
    @Schema(description = "D3 临时围堵措施")
    private String d3Containment;

    @NotBlank(message = "D4 根本原因分析不能为空")
    @Schema(description = "D4 根本原因分析")
    private String d4RootCause;

    @NotBlank(message = "D5 永久纠正措施不能为空")
    @Schema(description = "D5 永久纠正措施")
    private String d5Corrective;

    @NotBlank(message = "D6 实施与验证不能为空")
    @Schema(description = "D6 实施与验证")
    private String d6Verification;

    @NotBlank(message = "D7 预防再发不能为空")
    @Schema(description = "D7 预防再发")
    private String d7Prevention;

    @NotBlank(message = "D8 总结与关闭不能为空")
    @Schema(description = "D8 总结与关闭")
    private String d8Summary;
}
