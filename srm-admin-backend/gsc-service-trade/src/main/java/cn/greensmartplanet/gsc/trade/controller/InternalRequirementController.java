package cn.greensmartplanet.gsc.trade.controller;

import cn.greensmartplanet.gsc.api.trade.dto.RequirementInfoDTO;
import cn.greensmartplanet.gsc.api.trade.dto.RequirementMatchFieldsDTO;
import cn.greensmartplanet.gsc.api.trade.dto.RequirementRecallQueryDTO;
import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.trade.service.RequirementInternalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 交易内部接口 — 采购需求（仅服务间经内网服务发现 Feign 调用）
 * <p>
 * 网关已用 InternalBlockWebFilter 拒绝 /internal/** 外部访问（403），
 * 此处接口只对注册中心内的其他服务可见。
 * <p>
 * ⚠️ 路径一致性：本控制器路径与 gsc-api 的 RequirementClient 契约保持一致
 * （契约接口为调用方声明，提供方不实现该接口，两端路径由本注释约定同步修改）。
 *
 * @author gsc
 */
@RestController
@RequestMapping("/internal/requirement")
@RequiredArgsConstructor
public class InternalRequirementController {

    private final RequirementInternalService requirementInternalService;

    /** 按需求ID查基础信息（不存在返回 data=null） */
    @GetMapping("/{id}")
    public R<RequirementInfoDTO> getById(@PathVariable("id") Long id) {
        return R.ok(requirementInternalService.getInfoById(id));
    }

    /** 智能匹配候选集：按需求ID查语义匹配字段（不存在返回 data=null） */
    @GetMapping("/{id}/match-fields")
    public R<RequirementMatchFieldsDTO> getMatchFields(@PathVariable("id") Long id) {
        return R.ok(requirementInternalService.getMatchFields(id));
    }

    /** 批量按需求ID查基础信息（不存在的需求不在 map 中） */
    @PostMapping("/batch")
    public R<Map<Long, RequirementInfoDTO>> getByIds(@RequestBody List<Long> ids) {
        return R.ok(requirementInternalService.getInfoByIds(ids));
    }

    /** 对接接受后回调：目标供应商数达标则需求 open → completed（幂等：已 completed 返回 true） */
    @PostMapping("/{id}/complete-if-target-reached")
    public R<Boolean> completeIfTargetReached(@PathVariable("id") Long id,
                                              @RequestParam("acceptedCount") long acceptedCount,
                                              @RequestParam("targetSupplierCount") int targetSupplierCount) {
        return R.ok(requirementInternalService.completeIfTargetReached(id, acceptedCount, targetSupplierCount));
    }

    /** 找需求 SQL 召回（供应商域智能推荐用） */
    @PostMapping("/recall-for-match")
    public R<List<RequirementMatchFieldsDTO>> recallOpenForMatch(@RequestBody RequirementRecallQueryDTO query) {
        return R.ok(requirementInternalService.recallOpenForMatch(query));
    }
}
