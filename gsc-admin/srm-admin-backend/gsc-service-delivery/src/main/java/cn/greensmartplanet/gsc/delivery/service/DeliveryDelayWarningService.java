package cn.greensmartplanet.gsc.delivery.service;

/**
 * 交付交期延误预警服务
 * <p>
 * 从 DeliveryServiceImpl 拆出：扫临期/超期未闭环交付单，通知采购商+供应商，记时间线。
 * 定时触发（DeliveryDelayScheduler 每天 9 点）。
 *
 * @author gsc
 */
public interface DeliveryDelayWarningService {

    /** 交期延误预警：扫临期/超期未闭环交付单，通知采购商+供应商，记时间线（定时触发） */
    void warnDeliveryDelay();
}
