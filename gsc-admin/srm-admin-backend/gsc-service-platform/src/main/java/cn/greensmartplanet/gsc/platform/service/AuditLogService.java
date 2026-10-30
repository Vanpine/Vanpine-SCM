package cn.greensmartplanet.gsc.platform.service;

import cn.greensmartplanet.gsc.platform.entity.AuditLogEntry;

import java.util.Map;

/**
 * 操作日志服务 — 写入 + 分页查询
 *
 * @author gsc
 */
public interface AuditLogService {

    /** 保存操作日志（AOP 切面调用） */
    void save(AuditLogEntry entry);

    /** 分页查询（超管看全部，企业管理员只看自己租户） */
    Map<String, Object> page(int pageNum, int pageSize, Long tenantId,
                             String module, String operation, String username,
                             String status, String startTime, String endTime);
}
