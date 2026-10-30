package cn.greensmartplanet.gsc.api.delivery;

import cn.greensmartplanet.gsc.api.constant.ServiceNameConstants;
import cn.greensmartplanet.gsc.api.delivery.dto.AiDeliveryDetailDTO;
import cn.greensmartplanet.gsc.api.delivery.dto.AiDeliveryOperateRequest;
import cn.greensmartplanet.gsc.api.delivery.dto.AiDeliveryQuery;
import cn.greensmartplanet.gsc.api.delivery.dto.AiDeliverySummaryDTO;
import cn.greensmartplanet.gsc.api.delivery.factory.AiDeliveryClientFallbackFactory;
import cn.greensmartplanet.gsc.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 交付域服务 Feign 客户端契约 — 专供 AI 工具调用的交付查询 + 写操作
 * <p>
 * 契约约定（调用方与提供方共同遵守）：
 * <ul>
 *   <li>提供方 gsc-service-delivery 的 InternalAiDeliveryController 实现本接口，URL/参数编译期强一致</li>
 *   <li>AI 域用它替代直查/直改 delivery 表，交付数据由交付域统一出口</li>
 *   <li>返回类型一律契约 DTO（禁止 Map），字段由编译期保证一致</li>
 *   <li>list 无则空列表；写操作 tenantId/userId 由 AI 域从登录态注入，交付域校验归属后落库</li>
 *   <li>降级（FallbackFactory）：抛 BizException——写操作降级若静默通过会误以为已落库，
 *       查询降级会喂给模型空数据编造答案，均不静默通过</li>
 * </ul>
 *
 * @author gsc
 */
@FeignClient(
        name = ServiceNameConstants.GSC_DELIVERY_SERVICE,
        path = "/internal/ai/delivery",
        contextId = "aiDeliveryClient",
        fallbackFactory = AiDeliveryClientFallbackFactory.class)
public interface AiDeliveryClient {

    /** 按当前租户（buyer/supplier 双视角）分页查交付单摘要（无则空列表） */
    @PostMapping("/list")
    R<List<AiDeliverySummaryDTO>> listDeliveries(@RequestBody AiDeliveryQuery query);

    /** 按交付单ID查详情（含履约时间线 + 物流事件 + 承诺记录 + 退货信息；不存在/非本人返回 data=null） */
    @PostMapping("/get")
    R<AiDeliveryDetailDTO> getDelivery(@RequestBody AiDeliveryQuery query);

    /** 供应商提交承诺交期（AI 工具 submit_promise_date 二次确认后；写操作，返回新 promiseId） */
    @PostMapping("/promise-date")
    R<Long> submitPromiseDate(@RequestBody AiDeliveryOperateRequest request);

    /** 采购方填写退货物流（AI 工具 fill_return_logistics 二次确认后；写操作） */
    @PostMapping("/return-logistics")
    R<Void> fillReturnLogistics(@RequestBody AiDeliveryOperateRequest request);
}
