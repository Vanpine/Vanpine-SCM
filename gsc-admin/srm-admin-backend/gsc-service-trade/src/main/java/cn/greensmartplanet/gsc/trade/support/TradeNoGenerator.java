package cn.greensmartplanet.gsc.trade.support;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 业务编号生成器 — 统一 询盘/报价/合同 的编号规则
 * <p>
 * 编号规则：前缀 + yyyyMMdd + 6 位 Redis 自增序号（如 CTR20260903000001）。
 * 以「domain:seq:日期」为自增键，首次自增时给键设 2 天过期，避免 Redis 键无限堆积。
 * <p>
 * 抽离前各 ServiceImpl 各自内联同款逻辑（generateInquiryNo/generateQuoteNo/generateContractNo），
 * 统一收敛到此组件，消除重复。
 *
 * @author gsc
 */
@Component
@RequiredArgsConstructor
public class TradeNoGenerator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int SEQ_WIDTH = 6;
    private static final Duration KEY_TTL = Duration.ofDays(2);

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 生成业务编号
     *
     * @param prefix      编号前缀（询盘 RFQ / 报价 QTE / 合同 CTR）
     * @param redisDomain Redis 自增键域名（inquiry / quote / contract）
     */
    public String generate(String prefix, String redisDomain) {
        // 1. 当日日期 + 自增键（首次自增设 2 天过期，防止键堆积）
        String date = LocalDate.now().format(DATE_FMT);
        String key = redisDomain + ":seq:" + date;
        Long seq = stringRedisTemplate.opsForValue().increment(key);
        if (seq == 1) {
            stringRedisTemplate.expire(key, KEY_TTL);
        }
        // 2. 拼接 前缀 + 日期 + 6 位序号
        return prefix + date + String.format("%0" + SEQ_WIDTH + "d", seq);
    }
}
