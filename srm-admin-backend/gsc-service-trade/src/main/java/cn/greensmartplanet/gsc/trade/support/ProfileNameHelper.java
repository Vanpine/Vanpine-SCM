package cn.greensmartplanet.gsc.trade.support;

import cn.greensmartplanet.gsc.api.supplier.ProfileClient;
import cn.greensmartplanet.gsc.api.supplier.dto.ProfileInfoDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 企业名查询帮助类 — 统一封装 ProfileClient（画像归供应商域，Feign 查询）
 * <p>
 * 消除各 ServiceImpl 重复的私有 getCompanyName，列表/详情场景用批量接口消 N+1。
 * <p>
 * 降级语义（红线 6）：公司名为展示性字段，供应商服务不可用时降级为空 + warn 日志，不影响主业务。
 *
 * @author gsc
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileNameHelper {

    private final ProfileClient profileClient;

    /**
     * 按租户ID查企业名（不存在/降级返回 null）
     */
    public String getCompanyName(Long tenantId) {
        if (tenantId == null) return null;
        return getCompanyNames(Set.of(tenantId)).get(tenantId);
    }

    /**
     * 批量按租户ID查企业名（tenantId → companyName）
     * <p>
     * 入参为空/降级返回空 map；内部去重并过滤 null，一次 Feign 批量查询。
     */
    public Map<Long, String> getCompanyNames(Collection<Long> tenantIds) {
        if (tenantIds == null || tenantIds.isEmpty()) return Map.of();
        // 1. 去重 + 过滤 null，避免重复/空值触发 Feign 批量查询
        List<Long> distinctIds = tenantIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (distinctIds.isEmpty()) return Map.of();
        try {
            // 2. 一次批量查询全部画像（画像归供应商域，Feign 契约）
            Map<Long, ProfileInfoDTO> profiles = profileClient.getByTenantIds(distinctIds).getData();
            if (profiles == null) return Map.of();
            // 3. 精简为「租户ID → 公司名称」，空值跳过
            Map<Long, String> names = new HashMap<>();
            for (Map.Entry<Long, ProfileInfoDTO> entry : profiles.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null && entry.getValue().getCompanyName() != null) {
                    names.put(entry.getKey(), entry.getValue().getCompanyName());
                }
            }
            return names;
        } catch (Exception e) {
            log.warn("批量查询企业名失败，降级为空: tenantIds={}, cause={}", distinctIds, e.getMessage());
            return Map.of();
        }
    }
}
