package cn.greensmartplanet.gsc.trade.controller;

import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.framework.audit.AuditLog;
import cn.greensmartplanet.gsc.framework.security.JwtUserDetails;
import cn.greensmartplanet.gsc.trade.dto.PublishRequirementDTO;
import cn.greensmartplanet.gsc.trade.service.RequirementLifecycleService;
import cn.greensmartplanet.gsc.trade.service.RequirementQueryService;
import cn.greensmartplanet.gsc.trade.vo.RequirementVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 采购需求 Controller（需求域迁回交易域，阶段 9 归属纠正）
 * <p>
 * 由 MatchRequirementController 改名而来（Service 与 Controller 命名对齐，8.2 规范）。
 * ⚠️ 历史：阶段 5 需求 8 端点误放供应商域（/api/admin/match/requirement/**），
 * 阶段 9 按表归属（procurement_requirement 归 trade）迁回本服务；
 * 网关按精确路径路由，URL 对前端零变化。
 * <p>
 * /requirement/{id}/match 与 /declined-suppliers 仍归供应商域
 * （依赖画像匹配策略与询盘邀请表），不由本控制器提供。
 * <p>
 * 需求端点按职责委托给 RequirementQueryService（读）+ RequirementLifecycleService（写）。
 *
 * @author gsc
 */
@RestController
@RequestMapping("/api/admin/match/requirement")
@RequiredArgsConstructor
@Tag(name = "采购需求管理")
public class RequirementController {

    private final RequirementQueryService requirementQueryService;
    private final RequirementLifecycleService requirementLifecycleService;

    /** 发布采购需求 */
    @AuditLog(module = "需求管理", operation = "CREATE", desc = "发布采购需求", targetType = "Requirement")
    @Operation(summary = "发布采购需求")
    @PostMapping
    public R<RequirementVO> publishRequirement(@Valid @RequestBody PublishRequirementDTO dto,
                                               @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(requirementLifecycleService.publish(user.getTenantId(), dto));
    }

    /** 分页查我的需求列表 */
    @Operation(summary = "我的需求列表")
    @GetMapping
    public R<Map<String, Object>> listMyRequirements(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "状态筛选：open/closed") @RequestParam(required = false) String status,
            @Parameter(description = "关键词搜索（标题/品类/地域/预算）") @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(requirementQueryService.listMyRequirements(user.getTenantId(), pageNum, pageSize, status, keyword));
    }

    /** 查需求详情 */
    @Operation(summary = "需求详情")
    @GetMapping("/{id}")
    public R<RequirementVO> getRequirementDetail(@PathVariable Long id,
                                                 @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(requirementQueryService.getDetail(id, user.getTenantId()));
    }

    /** 编辑需求 */
    @AuditLog(module = "需求管理", operation = "UPDATE", desc = "编辑需求", targetType = "Requirement")
    @Operation(summary = "编辑需求")
    @PutMapping("/{id}")
    public R<Void> updateRequirement(@PathVariable Long id,
                                     @Valid @RequestBody PublishRequirementDTO dto,
                                     @AuthenticationPrincipal JwtUserDetails user) {
        requirementLifecycleService.update(id, user.getTenantId(), dto);
        return R.ok();
    }

    /** 重新发布需求 */
    @AuditLog(module = "需求管理", operation = "UPDATE", desc = "重新发布需求", targetType = "Requirement")
    @Operation(summary = "重新发布需求")
    @PutMapping("/{id}/republish")
    public R<Void> republishRequirement(@PathVariable Long id,
                                        @Valid @RequestBody PublishRequirementDTO dto,
                                        @AuthenticationPrincipal JwtUserDetails user) {
        requirementLifecycleService.republish(id, user.getTenantId(), dto);
        return R.ok();
    }

    /** 关闭需求 */
    @AuditLog(module = "需求管理", operation = "UPDATE", desc = "关闭需求", targetType = "Requirement")
    @Operation(summary = "关闭需求")
    @PutMapping("/{id}/close")
    public R<Void> closeRequirement(@PathVariable Long id,
                                    @AuthenticationPrincipal JwtUserDetails user) {
        requirementLifecycleService.close(id, user.getTenantId());
        return R.ok();
    }

    /** 需求广场：供应商浏览全平台公开需求（排除自己发布的，支持品类关键词搜索） */
    @Operation(summary = "需求广场列表")
    @GetMapping("/plaza")
    public R<Map<String, Object>> listPlaza(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "品类关键词") @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(requirementQueryService.listOpenRequirements(user.getTenantId(), keyword, pageNum, pageSize));
    }
}
