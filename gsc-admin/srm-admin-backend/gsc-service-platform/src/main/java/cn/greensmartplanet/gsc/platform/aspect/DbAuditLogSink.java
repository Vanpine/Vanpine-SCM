package cn.greensmartplanet.gsc.platform.aspect;

import cn.greensmartplanet.gsc.framework.audit.AuditLogSink;
import cn.greensmartplanet.gsc.platform.entity.AuditLogEntry;
import cn.greensmartplanet.gsc.platform.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 审计日志直写落库实现 — 框架层切面的 DB 通道
 * <p>
 * 微服务拆分过渡期：旧单体（及平台服务）用此实现直写 audit_log 表，
 * 行为与原切面直接调 AuditLogService.save 完全一致（@Async 异步写库）。
 * 阶段 8 全量切流后替换为 MqAuditLogSink（发 gsc-audit-log 事件）。
 * <p>
 * 类型桥接：框架层切面构建 {@code cn.greensmartplanet.gsc.framework.audit.AuditLogEntry}（纯 POJO），
 * 此处转换为 system 模块的 MyBatis-Plus 实体后委托 AuditLogService 落库。
 *
 * @author gsc
 */
@Component
@RequiredArgsConstructor
public class DbAuditLogSink implements AuditLogSink {

    private final AuditLogService auditLogService;

    @Override
    // 参数类型保留全限定名：框架层 AuditLogEntry 与本模块实体 AuditLogEntry 同名，
    // Java 不允许同简单名双 import，签名处只能全限定（桥梁文件固有约束，阶段 8 切 MQ 后随文件删除）
    public void save(cn.greensmartplanet.gsc.framework.audit.AuditLogEntry source) {
        auditLogService.save(AuditLogEntry.builder()
                .tenantId(source.getTenantId())
                .userId(source.getUserId())
                .username(source.getUsername())
                .module(source.getModule())
                .operation(source.getOperation())
                .description(source.getDescription())
                .requestMethod(source.getRequestMethod())
                .requestUrl(source.getRequestUrl())
                .requestParams(source.getRequestParams())
                .ip(source.getIp())
                .status(source.getStatus())
                .errorMsg(source.getErrorMsg())
                .costTimeMs(source.getCostTimeMs())
                .targetType(source.getTargetType())
                .targetId(source.getTargetId())
                .targetSummary(source.getTargetSummary())
                .createTime(source.getCreateTime())
                .build());
    }
}
