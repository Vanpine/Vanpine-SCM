package cn.greensmartplanet.gsc.api.trade;

import cn.greensmartplanet.gsc.api.constant.ServiceNameConstants;
import cn.greensmartplanet.gsc.api.trade.dto.RequirementInfoDTO;
import cn.greensmartplanet.gsc.api.trade.dto.RequirementMatchFieldsDTO;
import cn.greensmartplanet.gsc.api.trade.dto.RequirementRecallQueryDTO;
import cn.greensmartplanet.gsc.api.trade.factory.RequirementClientFallbackFactory;
import cn.greensmartplanet.gsc.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 交易域服务 Feign 客户端契约 — 采购需求信息查询与达标回调
 * <p>
 * 契约约定（调用方与提供方共同遵守）：
 * <ul>
 *   <li>提供方 gsc-service-trade 的 InternalRequirementController 实现本接口，URL/参数编译期强一致</li>
 *   <li>供应商域用它替代跨服务直查 procurement_requirement 表（对接流程校验/智能匹配/达标翻 completed）</li>
 *   <li>返回类型一律契约 DTO（禁止 Map），字段由编译期保证一致</li>
 *   <li>查询契约：需求不存在返回 data=null（HTTP 200），调用方按 null 走业务分支；
 *       HTTP 错误仅代表交易域不可用，由降级工厂接管</li>
 * </ul>
 *
 * @author gsc
 */
@FeignClient(
        name = ServiceNameConstants.GSC_TRADE_SERVICE,
        path = "/internal/requirement",
        contextId = "requirementClient",
        fallbackFactory = RequirementClientFallbackFactory.class)
public interface RequirementClient {

    /** 按需求ID查基础信息（不存在返回 data=null） */
    @GetMapping("/{id}")
    R<RequirementInfoDTO> getById(@PathVariable("id") Long id);

    /** 智能匹配候选集：按需求ID查语义匹配字段（不存在返回 data=null） */
    @GetMapping("/{id}/match-fields")
    R<RequirementMatchFieldsDTO> getMatchFields(@PathVariable("id") Long id);

    /** 批量按需求ID查基础信息（requirementId → DTO，不存在的需求不在 map 中） */
    @PostMapping("/batch")
    R<Map<Long, RequirementInfoDTO>> getByIds(@RequestBody List<Long> ids);

    /** 对接接受后回调：目标供应商数达标则需求翻 completed（幂等，已 completed 直接成功） */
    @PostMapping("/{id}/complete-if-target-reached")
    R<Boolean> completeIfTargetReached(@PathVariable("id") Long id,
                                       @RequestParam("acceptedCount") long acceptedCount,
                                       @RequestParam("targetSupplierCount") int targetSupplierCount);

    /** 找需求 SQL 召回：进行中公开需求，按品类或地域缩小（一次一个维度） */
    @PostMapping("/recall-for-match")
    R<List<RequirementMatchFieldsDTO>> recallOpenForMatch(@RequestBody RequirementRecallQueryDTO query);
}
