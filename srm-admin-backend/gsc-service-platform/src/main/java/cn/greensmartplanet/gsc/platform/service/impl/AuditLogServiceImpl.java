package cn.greensmartplanet.gsc.platform.service.impl;

import cn.greensmartplanet.gsc.platform.entity.AuditLogEntry;
import cn.greensmartplanet.gsc.platform.entity.User;
import cn.greensmartplanet.gsc.platform.mapper.AuditLogMapper;
import cn.greensmartplanet.gsc.platform.mapper.UserMapper;
import cn.greensmartplanet.gsc.platform.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 操作日志服务实现 — 写入 + 分页查询
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogMapper auditLogMapper;
    private final UserMapper userMapper;

    /** 保存操作日志（异步写入，不阻塞主请求） */
    @Async
    @Override
    public void save(AuditLogEntry entry) {
        auditLogMapper.insert(entry);
    }

    /** 分页查询（超管 tenantId=null 查全部，企业管理员传 tenantId 只看自己租户） */
    @Override
    public Map<String, Object> page(int pageNum, int pageSize, Long tenantId,
                                     String module, String operation, String username,
                                     String status, String startTime, String endTime) {
        // 查询全部匹配记录
        List<AuditLogEntry> allRecords = auditLogMapper.selectPage(
                tenantId, module, operation, username, status, startTime, endTime);
        // 手动分页
        int total = allRecords.size();
        int from = (pageNum - 1) * pageSize;
        int to = Math.min(from + pageSize, total);
        List<AuditLogEntry> pageRecords = from < total ? allRecords.subList(from, to) : List.of();
        fillOperatorNames(pageRecords);

        Map<String, Object> result = new HashMap<>();
        result.put("records", pageRecords);
        result.put("total", total);
        result.put("page", pageNum);
        result.put("pageSize", pageSize);
        return result;
    }

    /**
     * 用 userId 回填真实姓名，列表上能看出具体是哪个用户。
     */
    private void fillOperatorNames(List<AuditLogEntry> records) {
        Set<Long> userIds = records.stream()
                .map(AuditLogEntry::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return;
        }
        Map<Long, String> names = userMapper.selectBatchIds(userIds).stream()
                .filter(user -> user.getRealName() != null && !user.getRealName().isBlank())
                .collect(Collectors.toMap(User::getId, User::getRealName, (a, b) -> a));
        for (AuditLogEntry entry : records) {
            if (entry.getUserId() != null) {
                entry.setRealName(names.get(entry.getUserId()));
            }
        }
    }
}
