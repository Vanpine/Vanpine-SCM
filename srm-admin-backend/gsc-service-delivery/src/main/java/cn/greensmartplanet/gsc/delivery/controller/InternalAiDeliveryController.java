package cn.greensmartplanet.gsc.delivery.controller;

import cn.greensmartplanet.gsc.api.delivery.dto.AiDeliveryDetailDTO;
import cn.greensmartplanet.gsc.api.delivery.dto.AiDeliveryOperateRequest;
import cn.greensmartplanet.gsc.api.delivery.dto.AiDeliveryQuery;
import cn.greensmartplanet.gsc.api.delivery.dto.AiDeliverySummaryDTO;
import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.delivery.service.DeliveryAiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 交付内部接口 — AI 工具交付查询 + 写操作（仅服务间经内网服务发现 Feign 调用）
 * <p>
 * 网关已用 InternalBlockWebFilter 拒绝 /internal/** 外部访问（403），
 * 此处接口只对注册中心内的其他服务可见。
 * <p>
 * ⚠️ 路径一致性：本控制器路径与 gsc-api 的 AiDeliveryClient 契约保持一致
 * （契约接口为调用方声明，提供方不实现该接口，两端路径由本注释约定同步修改）。
 * <p>
 * 安全边界：写操作的 tenantId/userId 由 AI 域从登录态注入（模型不可伪造），
 * 交付域据此校验归属 + 状态前置后落库。
 *
 * @author gsc
 */
@RestController
@RequestMapping("/internal/ai/delivery")
@RequiredArgsConstructor
public class InternalAiDeliveryController {

    private final DeliveryAiService deliveryAiService;

    /** 按当前租户（buyer/supplier 双视角）分页查交付单摘要（AI 工具 list_deliveries；无则空列表） */
    @PostMapping("/list")
    public R<List<AiDeliverySummaryDTO>> listDeliveries(@RequestBody AiDeliveryQuery query) {
        return R.ok(deliveryAiService.listForAi(query));
    }

    /** 按交付单ID查详情（AI 工具 get_delivery_detail；不存在/非本人返回 data=null） */
    @PostMapping("/get")
    public R<AiDeliveryDetailDTO> getDelivery(@RequestBody AiDeliveryQuery query) {
        return R.ok(deliveryAiService.getForAi(query));
    }

    /** 供应商提交承诺交期（AI 工具 submit_promise_date 二次确认后；写操作，返回新 promiseId） */
    @PostMapping("/promise-date")
    public R<Long> submitPromiseDate(@RequestBody AiDeliveryOperateRequest request) {
        return R.ok(deliveryAiService.submitPromiseDate(request));
    }

    /** 采购方填写退货物流（AI 工具 fill_return_logistics 二次确认后；写操作） */
    @PostMapping("/return-logistics")
    public R<Void> fillReturnLogistics(@RequestBody AiDeliveryOperateRequest request) {
        deliveryAiService.fillReturnLogistics(request);
        return R.ok();
    }
}
