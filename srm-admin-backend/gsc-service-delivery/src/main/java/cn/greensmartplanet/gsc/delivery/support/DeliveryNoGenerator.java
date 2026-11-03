package cn.greensmartplanet.gsc.delivery.support;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 交付单编号生成器
 * <p>
 * 格式 DEL + yyyyMMdd + 6 位 Redis 自增序号（按日过期）。
 * 从 DeliveryServiceImpl 私有方法迁出，供创建/拆批/补发等多处复用
 * （createFromContract / createManually / splitBatch / partialReject / shipFromBatchPlan）。
 *
 * @author gsc
 */
@Component
@RequiredArgsConstructor
public class DeliveryNoGenerator {

    private final StringRedisTemplate stringRedisTemplate;

    /** 生成交付单编号 DEL + yyyyMMdd + 6位Redis自增序号 */
    public String generate() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String key = "delivery:seq:" + date;
        Long seq = stringRedisTemplate.opsForValue().increment(key);
        if (seq == 1) {
            stringRedisTemplate.expire(key, Duration.ofDays(2));
        }
        return "DEL" + date + String.format("%06d", seq);
    }
}
