package cn.greensmartplanet.gsc.delivery.service;

import cn.greensmartplanet.gsc.api.delivery.dto.DeliveryStatsDTO;

/**
 * 工作台统计服务接口 — 平台域工作台 BFF 经 Feign 聚合的本域计数 stats 统计
 * <p>
 * ⚠️ 阶段 8.1：统计口径与原单体 DashboardServiceImpl 完全一致
 * （状态集合定义逐项对应），保证切流后工作台数字基线不变。
 *
 * @author gsc
 */
public interface DeliveryStatsService {

    /**
     * 工作台交付/超期计数
     *
     * @param tenantId 租户ID（采购方/供应方视角各自的 WHERE 条件）
     * @return 统计契约 DTO（全字段 long 基本类型）
     */
    DeliveryStatsDTO getDashboardStats(Long tenantId);
}
