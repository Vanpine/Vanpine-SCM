package cn.greensmartplanet.gsc.trade.controller;

import cn.greensmartplanet.gsc.api.trade.dto.TradeStatsDTO;
import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.trade.service.TradeStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 交易内部接口 — 工作台统计（仅服务间经内网服务发现 Feign 调用）
 * <p>
 * 网关已用 InternalBlockWebFilter 拒绝 /internal/** 外部访问（403），
 * 此处接口只对注册中心内的其他服务可见。
 * <p>
 * ⚠️ 路径一致性：本控制器路径与 gsc-api 的 TradeStatsClient 契约保持一致
 * （契约接口为调用方声明，提供方不实现该接口，两端路径由本注释约定同步修改）。
 *
 * @author gsc
 */
@RestController
@RequestMapping("/internal/stats")
@RequiredArgsConstructor
public class InternalStatsController {

    private final TradeStatsService tradeStatsService;

    /** 工作台询盘/合同计数（tenantId 缺省为超管，只取平台级合同总数） */
    @GetMapping("/dashboard")
    public R<TradeStatsDTO> getDashboardStats(@RequestParam(value = "tenantId", required = false) Long tenantId) {
        return R.ok(tradeStatsService.getDashboardStats(tenantId));
    }
}
