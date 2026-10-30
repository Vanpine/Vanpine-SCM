package cn.greensmartplanet.gsc.supplier.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 站内消息通知实体
 * <p>
 * 撮合/采购/合同业务事件（对接、报价、合同状态流转）自动生成，
 * 按接收方租户 receiver_tenant_id 推送，该企业下所有登录用户可见。
 * is_read=1 表示已读，标记后不再作为未读展示。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("notification")
public class Notification {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 接收方租户ID */
    private Long receiverTenantId;

    /** 事件类型：CONNECTION / QUOTE / CONTRACT 系列 */
    private String type;

    /** 通知标题 */
    private String title;

    /** 通知内容 */
    private String content;

    /** 关联业务类型（connection/quote/contract，前端据此跳转） */
    private String relatedType;

    /** 关联业务ID */
    private Long relatedId;

    /** 发起方租户ID */
    private Long fromTenantId;

    /** 发起用户ID */
    private Long fromUserId;

    /** 是否已读：0未读 1已读 */
    private Integer isRead;

    /** 已读时间 */
    private LocalDateTime readTime;

    // ---- 审计 ----
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;
}
