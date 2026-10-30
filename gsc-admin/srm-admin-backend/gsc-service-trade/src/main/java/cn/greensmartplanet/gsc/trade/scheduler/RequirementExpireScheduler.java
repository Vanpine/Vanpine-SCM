package cn.greensmartplanet.gsc.trade.scheduler;

import cn.greensmartplanet.gsc.trade.mapper.ProcurementRequirementMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 采购需求过期扫描 — 将 deadline 已过的 open 需求改为 expired
 * <p>
 * ⚠️ 微服务拆分：XXL-JOB 管理台 3.x 协议联调暂缓（用户指示），
 * 定时任务先回退 Spring 原生 @Scheduled（fixedRate 每 5 分钟，与原单体一致）。
 * 切流窗口内旧单体同任务会双跑，updateExpired 是 UPDATE 幂等操作，双跑结果收敛。
 * <p>
 * 独立成 scheduler 类（与 InquiryExpireScheduler 同模式），不再藏在 ServiceImpl 中。
 *
 * @author gsc
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RequirementExpireScheduler {

    private final ProcurementRequirementMapper requirementMapper;

    /** 每 5 分钟扫描过期需求 */
    @Scheduled(fixedRate = 300_000)
    public void scanExpiredRequirements() {
        int rows = requirementMapper.updateExpired();
        if (rows > 0) {
            log.info("扫描过期需求完成, 过期数={}", rows);
        }
    }
}
