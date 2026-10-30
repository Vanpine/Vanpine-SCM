package cn.greensmartplanet.gsc.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/**
 * 租户实体 — 每个入驻企业对应一条记录，id 就是 tenant_id
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tenant")
public class Tenant {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;                // 主键，即 tenant_id（1 = 平台超管）

    private String tenantCode;      // 租户编码（全局唯一，登录时用）
    private String tenantName;      // 企业全称
    private String shortName;       // 企业简称
    private String country;         // 所在国家
    private String regNumber;       // 企业注册号
    private String logoUrl;         // Logo 地址
    private String contactName;     // 联系人
    private String contactEmail;    // 联系人邮箱
    private String contactPhone;    // 联系人电话
    private String status;          // pending / active / suspended / rejected
    private String reviewComment;   // 审核意见
    private Long reviewedBy;        // 审核人ID
    private LocalDateTime reviewedAt;    // 审核时间
    private Long subscriptionId;    // 当前订阅ID
    private LocalDateTime expiredAt;     // 订阅到期时间
    private Integer sortOrder;      // 排序

    /** 申请人身份（buyer/supplier/buyer,supplier），非 DB 字段，列表查询时 LEFT JOIN 填充 */
    @TableField(exist = false)
    private String applicantRole;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;
}
