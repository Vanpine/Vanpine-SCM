package cn.greensmartplanet.gsc.trade.vo;

import cn.greensmartplanet.gsc.trade.entity.InquiryInvite;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 询盘受邀供应商 VO
 *
 * @author gsc
 */
@Data
@Schema(description = "询盘受邀供应商")
public class InquiryInviteVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "询盘ID")
    private Long inquiryId;

    @Schema(description = "供应商租户ID")
    private Long supplierTenantId;

    @Schema(description = "供应商企业名")
    private String supplierCompanyName;

    @Schema(description = "受邀状态")
    private String inviteStatus;

    @Schema(description = "邀请时间")
    private LocalDateTime invitedAt;

    @Schema(description = "响应时间")
    private LocalDateTime respondedAt;

    @Schema(description = "拒绝原因")
    private String declineReason;

    public static InquiryInviteVO fromEntity(InquiryInvite inv) {
        InquiryInviteVO vo = new InquiryInviteVO();
        vo.setId(inv.getId());
        vo.setInquiryId(inv.getInquiryId());
        vo.setSupplierTenantId(inv.getSupplierTenantId());
        vo.setInviteStatus(inv.getInviteStatus());
        vo.setInvitedAt(inv.getInvitedAt());
        vo.setRespondedAt(inv.getRespondedAt());
        vo.setDeclineReason(inv.getDeclineReason());
        return vo;
    }
}
