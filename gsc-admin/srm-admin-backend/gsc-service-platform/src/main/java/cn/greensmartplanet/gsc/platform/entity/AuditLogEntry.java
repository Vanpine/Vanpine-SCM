package cn.greensmartplanet.gsc.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/**
 * 操作审计日志实体 — 记录谁在什么时候做了什么操作
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("audit_log")
public class AuditLogEntry {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;                // 主键

    private String eventId;         // MQ事件ID（幂等键，直写为NULL不参与查重，uk_event_id 唯一索引）

    private Long tenantId;          // 所属租户ID
    private Long userId;            // 操作人ID
    private String username;        // 操作人登录名（落库快照）

    /** 操作人真实姓名，查询时按 userId 回填，不落库 */
    @TableField(exist = false)
    private String realName;
    private String module;          // 操作模块（如"用户管理"）
    private String operation;       // 操作类型（如"DELETE"）
    private String description;     // 操作描述
    private String requestMethod;   // HTTP方法
    private String requestUrl;      // 请求URL
    private String requestParams;   // 请求参数JSON
    private String ip;              // 客户端IP
    private String status;          // success / fail
    private String errorMsg;        // 失败原因
    private Integer costTimeMs;     // 耗时（毫秒）
    private String targetType;      // 操作目标类型（如 Inquiry/Quote）
    private Long targetId;          // 操作目标ID
    private String targetSummary;   // 操作目标摘要（可读，如询盘编号）

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
