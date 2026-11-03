package cn.greensmartplanet.gsc.api.trade;

import cn.greensmartplanet.gsc.api.constant.ServiceNameConstants;
import cn.greensmartplanet.gsc.api.trade.dto.TradeStatsDTO;
import cn.greensmartplanet.gsc.api.trade.factory.TradeStatsClientFallbackFactory;
import cn.greensmartplanet.gsc.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 交易域服务 Feign 客户端契约 — 工作台统计计数
 * <p>
 * 契约约定（调用方与提供方共同遵守）：
 * <ul>
 *   <li>提供方 gsc-service-trade 的 InternalStatsController 实现本接口，URL/参数编译期强一致</li>
 *   <li>平台域工作台 BFF 经此契约聚合询盘/合同计数，替代跨服务直查 trade 表</li>
 *   <li>返回一律契约 DTO（字段为 long 基本类型，无需判空）</li>
 *   <li>降级（FallbackFactory）：统计数字是工作台纯展示性数据，trade 不可用时
 *       返回全 0 + warn 日志，工作台照常渲染（比直查时整个面板挂掉更稳）</li>
 * </ul>
 *
 * @author gsc
 */
@FeignClient(
        name = ServiceNameConstants.GSC_TRADE_SERVICE,
        path = "/internal/stats",
        contextId = "tradeStatsClient",
        fallbackFactory = TradeStatsClientFallbackFactory.class)
public interface TradeStatsClient {

    /** 工作台询盘/合同计数（tenantId=null 表示超管，只取平台级合同总数） */
    @GetMapping("/dashboard")
    R<TradeStatsDTO> getDashboardStats(@RequestParam(value = "tenantId", required = false) Long tenantId);
}
