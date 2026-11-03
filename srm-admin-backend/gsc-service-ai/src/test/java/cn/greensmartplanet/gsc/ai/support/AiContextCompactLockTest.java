package cn.greensmartplanet.gsc.ai.support;

import cn.greensmartplanet.gsc.ai.entity.AiMessage;
import cn.greensmartplanet.gsc.ai.mapper.AiConversationMapper;
import cn.greensmartplanet.gsc.ai.mapper.AiMessageMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 压缩并发锁 + 压缩批次 token 上限测试。
 * <p>
 * CAS 只保证结果不互相覆盖；不加锁时多实例会各自取同一批消息、各调一次大模型。
 */
@ExtendWith(MockitoExtension.class)
class AiContextCompactLockTest {

    @Mock
    private AiConversationMapper conversationMapper;
    @Mock
    private AiMessageMapper messageMapper;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private AiChatImageDescService imageDescService;

    private AiContextCompactService service() {
        return new AiContextCompactService(
                conversationMapper, messageMapper, objectMapper, redisTemplate, imageDescService);
    }

    @Test
    @DisplayName("抢不到锁则直接返回，不查库、不调大模型")
    void lockBusy_skipsCompaction() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);

        service().compactAsync(1L, 2L, 500L);

        verify(conversationMapper, never()).selectById(any());
        verify(messageMapper, never()).selectCompactable(any(), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("Redis 异常时降级为继续压缩（不因缓存故障让摘要永远落后）")
    void redisError_degradesToCompacting() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RuntimeException("redis down"));
        when(conversationMapper.selectById(1L)).thenReturn(null);

        service().compactAsync(1L, 2L, 500L);

        verify(conversationMapper).selectById(1L);
    }

    @Test
    @DisplayName("批量按 token 截断：超预算的尾部留给下一轮")
    void batchTrimmedByTokenBudget() {
        AiContextCompactService service = service();
        // 每条 400 字 ≈ 400 token，25 条 ≈ 1 万 token > MAX_BATCH_TOKENS(8000)
        List<AiMessage> messages = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            messages.add(AiMessage.builder().id((long) i).role("user").content("字".repeat(400)).build());
        }

        int dropped = service.capByTokenBudget(messages);

        assertThat(dropped).isGreaterThan(0);
        assertThat(messages).isNotEmpty();
        // 留下的是最老的这批（升序头部），尾部被截
        assertThat(messages.get(0).getId()).isZero();
        int used = messages.stream().mapToInt(m -> TokenEstimator.count(m.getContent())).sum();
        assertThat(used).isLessThanOrEqualTo(AiContextCompactService.MAX_BATCH_TOKENS);
    }

    @Test
    @DisplayName("未超预算时不截断")
    void batchWithinBudgetKept() {
        List<AiMessage> messages = List.of(
                AiMessage.builder().id(1L).role("user").content("查合同").build(),
                AiMessage.builder().id(2L).role("assistant").content("共 3 份").build());

        assertThat(service().capByTokenBudget(messages)).isZero();
        assertThat(messages).hasSize(2);
    }
}
