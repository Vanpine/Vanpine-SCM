package cn.greensmartplanet.gsc.ai.support;

import cn.greensmartplanet.gsc.ai.dto.AiToolProposal;
import cn.greensmartplanet.gsc.common.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 写操作提案服务测试。
 * <p>
 * 核心语义：
 * <ul>
 *   <li>take 读后即删 → 并发双击第二次拿不到，天然防重放</li>
 *   <li>take 做租户 + 用户归属校验 → 防越权确认他人提案</li>
 *   <li>restore 把失败提案放回 → 下游 Feign 抖动后用户仍可重试</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AiToolProposalServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AiToolProposalService service;

    @BeforeEach
    void setUp() {
        service = new AiToolProposalService(stringRedisTemplate, objectMapper);
    }

    private AiToolProposal proposal(Long tenantId, Long userId) {
        return AiToolProposal.builder()
                .toolName("create_supplier_note")
                .tenantId(tenantId)
                .userId(userId)
                .content("重点供应商")
                .build();
    }

    @Test
    @DisplayName("save 用 10 分钟 TTL 暂存")
    void save_usesTenMinuteTtl() throws Exception {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        String id = service.save(proposal(1L, 2L));

        assertThat(id).isNotBlank();
        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(eq("ai:tool:proposal:" + id), anyString(), ttl.capture());
        assertThat(ttl.getValue()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("take 读后即删（getAndDelete），并发双击第二次拿不到")
    void take_isReadAndDelete() throws Exception {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete("ai:tool:proposal:p1"))
                .thenReturn(objectMapper.writeValueAsString(proposal(1L, 2L)));

        AiToolProposal taken = service.take("p1", 1L, 2L);

        assertThat(taken.getToolName()).isEqualTo("create_supplier_note");
        verify(valueOperations).getAndDelete("ai:tool:proposal:p1");
    }

    @Test
    @DisplayName("归属不符拒绝（防越权确认他人提案）")
    void take_rejectsWrongOwner() throws Exception {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete("ai:tool:proposal:p1"))
                .thenReturn(objectMapper.writeValueAsString(proposal(1L, 2L)));

        assertThatThrownBy(() -> service.take("p1", 1L, 999L))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("提案不存在或已消费时拒绝")
    void take_rejectsMissing() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete(anyString())).thenReturn(null);

        assertThatThrownBy(() -> service.take("p1", 1L, 2L))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("restore 用 2 分钟短 TTL 放回，供用户重试")
    void restore_putsBackWithShortTtl() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        service.restore("p1", proposal(1L, 2L));

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(eq("ai:tool:proposal:p1"), anyString(), ttl.capture());
        assertThat(ttl.getValue()).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    @DisplayName("restore 写入异常不外传（不能掩盖原本的执行失败）")
    void restore_swallowsRedisError() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        // set 返回 void，打桩必须用 doThrow
        doThrow(new RuntimeException("redis down"))
                .when(valueOperations).set(anyString(), anyString(), any(Duration.class));

        service.restore("p1", proposal(1L, 2L));

        // 未抛异常即通过
        verify(valueOperations).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("restore 传 null 时直接返回，不碰 Redis")
    void restore_noopOnNull() {
        service.restore(null, proposal(1L, 2L));
        service.restore("p1", null);

        verify(stringRedisTemplate, never()).opsForValue();
    }
}
