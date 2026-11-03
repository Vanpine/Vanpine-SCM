package cn.greensmartplanet.gsc.delivery.scheduler;

import cn.greensmartplanet.gsc.delivery.service.DeliveryRiskLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 多实例互斥：每 5 分钟每实例都触发，只有抢到 Redis 锁的实例执行扫描。
 */
@ExtendWith(MockitoExtension.class)
class DeliveryRiskSchedulerTest {

    @Mock
    private DeliveryRiskLifecycleService lifecycleService;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private DeliveryRiskScheduler scheduler;

    @BeforeEach
    void stubRedis() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("抢到锁：执行扫描")
    void scan_lockAcquired_runsScan() {
        stubLock(true);

        scheduler.scanDeliveryRisk();

        verify(lifecycleService).scanRisks();
    }

    @Test
    @DisplayName("抢不到锁：跳过本轮，不扫描")
    void scan_lockHeld_skips() {
        stubLock(false);

        scheduler.scanDeliveryRisk();

        verify(lifecycleService, never()).scanRisks();
    }

    @Test
    @DisplayName("Redis 不可用：保守跳过本轮")
    void scan_redisDown_skips() {
        when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        scheduler.scanDeliveryRisk(); // 不抛即通过

        verify(lifecycleService, never()).scanRisks();
    }

    /** 打桩抢锁结果：true=抢到，false=已被其它实例持有 */
    private void stubLock(boolean acquired) {
        when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(acquired);
    }
}
