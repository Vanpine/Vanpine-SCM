package cn.greensmartplanet.gsc.delivery.controller;

import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.delivery.service.DeliveryQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 交付内部接口 — 仅服务间经内网服务发现（Feign）调用
 * <p>
 * 网关已用 InternalBlockWebFilter 拒绝 /internal/** 外部访问（403），
 * 此处接口只对注册中心内的其他服务可见。
 * <p>
 * ⚠️ 路径一致性：本控制器路径与 gsc-api 的 DeliveryClient 契约保持一致
 * （契约接口为调用方声明，提供方不实现该接口，两端路径由本注释约定同步修改）。
 *
 * @author gsc
 */
@RestController
@RequestMapping("/internal/delivery")
@RequiredArgsConstructor
public class InternalDeliveryController {

    private final DeliveryQueryService deliveryQueryService;

    /** 合同是否存在进行中（非 accepted）交付单（交易域终止/完成合同保护 Feign 调用） */
    @GetMapping("/active")
    public R<Boolean> hasActive(@RequestParam("contractId") Long contractId) {
        return R.ok(deliveryQueryService.hasActiveDelivery(contractId));
    }
}
