package cn.greensmartplanet.gsc.trade.controller;

import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.trade.service.InquiryQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 交易内部接口 — 询盘（仅服务间经内网服务发现 Feign 调用）
 * <p>
 * 网关已用 InternalBlockWebFilter 拒绝 /internal/** 外部访问（403），
 * 此处接口只对注册中心内的其他服务可见。
 * <p>
 * ⚠️ 路径一致性：本控制器路径与 gsc-api 的 InquiryClient 契约保持一致
 * （契约接口为调用方声明，提供方不实现该接口，两端路径由本注释约定同步修改）。
 *
 * @author gsc
 */
@RestController
@RequestMapping("/internal/inquiry")
@RequiredArgsConstructor
public class InternalInquiryController {

    private final InquiryQueryService inquiryQueryService;

    /** 按需求ID查拒绝过相关询盘的供应商租户ID集合（供应商域智能匹配页"已拒绝该询盘"标注） */
    @GetMapping("/declined-suppliers")
    public R<List<Long>> getDeclinedSuppliers(@RequestParam("requirementId") Long requirementId,
                                              @RequestParam("buyerTenantId") Long buyerTenantId) {
        return R.ok(inquiryQueryService.getDeclinedSupplierIds(requirementId, buyerTenantId));
    }
}
