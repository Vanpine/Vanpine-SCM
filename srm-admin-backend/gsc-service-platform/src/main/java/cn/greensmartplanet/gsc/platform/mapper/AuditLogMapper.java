package cn.greensmartplanet.gsc.platform.mapper;

import cn.greensmartplanet.gsc.platform.entity.AuditLogEntry;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 操作日志 Mapper — 写入 + 分页查询
 *
 * @author gsc
 */
public interface AuditLogMapper {

    /** 写入操作日志 */
    int insert(AuditLogEntry entry);

    /** 分页查询操作日志（超管不传 tenantId 查全部，企业管理员传 tenantId 只看自己租户的） */
    List<AuditLogEntry> selectPage(@Param("tenantId") Long tenantId,
                                    @Param("module") String module,
                                    @Param("operation") String operation,
                                    @Param("username") String username,
                                    @Param("status") String status,
                                    @Param("startTime") String startTime,
                                    @Param("endTime") String endTime);
}
