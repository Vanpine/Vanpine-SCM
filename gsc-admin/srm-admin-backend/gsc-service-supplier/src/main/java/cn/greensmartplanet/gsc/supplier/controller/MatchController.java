package cn.greensmartplanet.gsc.supplier.controller;

import cn.greensmartplanet.gsc.common.result.PageResult;
import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.framework.security.JwtUserDetails;
import cn.greensmartplanet.gsc.supplier.service.MatchQueryService;
import cn.greensmartplanet.gsc.supplier.service.impl.RequirementPlazaRecommendService;
import cn.greensmartplanet.gsc.supplier.vo.RequirementRecommendVO;
import cn.greensmartplanet.gsc.supplier.vo.SupplierRecommendVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 智能匹配 Controller（供应商域：纯匹配）
 * <p>
 * 单资源控制器：只服务"智能匹配候选/拒绝标注"。薄壳转发，Feign 编排与归属/权限校验在 MatchQueryService。
 *
 * @author gsc
 */
@RestController
@RequestMapping("/api/admin/match/requirement")
@RequiredArgsConstructor
@Tag(name = "智能撮合")
public class MatchController {

    private final MatchQueryService matchQueryService;
    private final RequirementPlazaRecommendService requirementPlazaRecommendService;

    /** 智能匹配候选供应商：名单同步返回，AI 理由后台写入后前端轮询 */
    @Operation(summary = "智能匹配供应商")
    @GetMapping("/{id}/match")
    public R<List<SupplierRecommendVO>> matchSuppliers(@PathVariable Long id,
                                                       @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(matchQueryService.getMatchedSuppliers(id, user.getTenantId()));
    }

    /** 查拒绝过该需求相关询盘的供应商（智能匹配页"已拒绝该询盘"标注）——询盘表归交易域，Feign 查询 */
    @Operation(summary = "查询盘拒绝供应商")
    @GetMapping("/{id}/declined-suppliers")
    public R<List<Long>> getDeclinedSuppliers(@PathVariable Long id,
                                              @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(matchQueryService.getDeclinedSupplierIds(id, user.getTenantId()));
    }

    /**
     * 需求广场「找供应商」智能推荐。
     * 路径多一段 /plaza-recommend，不命中 trade 的 requirement/* 精确路由，仍走供应商域。
     */
    @Operation(summary = "广场智能推荐供应商")
    @GetMapping("/{id}/plaza-recommend")
    public R<PageResult<SupplierRecommendVO>> recommendForPlaza(
            @PathVariable Long id,
            @Parameter(description = "关键词（企业名/品类/行业/地区）") @RequestParam(required = false) String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize,
            @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(matchQueryService.recommendForPlaza(id, user.getTenantId(), keyword, pageNum, pageSize));
    }

    /**
     * 需求广场「找需求」智能推荐。
     * 两段路径 /plaza/recommend-requirements，避开网关 trade 的 requirement/* 精确路由。
     */
    @Operation(summary = "广场智能推荐需求")
    @GetMapping("/plaza/recommend-requirements")
    public R<PageResult<RequirementRecommendVO>> recommendRequirementsForPlaza(
            @Parameter(description = "关键词（标题/品类/地域/资质/采购企业）") @RequestParam(required = false) String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize,
            @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(requirementPlazaRecommendService.recommend(user.getTenantId(), keyword, pageNum, pageSize));
    }
}
