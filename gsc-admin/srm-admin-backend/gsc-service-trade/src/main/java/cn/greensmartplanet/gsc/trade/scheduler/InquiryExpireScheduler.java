package cn.greensmartplanet.gsc.trade.scheduler;

import cn.greensmartplanet.gsc.trade.mapper.InquiryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 询盘过期扫描 — 将 quote_deadline 已过的 open / quoting 询盘改为 expired
 * <p>
 * ⚠️ 微服务拆分：XXL-JOB 管理台 3.x 协议联调暂缓（用户指示），
 * 定时任务先回退 Spring 原生 @Scheduled（fixedRate 每分钟，与原单体一致）。
 * 切流窗口内旧单体同任务会双跑，updateExpired 是 UPDATE 幂等操作，双跑结果收敛。
 *
 * @author gsc
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InquiryExpireScheduler {

    private final InquiryMapper inquiryMapper;

    /** 每分钟扫描过期询盘（与原单体一致） */
    @Scheduled(fixedRate = 60_000)
    public void scanExpiredInquiries() {
        int rows = inquiryMapper.updateExpired();
        if (rows > 0) {
            log.info("询盘过期扫描完成, 过期数={}", rows);
        }
    }
}
