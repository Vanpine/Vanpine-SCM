package cn.greensmartplanet.gsc.delivery.service;

import cn.greensmartplanet.gsc.delivery.dto.PartialRejectDTO;
import cn.greensmartplanet.gsc.delivery.dto.RejectDeliveryDTO;
import cn.greensmartplanet.gsc.delivery.dto.ReturnLogisticsDTO;

/**
 * 交付退货状态机服务
 * <p>
 * 从 DeliveryServiceImpl 拆出：验收不合格退回的闭环子状态机——
 * reject（received → return_pending）→ confirmReturn（→ return_confirmed）→
 * fillReturnLogistics（→ return_shipping）→ confirmReturnReceipt（→ returned），
 * 异议 disputeReturn（return_pending → received）回退重验，部分残次 partialReject 生成补发批。
 * 归属校验复用 {@link DeliveryLifecycleService#getDeliveryOrThrow}，终态评估委托 DeliveryAssessmentService。
 *
 * @author gsc
 */
public interface DeliveryReturnService {

    /** 采购商验收不合格（received → return_pending） */
    void reject(Long id, Long tenantId, Long userId, RejectDeliveryDTO dto);

    /** 采购商部分残次（received → 原批 accepted + 补发批 reship_pending） */
    void partialReject(Long id, Long tenantId, Long userId, PartialRejectDTO dto);

    /** 供应商确认退回（return_pending → return_confirmed） */
    void confirmReturn(Long id, Long tenantId, Long userId);

    /** 供应商提出异议（return_pending → received），remark 为异议原因 */
    void disputeReturn(Long id, Long tenantId, Long userId, String remark);

    /** 采购商填写退货物流（return_confirmed → return_shipping） */
    void fillReturnLogistics(Long id, Long tenantId, Long userId, ReturnLogisticsDTO dto);

    /** 供应商确认收到退货（return_shipping → returned） */
    void confirmReturnReceipt(Long id, Long tenantId, Long userId);
}
