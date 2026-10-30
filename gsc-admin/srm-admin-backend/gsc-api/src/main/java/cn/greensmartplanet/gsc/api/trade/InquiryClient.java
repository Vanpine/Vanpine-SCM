package cn.greensmartplanet.gsc.api.trade;

import cn.greensmartplanet.gsc.api.constant.ServiceNameConstants;
import cn.greensmartplanet.gsc.api.trade.factory.InquiryClientFallbackFactory;
import cn.greensmartplanet.gsc.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 交易域服务 Feign 客户端契约 — 询盘信息查询
 * <p>
 * 契约约定（调用方与提供方共同遵守）：
 * <ul>
 *   <li>提供方 gsc-service-trade 的 InternalTradeController 实现本接口，URL/参数编译期强一致</li>
 *   <li>供应商域用它替代跨服务直查 inquiry_invite 表（智能匹配页"已拒绝该询盘"标注）</li>
 *   <li>降级（FallbackFactory）：返回空列表——该标注仅是展示性提示，交易域不可用时
 *       匹配页最多不显示"已拒绝"标注，不阻断匹配主流程</li>
 * </ul>
 *
 * @author gsc
 */
@FeignClient(
        name = ServiceNameConstants.GSC_TRADE_SERVICE,
        path = "/internal/inquiry",
        contextId = "inquiryClient",
        fallbackFactory = InquiryClientFallbackFactory.class)
public interface InquiryClient {

    /** 按需求ID查拒绝过相关询盘的供应商租户ID集合（智能匹配页"已拒绝该询盘"标注） */
    @GetMapping("/declined-suppliers")
    R<List<Long>> getDeclinedSuppliers(@RequestParam("requirementId") Long requirementId,
                                       @RequestParam("buyerTenantId") Long buyerTenantId);
}
