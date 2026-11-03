package cn.greensmartplanet.gsc.trade.service;

import cn.greensmartplanet.gsc.api.trade.dto.TradeStatsDTO;

/**
 * 工作台统计服务接口 — 平台域工作台 BFF 经 Feign 聚合的本域计数
 * <p>
 * ⚠️ 阶段 8.1：统计口径与原单体 DashboardServiceImpl 完全一致
 * （状态集合定义逐项对应），保证切流后工作台数字基线不变。
 *
 * @author gsc
 */
public interface TradeStatsService {

    /**
     * 工作台询盘/合同计数
     *
     * @param tenantId 租户ID；null 表示超管（只取平台级合同总数，其余字段为 0）
     * @return 统计契约 DTO（全字段 long 基本类型）
     */
    TradeStatsDTO getDashboardStats(Long tenantId);
}
