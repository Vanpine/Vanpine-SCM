package cn.greensmartplanet.gsc.delivery.service;

import cn.greensmartplanet.gsc.api.delivery.dto.AiDeliveryDetailDTO;
import cn.greensmartplanet.gsc.api.delivery.dto.AiDeliveryOperateRequest;
import cn.greensmartplanet.gsc.api.delivery.dto.AiDeliveryQuery;
import cn.greensmartplanet.gsc.api.delivery.dto.AiDeliverySummaryDTO;

import java.util.List;

/**
 * 交付 AI 适配服务 — 把交付域业务查询/写操作适配成 AI 契约 DTO
 * <p>
 * 只做「AI DTO 组装 + 状态中文 + 业务 DTO 转换」，不重写业务：
 * 读路径复用 {@link DeliveryQueryService}，写路径委托
 * {@link DeliveryPromiseDateService} / {@link DeliveryReturnService}。
 *
 * @author gsc
 */
public interface DeliveryAiService {

    /** 分页查交付单摘要（buyer/supplier 双视角），填 statusText 中文 */
    List<AiDeliverySummaryDTO> listForAi(AiDeliveryQuery query);

    /** 按交付单ID查详情（含履约时间线 + 物流事件 + 承诺记录 + 退货信息），填 statusText 中文 */
    AiDeliveryDetailDTO getForAi(AiDeliveryQuery query);

    /** 供应商提交承诺交期（委托 DeliveryPromiseDateService），返回新 promiseId */
    Long submitPromiseDate(AiDeliveryOperateRequest request);

    /** 采购方填写退货物流（委托 DeliveryReturnService） */
    void fillReturnLogistics(AiDeliveryOperateRequest request);
}
