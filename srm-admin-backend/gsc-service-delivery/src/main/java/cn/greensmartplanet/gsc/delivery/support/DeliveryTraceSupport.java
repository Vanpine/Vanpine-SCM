package cn.greensmartplanet.gsc.delivery.support;

import cn.greensmartplanet.gsc.delivery.entity.DeliveryTrace;
import cn.greensmartplanet.gsc.delivery.mapper.DeliveryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 交付轨迹落库支持
 * <p>
 * 封装 addTrace（履约时间线插入），从 DeliveryServiceImpl 私有方法迁出。
 * 被主状态机 / 退货状态机 / 承诺交期 / 交期预警共 17 处调用，抽组件避免跨服务重复实现。
 *
 * @author gsc
 */
@Component
@RequiredArgsConstructor
public class DeliveryTraceSupport {

    private final DeliveryMapper deliveryMapper;

    /** 写一条交付轨迹 */
    public void addTrace(Long deliveryId, String action, String actionDesc,
                         Long operatorTenantId, Long operatorUserId, String remark) {
        deliveryMapper.insertTrace(DeliveryTrace.builder()
                .deliveryId(deliveryId)
                .action(action)
                .actionDesc(actionDesc)
                .operatorTenantId(operatorTenantId)
                .operatorUserId(operatorUserId)
                .remark(remark)
                .build());
    }
}
