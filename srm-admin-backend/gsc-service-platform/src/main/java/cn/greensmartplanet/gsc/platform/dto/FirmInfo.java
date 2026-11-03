package cn.greensmartplanet.gsc.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 事务所信息 — 从公司事务所查询 API 解析后的精简 DTO
 *
 * <p>⚠️ 迁移期桥接（微服务拆分）：本文件归属供应商域（阶段 7 gsc-service-supplier），platform 内为过渡副本，supplier 上线后随桥接收敛删除/改 Feign。</p>
 * @author gsc
 */
@Data
@Schema(description = "事务所信息")
public class FirmInfo {

    @Schema(description = "事务所ID")
    private Long firmId;

    @Schema(description = "事务所名称")
    private String firmName;

    @Schema(description = "联系人姓名")
    private String contactName;

    @Schema(description = "联系人邮箱")
    private String contactEmail;

    @Schema(description = "联系人电话")
    private String contactPhone;

    @Schema(description = "省份")
    private String province;

    @Schema(description = "城市")
    private String city;

    @Schema(description = "区县")
    private String district;

    @Schema(description = "详细地址")
    private String address;

    @Schema(description = "服务范围")
    private String serviceScope;

    @Schema(description = "主要咨询服务领域")
    private String serviceSector;

    @Schema(description = "状态（1=启用）")
    private Integer status;
}
