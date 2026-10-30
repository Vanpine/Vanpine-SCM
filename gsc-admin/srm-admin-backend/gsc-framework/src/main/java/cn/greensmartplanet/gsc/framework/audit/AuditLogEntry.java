package cn.greensmartplanet.gsc.framework.audit;

import java.time.LocalDateTime;

/**
 * 审计日志实体 — 框架层公共 POJO，供切面构建、各服务通过 sink 落库
 * <p>
 * 字段与 audit_log 表一一对应（原 system 模块的 AuditLogEntry 迁入框架层，
 * 让 @AuditLog 切面不依赖任何业务模块）。
 *
 * @author gsc
 */
public class AuditLogEntry {

    private Long id;                // 主键
    private Long tenantId;          // 所属租户ID
    private Long userId;            // 操作人ID
    private String username;        // 操作人用户名
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
    private LocalDateTime createTime; // 创建时间

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getRequestMethod() { return requestMethod; }
    public void setRequestMethod(String requestMethod) { this.requestMethod = requestMethod; }
    public String getRequestUrl() { return requestUrl; }
    public void setRequestUrl(String requestUrl) { this.requestUrl = requestUrl; }
    public String getRequestParams() { return requestParams; }
    public void setRequestParams(String requestParams) { this.requestParams = requestParams; }
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
    public Integer getCostTimeMs() { return costTimeMs; }
    public void setCostTimeMs(Integer costTimeMs) { this.costTimeMs = costTimeMs; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public String getTargetSummary() { return targetSummary; }
    public void setTargetSummary(String targetSummary) { this.targetSummary = targetSummary; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 简单构建器 — 切面里用 builder() 链式构建，避免手写 setter 样板
     */
    public static class Builder {
        private final AuditLogEntry entry = new AuditLogEntry();
        public Builder id(Long id) { entry.id = id; return this; }
        public Builder tenantId(Long tenantId) { entry.tenantId = tenantId; return this; }
        public Builder userId(Long userId) { entry.userId = userId; return this; }
        public Builder username(String username) { entry.username = username; return this; }
        public Builder module(String module) { entry.module = module; return this; }
        public Builder operation(String operation) { entry.operation = operation; return this; }
        public Builder description(String description) { entry.description = description; return this; }
        public Builder requestMethod(String requestMethod) { entry.requestMethod = requestMethod; return this; }
        public Builder requestUrl(String requestUrl) { entry.requestUrl = requestUrl; return this; }
        public Builder requestParams(String requestParams) { entry.requestParams = requestParams; return this; }
        public Builder ip(String ip) { entry.ip = ip; return this; }
        public Builder status(String status) { entry.status = status; return this; }
        public Builder errorMsg(String errorMsg) { entry.errorMsg = errorMsg; return this; }
        public Builder costTimeMs(Integer costTimeMs) { entry.costTimeMs = costTimeMs; return this; }
        public Builder targetType(String targetType) { entry.targetType = targetType; return this; }
        public Builder targetId(Long targetId) { entry.targetId = targetId; return this; }
        public Builder targetSummary(String targetSummary) { entry.targetSummary = targetSummary; return this; }
        public Builder createTime(LocalDateTime createTime) { entry.createTime = createTime; return this; }
        public AuditLogEntry build() { return entry; }
    }
}
