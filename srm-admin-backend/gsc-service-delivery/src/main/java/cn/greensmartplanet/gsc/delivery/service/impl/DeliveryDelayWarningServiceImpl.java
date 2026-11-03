package cn.greensmartplanet.gsc.delivery.service.impl;

import cn.greensmartplanet.gsc.delivery.entity.Delivery;
import cn.greensmartplanet.gsc.delivery.entity.Notification;
import cn.greensmartplanet.gsc.delivery.mapper.DeliveryMapper;
import cn.greensmartplanet.gsc.delivery.service.DeliveryDelayWarningService;
import cn.greensmartplanet.gsc.delivery.service.NotificationService;
import cn.greensmartplanet.gsc.delivery.support.DeliveryTraceSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 交付交期延误预警服务实现
 * <p>
 * 扫临期/超期未闭环交付单，逐单通知采购商+供应商并记时间线。
 * 轨迹落库委托 {@link DeliveryTraceSupport}。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryDelayWarningServiceImpl implements DeliveryDelayWarningService {

    private final DeliveryMapper deliveryMapper;
    private final NotificationService notificationService;
    private final DeliveryTraceSupport traceSupport;

    /** 交期延误预警：扫临期/超期未闭环交付单，通知采购商+供应商，记时间线（定时触发） */
    @Override
    public void warnDeliveryDelay() {
        LocalDate today = LocalDate.now();
        LocalDate deadline = today.plusDays(3);
        List<Delivery> list = deliveryMapper.selectDelayWarningCandidates(deadline);
        if (list.isEmpty()) return;
        for (Delivery d : list) {
            // 考核基准：已确认承诺交期优先，无则回退合同交期
            LocalDate effectiveDate = d.getConfirmedDeliveryDate() != null
                    ? d.getConfirmedDeliveryDate() : d.getDeliveryDate();
            String dateStr = effectiveDate != null ? effectiveDate.toString() : "未设置";
            boolean overdue = effectiveDate != null && effectiveDate.isBefore(today);
            String statusText = overdue ? "已超期" : "即将到期";
            int batchNo = d.getBatchNo() != null ? d.getBatchNo() : 1;
            String subject = "交付单【" + d.getDeliveryNo() + "】第 " + batchNo + " 批";
            notificationService.notify(Notification.builder()
                    .type("DELIVERY_DELAY_WARNING")
                    .title("交付交期预警")
                    .content(subject + "交期 " + dateStr + " " + statusText + "，请关注供应商履约进度")
                    .receiverTenantId(d.getBuyerTenantId())
                    .relatedType("delivery")
                    .relatedId(d.getId())
                    .build());
            notificationService.notify(Notification.builder()
                    .type("DELIVERY_DELAY_WARNING")
                    .title("交付交期预警")
                    .content(subject + "交期 " + dateStr + " " + statusText + "，请尽快安排发货")
                    .receiverTenantId(d.getSupplierTenantId())
                    .relatedType("delivery")
                    .relatedId(d.getId())
                    .build());
            traceSupport.addTrace(d.getId(), "delay_warning", "交期预警", null, null, "交期 " + dateStr + "，" + statusText);
        }
        log.info("交期延误预警扫描完成, 预警数={}", list.size());
    }
}
