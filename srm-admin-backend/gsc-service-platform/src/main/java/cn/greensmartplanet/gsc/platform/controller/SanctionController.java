package cn.greensmartplanet.gsc.platform.controller;

import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.framework.audit.AuditLog;
import cn.greensmartplanet.gsc.platform.mapper.SanctionEntryMapper;
import cn.greensmartplanet.gsc.platform.service.SanctionScreeningService;
import cn.greensmartplanet.gsc.platform.service.SanctionSyncService;
import cn.greensmartplanet.gsc.platform.vo.SanctionScreenResultVO;
import cn.greensmartplanet.gsc.platform.vo.SanctionSyncStatusVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
/**
 * 制裁筛查控制器（平台域） — 名单库 + 同步 + 筛查
 * <p>
 * 超管专用：查看制裁名单库、手动触发同步、查同步状态、对入驻申请做筛查。
 *
 * @author gsc
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('*:*:*', 'admin', 'sanction')")
@Tag(name = "制裁筛查", description = "制裁名单库、同步管理与筛查（平台域）")
public class SanctionController {

    private final SanctionSyncService sanctionSyncService;
    private final SanctionEntryMapper sanctionEntryMapper;
    private final SanctionScreeningService sanctionScreeningService;

    /**
     * 名单库分页列表（超管）
     */
    @Operation(summary = "制裁名单库分页")
    @GetMapping("/sanction/entries")
    public R<Map<String, Object>> page(@RequestParam(defaultValue = "1") int pageNum,
                                       @RequestParam(defaultValue = "10") int pageSize,
                                       @RequestParam(required = false) String keyword) {
        int safePage = Math.max(pageNum, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 50);
        int offset = (safePage - 1) * safeSize;
        Map<String, Object> result = new HashMap<>();
        result.put("records", sanctionEntryMapper.selectPage(keyword, offset, safeSize));
        result.put("total", sanctionEntryMapper.count(keyword));
        result.put("page", safePage);
        result.put("pageSize", safeSize);
        return R.ok(result);
    }

    /**
     * 手动触发同步（异步，立即返回；进度轮询 status 接口）
     */
    @Operation(summary = "手动触发制裁名单同步（异步）")
    @AuditLog(module = "制裁筛查", operation = "UPDATE", desc = "手动同步制裁名单", targetType = "SanctionEntry")
    @PostMapping("/sanction/sync")
    public R<Void> sync() {
        sanctionSyncService.triggerSync();
        return R.ok();
    }

    /**
     * 同步状态（超管名单库页顶部展示）
     */
    @Operation(summary = "制裁名单同步状态")
    @GetMapping("/sanction/sync/status")
    public R<SanctionSyncStatusVO> status() {
        return R.ok(sanctionSyncService.getStatus());
    }

    /**
     * 对入驻申请做制裁筛查（超管打开预审详情页时调，幂等可重查）
     */
    @Operation(summary = "对入驻申请做制裁筛查")
    @AuditLog(module = "制裁筛查", operation = "UPDATE", desc = "筛查入驻申请", targetType = "Application")
    @PostMapping("/sanction/screen/{applicationId}")
    public R<SanctionScreenResultVO> screen(@PathVariable Long applicationId) {
        return R.ok(sanctionScreeningService.screen(applicationId));
    }
}
