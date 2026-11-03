package cn.greensmartplanet.gsc.platform.dto;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 事务所提交尽调结果请求 DTO
 *
 * <p>⚠️ 迁移期桥接（微服务拆分）：本文件归属供应商域（阶段 7 gsc-service-supplier），platform 内为过渡副本，supplier 上线后随桥接收敛删除/改 Feign。</p>
 * @author gsc
 */
@Data
@Schema(description = "提交尽调结果")
public class SubmitFirmResultDTO {

    @Schema(description = "事务所工作备注/说明")
    private String progress;

    @NotBlank(message = "报告文件参数不能为空")
    @Schema(description = "报告文件参数（格式：OSS URL|原始文件名，竖线分隔）")
    private String resultUrl;
}
