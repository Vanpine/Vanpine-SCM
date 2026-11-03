package cn.greensmartplanet.gsc.supplier.scheduler;

import cn.greensmartplanet.gsc.supplier.mapper.EnterpriseProfileMapper;
import cn.greensmartplanet.gsc.supplier.service.impl.ProfileSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 画像向量对账：Milvus 少于 MySQL 才回填；一致不回填；AI 服务不可用静默跳过；
 * 多实例抢不到锁跳过本轮。
 */
@ExtendWith(MockitoExtension.class)
class ProfileVectorReconcileSchedulerTest {

    @Mock
    private EnterpriseProfileMapper enterpriseProfileMapper;
    @Mock
    private ProfileSyncService profileSyncService;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private RestClient restClient;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOps;

    @InjectMocks
    private ProfileVectorReconcileScheduler scheduler;

    @BeforeEach
    void stubRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("Milvus 少于 MySQL 时触发全量回填")
    void reconcile_missing_triggersSync() {
        stubLock(true);
        when(enterpriseProfileMapper.countAllSuppliers()).thenReturn(7L);
        stubHealth(5);

        scheduler.reconcileSupplierVectors();

        verify(profileSyncService).syncAll();
    }

    @Test
    @DisplayName("两侧一致时不触发回填")
    void reconcile_consistent_skipsSync() {
        stubLock(true);
        when(enterpriseProfileMapper.countAllSuppliers()).thenReturn(7L);
        stubHealth(7);

        scheduler.reconcileSupplierVectors();

        verify(profileSyncService, never()).syncAll();
    }

    @Test
    @DisplayName("AI 服务不可用时静默跳过，不抛异常也不回填")
    void reconcile_aiUnavailable_skipsSilently() {
        stubLock(true);
        when(enterpriseProfileMapper.countAllSuppliers()).thenReturn(7L);
        when(restClient.get().uri("/api/health").retrieve().body(Map.class))
                .thenThrow(new ResourceAccessException("connection refused"));

        scheduler.reconcileSupplierVectors(); // 不抛即通过

        verify(profileSyncService, never()).syncAll();
    }

    @Test
    @DisplayName("抢不到锁时跳过本轮（多实例互斥），连数量都不查")
    void reconcile_lockHeld_skipsWholeRound() {
        stubLock(false);

        scheduler.reconcileSupplierVectors();

        verify(enterpriseProfileMapper, never()).countAllSuppliers();
        verify(profileSyncService, never()).syncAll();
    }

    @Test
    @DisplayName("Redis 不可用时保守跳过本轮")
    void reconcile_redisDown_skipsWholeRound() {
        when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        scheduler.reconcileSupplierVectors(); // 不抛即通过

        verify(enterpriseProfileMapper, never()).countAllSuppliers();
        verify(profileSyncService, never()).syncAll();
    }

    /** 打桩抢锁结果：true=抢到，false=已被其它实例持有 */
    private void stubLock(boolean acquired) {
        when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(acquired);
    }

    /** 打桩 GET /api/health 返回指定 supplier_count */
    private void stubHealth(int supplierCount) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "ok");
        body.put("supplier_count", supplierCount);
        when(restClient.get().uri("/api/health").retrieve().body(Map.class)).thenReturn(body);
    }
}
