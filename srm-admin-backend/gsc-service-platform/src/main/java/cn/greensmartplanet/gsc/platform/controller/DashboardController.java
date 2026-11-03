package cn.greensmartplanet.gsc.platform.controller;

import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.framework.security.JwtUserDetails;
import cn.greensmartplanet.gsc.platform.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 工作台 Controller — 平台域 BFF（阶段 8.1 从旧单体迁入，URL 不变）
 * <p>
 * 本域计数本地查，询盘/合同与交付/超期计数经 Feign 聚合 trade/delivery，
 * 前端 /api/admin/dashboard 零改动。
 *
 * @author gsc
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@Tag(name = "工作台")
public class DashboardController {

    private final DashboardService dashboardService;

    /** 获取工作台数据（按角色返回 stats/todos/recentActivities） */
    @Operation(summary = "工作台统计")
    @GetMapping
    public R<Map<String, Object>> getDashboard(
            @Parameter(description = "角色标识") @RequestParam(required = false) String role,
            @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(dashboardService.getDashboard(user.getTenantId(), role));
    }
}
