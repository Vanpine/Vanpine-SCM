package cn.greensmartplanet.gsc.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 站内消息事件载荷（gsc-notification）
 * <p>
 * 事件契约类：trade/delivery/supplier 域生产、platform 域消费，字段即契约，双方共用本类避免类型漂移。
 * 字段与五域一致的 Notification 实体对齐（微服务拆分规范）。
 * 幂等：发送方给消息贴 messageId（RabbitMQ 原生属性），消费端落库到 notification.event_id 唯一索引防重。
 *
 * @author gsc
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPayload implements Serializable {

    /** 接收方租户ID */
    private Long receiverTenantId;
    /** 消息类型（如 contract_signed / delivery_shipped） */
    private String type;
    /** 标题 */
    private String title;
    /** 内容 */
    private String content;
    /** 关联业务类型（如 Contract / Delivery） */
    private String relatedType;
    /** 关联业务ID */
    private Long relatedId;
    /** 发起方租户ID */
    private Long fromTenantId;
    /** 发起方用户ID */
    private Long fromUserId;
    /** 未读标记（0 未读 / 1 已读） */
    private Integer isRead;
    /** 已读时间 */
    private LocalDateTime readTime;
    /** 创建时间 */
    private LocalDateTime createTime;
}
