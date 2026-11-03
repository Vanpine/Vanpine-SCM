package cn.greensmartplanet.gsc.delivery.service;

import cn.greensmartplanet.gsc.delivery.dto.PromiseDateDTO;

/**
 * 交付承诺交期服务
 * <p>
 * 从 DeliveryServiceImpl 拆出：供应商提交承诺交期 → 采购方确认（写回考核基准）/ 拒绝。
 * 归属校验复用 {@link DeliveryLifecycleService#getDeliveryOrThrow}，自持 promiseDateMapper。
 *
 * @author gsc
 */
public interface DeliveryPromiseDateService {

    /** 供应商提交承诺交期（未闭环单，同单 pending 时不可重复提交），通知采购方 */
    Long submitPromiseDate(Long deliveryId, Long tenantId, Long userId, PromiseDateDTO dto);

    /** 采购方确认承诺交期：写回 delivery.confirmed_delivery_date，通知供应商 */
    void confirmPromiseDate(Long promiseId, Long tenantId, Long userId);

    /** 采购方拒绝承诺交期，通知供应商 */
    void rejectPromiseDate(Long promiseId, Long tenantId, Long userId);
}
