package cn.greensmartplanet.gsc.delivery.scheduler;

import cn.greensmartplanet.gsc.delivery.service.DeliveryDelayWarningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 交期延误预警调度器 — 每天早 9 点扫描临期/超期未闭环交付单，通知采购商与供应商
 * <p>
 * ⚠️ 微服务拆分：XXL-JOB 管理台 3.x 协议联调暂缓（用户指示），
 * 定时任务先回退 Spring 原生 @Scheduled（每天 9 点，与原单体一致）。
 * 切流窗口内旧单体同任务会双跑，delay_warning 轨迹按交付单只预警一次，双跑结果收敛。
 *
 * @author gsc
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryDelayScheduler {

    private final DeliveryDelayWarningService delayWarningService;

    /** 每天早 9 点扫描一次（交期前 3 天预警，预警一次不重复） */
    @Scheduled(cron = "0 0 9 * * *")
    public void scanDeliveryDelay() {
        try {
            delayWarningService.warnDeliveryDelay();
        } catch (Exception e) {
            log.error("交期延误预警扫描异常", e);
        }
    }
}
