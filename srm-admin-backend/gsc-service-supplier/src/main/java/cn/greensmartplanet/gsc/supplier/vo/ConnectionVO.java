package cn.greensmartplanet.gsc.supplier.vo;

import cn.greensmartplanet.gsc.supplier.entity.Connection;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对接记录 VO
 *
 * @author gsc
 */
@Data
@Schema(description = "对接记录")
public class ConnectionVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "需求ID")
    private Long requirementId;

    @Schema(description = "需求标题")
    private String requirementTitle;

    @Schema(description = "采购方租户ID")
    private Long buyerTenantId;

    @Schema(description = "采购方企业名")
    private String buyerCompanyName;

    @Schema(description = "供应方租户ID")
    private Long supplierTenantId;

    @Schema(description = "供应方企业名")
    private String supplierCompanyName;

    @Schema(description = "状态：pending/accepted/rejected/closed")
    private String status;

    @Schema(description = "买方留言")
    private String message;

    @Schema(description = "采购方联系人")
    private String buyerContactName;

    @Schema(description = "采购方联系电话")
    private String buyerContactPhone;

    @Schema(description = "采购方联系邮箱")
    private String buyerContactEmail;

    @Schema(description = "供应方联系人")
    private String supplierContactName;

    @Schema(description = "供应方联系电话")
    private String supplierContactPhone;

    @Schema(description = "供应方联系邮箱")
    private String supplierContactEmail;

    @Schema(description = "供应商回复")
    private String replyMessage;

    @Schema(description = "发起人用户ID")
    private Long createBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    public static ConnectionVO fromEntity(Connection c) {
        ConnectionVO vo = new ConnectionVO();
        vo.setId(c.getId());
        vo.setRequirementId(c.getRequirementId());
        vo.setBuyerTenantId(c.getBuyerTenantId());
        vo.setSupplierTenantId(c.getSupplierTenantId());
        vo.setStatus(c.getStatus());
        vo.setMessage(c.getMessage());
        vo.setReplyMessage(c.getReplyMessage());
        vo.setCreateBy(c.getCreateBy());
        vo.setCreateTime(c.getCreateTime());
        vo.setUpdateTime(c.getUpdateTime());
        return vo;
    }
}
