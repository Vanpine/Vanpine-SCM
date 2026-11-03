package cn.greensmartplanet.gsc.supplier.controller;

import cn.greensmartplanet.gsc.api.supplier.dto.MyConnectionStatusDTO;
import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.supplier.service.ConnectionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 供应商内部接口 — 对接记录（仅服务间经内网服务发现 Feign 调用）
 * <p>
 * 网关已用 InternalBlockWebFilter 拒绝 /internal/** 外部访问（403），
 * 此处接口只对注册中心内的其他服务可见。
 * <p>
 * ⚠️ 路径一致性：本控制器路径与 gsc-api 的 ConnectionClient 契约保持一致
 * （契约接口为调用方声明，提供方不实现该接口，两端路径由本注释约定同步修改）。
 *
 * @author gsc
 */
@RestController
@RequestMapping("/internal/connection")
@RequiredArgsConstructor
public class InternalConnectionController {

    private final ConnectionQueryService connectionQueryService;

    /** 按需求ID批量查已接受对接数（requirementId → count） */
    @GetMapping("/counts")
    public R<Map<Long, Long>> countGroupByRequirementIds(@RequestParam("buyerTenantId") Long buyerTenantId,
                                                         @RequestParam("requirementIds") List<Long> requirementIds) {
        return R.ok(connectionQueryService.countAcceptedByRequirementIds(buyerTenantId, requirementIds));
    }

    /** 查某租户在多个需求下的对接状态（requirementId → DTO；无记录不在 map 中） */
    @GetMapping("/my-status")
    public R<Map<Long, MyConnectionStatusDTO>> getMyConnectionStatus(@RequestParam("tenantId") Long tenantId,
                                                                     @RequestParam("requirementIds") List<Long> requirementIds) {
        return R.ok(connectionQueryService.getMyConnectionStatusByRequirementIds(tenantId, requirementIds));
    }
}
