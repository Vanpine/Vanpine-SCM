package cn.greensmartplanet.gsc.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 更新核查清单项请求 DTO
 *
 * <p>⚠️ 迁移期桥接（微服务拆分）：本文件归属供应商域（阶段 7 gsc-service-supplier），platform 内为过渡副本，supplier 上线后随桥接收敛删除/改 Feign。</p>
 * @author gsc
 */
@Data
@Schema(description = "更新核查清单项")
public class UpdateChecklistItemDTO {

    @Schema(description = "是否通过（true=打勾，false=取消，null=不做改变）")
    private Boolean checked;

    @Schema(description = "核查备注")
    private String remark;
}
