package cn.greensmartplanet.gsc.platform.service.impl;

import cn.greensmartplanet.gsc.platform.mapper.SanctionEntryMapper;
import cn.greensmartplanet.gsc.platform.mq.producer.SanctionIndexRefreshPublisher;
import cn.greensmartplanet.gsc.platform.support.SanctionNameIndex;
import cn.greensmartplanet.gsc.platform.support.SanctionSyncAsyncRunner;
import cn.greensmartplanet.gsc.platform.vo.SanctionSyncStatusVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 回归：
 * 1) @Async 必须经独立 Bean 的代理生效（原同类自调用静默退化为同步，手动触发接口 10s 超时）。
 * 2) 并发闸门必须是 Redis 锁（原 AtomicBoolean 只挡本 JVM，多实例各下载 70MB）。
 * 3) 同步状态必须存 Redis（原 volatile 实例字段，LB 轮转下状态错乱）。
 */
@ExtendWith(MockitoExtension.class)
class SanctionSyncServiceImplTest {

    private static final String STATUS_KEY = "sanction:sync:status";

    @Mock
    private SanctionEntryMapper sanctionEntryMapper;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private SanctionNameIndex nameIndex;
    @Mock
    private SanctionSyncAsyncRunner asyncRunner;
    @Mock
    private SanctionIndexRefreshPublisher indexRefreshPublisher;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private HashOperations<String, Object, Object> hashOps;

    @InjectMocks
    private SanctionSyncServiceImpl service;

    // ==================== 并发闸门 + 异步投递 ====================

    @Test
    @DisplayName("抢到锁：把同步任务交给异步执行器，调用线程不阻塞")
    void triggerSync_delegatesToAsyncRunner() {
        stubLock(true);
        stubHash();

        service.triggerSync();

        // 任务被投递给异步执行器（而不是在本线程里直接跑 doSync 下载 70MB）
        verify(asyncRunner).run(any(Runnable.class));
        // 状态写 Redis（跨实例共享），而非实例字段
        verify(hashOps).putAll(eq(STATUS_KEY), anyMap());
    }

    @Test
    @DisplayName("抢不到锁（别的实例正在同步）：跳过，不投递任务")
    void triggerSync_lockHeld_skips() {
        stubLock(false);

        service.triggerSync();

        verify(asyncRunner, never()).run(any());
    }

    @Test
    @DisplayName("Redis 不可用：保守跳过，不投递任务")
    void triggerSync_redisDown_skips() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        service.triggerSync();

        verify(asyncRunner, never()).run(any());
    }

    // ==================== 状态跨实例共享 ====================

    @Test
    @DisplayName("getStatus 从 Redis 读状态（任一实例读到的都一致）")
    void getStatus_readsFromRedis() {
        stubHash();
        Map<Object, Object> stored = new HashMap<>();
        stored.put("status", "success");
        stored.put("error", "");
        stored.put("count", "72831");
        stored.put("version", "20261019");
        when(hashOps.entries(STATUS_KEY)).thenReturn(stored);

        SanctionSyncStatusVO vo = service.getStatus();

        assertThat(vo.getLastSyncStatus()).isEqualTo("success");
        assertThat(vo.getSyncedCount()).isEqualTo(72831);
        assertThat(vo.getVersion()).isEqualTo("20261019");
    }

    @Test
    @DisplayName("Redis 里没有状态时回退默认值 never / 0")
    void getStatus_redisEmpty_fallsBack() {
        stubHash();
        when(hashOps.entries(STATUS_KEY)).thenReturn(Map.of());

        SanctionSyncStatusVO vo = service.getStatus();

        assertThat(vo.getLastSyncStatus()).isEqualTo("never");
        assertThat(vo.getSyncedCount()).isZero();
    }

    // ==================== 打桩辅助 ====================

    /** 打桩抢锁结果：true=抢到，false=已被其它实例持有 */
    private void stubLock(boolean acquired) {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(acquired);
    }

    private void stubHash() {
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOps);
    }
}
