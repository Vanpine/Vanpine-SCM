package cn.greensmartplanet.gsc.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 询盘受邀供应商关联实体
 * <p>
 * 决定"供应商能看到哪些询盘"，多对多关联表。
 * invite_status：pending / accepted / declined / expired
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("inquiry_invite")
public class InquiryInvite {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 询盘ID */
    private Long inquiryId;

    /** 受邀供应商租户ID */
    private Long supplierTenantId;

    /** 受邀状态：pending/accepted/declined/expired */
    private String inviteStatus;

    /** 邀请时间 */
    private LocalDateTime invitedAt;

    /** 供应商响应时间 */
    private LocalDateTime respondedAt;

    /** 拒绝原因（可选） */
    private String declineReason;

    /** 创建人用户ID */
    private Long createBy;

    /** 更新人用户ID */
    private Long updateBy;

    // ---- 审计 ----
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;
}
