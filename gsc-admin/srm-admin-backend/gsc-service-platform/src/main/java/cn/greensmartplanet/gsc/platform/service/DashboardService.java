package cn.greensmartplanet.gsc.platform.service;

import java.util.Map;

/**
 * 工作台服务接口 — 平台域 BFF
 * <p>
 * ⚠️ 阶段 8.1：Dashboard BFF 拆分。本域计数（租户/近期动态）本地查，
 * 询盘/合同计数经 TradeStatsClient、交付/超期计数经 DeliveryStatsClient 聚合；
 * 卡片的组装逻辑（label/跳转链接/双身份合并）全部在本域，与原单体行为一致。
 *
 * @author gsc
 */
public interface DashboardService {

    /**
     * 工作台聚合：按角色分发 stats/todos/近期动态，双身份合并采购+供应两组指标
     *
     * @param tenantId 租户ID（超管传 null）
     * @param role     角色标识（SUPER_ADMIN / TENANT_ADMIN / BUYER / SUPPLIER / BOTH）
     * @return {stats, todos, recentActivities}
     */
    Map<String, Object> getDashboard(Long tenantId, String role);
}
