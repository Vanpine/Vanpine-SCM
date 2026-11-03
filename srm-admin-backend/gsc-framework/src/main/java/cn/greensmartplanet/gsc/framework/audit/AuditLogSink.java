package cn.greensmartplanet.gsc.framework.audit;

/**
 * 审计日志落库通道接口 — 框架层只定义接口，各服务提供实现
 * <p>
 * 为什么抽象成接口：@AuditLog 切面在框架层，不能依赖任何业务模块的 Service。
 * 各服务通过实现此接口决定审计日志落库方式：
 * <ul>
 *   <li>旧单体/平台服务：{@code DbAuditLogSink} 直写 audit_log 表</li>
 *   <li>其他业务服务：发 MQ 事件 {@code gsc-audit-log}，由平台服务消费落库（阶段 8 切流后启用）</li>
 * </ul>
 *
 * @author gsc
 */
public interface AuditLogSink {

    /**
     * 保存一条审计日志（实现方自行决定直写还是异步发送）
     */
    void save(AuditLogEntry entry);
}
