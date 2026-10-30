package cn.greensmartplanet.gsc.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审计日志事件载荷（gsc-audit-log）
 * <p>
 * 事件契约类：业务域经 MqAuditLogSink 生产、platform 域消费，字段即契约，双方共用本类避免类型漂移。
 * 字段与框架层 AuditLogEntry（audit_log 表）一一对应。
 * 幂等：发送方给消息贴 messageId（RabbitMQ 原生属性），消费端落库到 audit_log.event_id 唯一索引防重。
 *
 * @author gsc
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogPayload implements Serializable {

    /** 所属租户ID */
    private Long tenantId;
    /** 操作人ID */
    private Long userId;
    /** 操作人用户名 */
    private String username;
    /** 操作模块（如"用户管理"） */
    private String module;
    /** 操作类型（如"DELETE"） */
    private String operation;
    /** 操作描述 */
    private String description;
    /** HTTP方法 */
    private String requestMethod;
    /** 请求URL */
    private String requestUrl;
    /** 请求参数JSON */
    private String requestParams;
    /** 客户端IP */
    private String ip;
    /** success / fail */
    private String status;
    /** 失败原因 */
    private String errorMsg;
    /** 耗时（毫秒） */
    private Integer costTimeMs;
    /** 操作目标类型（如 Inquiry/Quote） */
    private String targetType;
    /** 操作目标ID */
    private Long targetId;
    /** 操作目标摘要（可读，如询盘编号） */
    private String targetSummary;
    /** 创建时间 */
    private LocalDateTime createTime;
}
