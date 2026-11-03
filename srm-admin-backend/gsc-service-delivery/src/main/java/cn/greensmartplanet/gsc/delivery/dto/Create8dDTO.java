package cn.greensmartplanet.gsc.delivery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 发起 8D 整改请求 DTO（采购方）
 *
 * @author gsc
 */
@Data
@Schema(description = "发起 8D 整改")
public class Create8dDTO {

    @NotNull(message = "整改期限不能为空")
    @Schema(description = "整改期限")
    private LocalDate deadline;
}
