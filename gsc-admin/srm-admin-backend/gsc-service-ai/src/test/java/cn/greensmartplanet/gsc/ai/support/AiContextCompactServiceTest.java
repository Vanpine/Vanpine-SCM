package cn.greensmartplanet.gsc.ai.support;

import cn.greensmartplanet.gsc.ai.entity.AiConversation;
import cn.greensmartplanet.gsc.ai.entity.AiMessage;
import cn.greensmartplanet.gsc.ai.mapper.AiConversationMapper;
import cn.greensmartplanet.gsc.ai.mapper.AiMessageMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会话上下文压缩测试。
 * <p>
 * 压缩是旁路：任何失败都只能记日志、保留旧摘要，不能影响主对话。
 * callCompact 打桩，避免测试真的发 HTTP。
 */
@ExtendWith(MockitoExtension.class)
class AiContextCompactServiceTest {

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

    private AiContextCompactService service;

    @BeforeEach
    void setUp() {
        // 默认抢锁成功（锁本身的失败分支由 AiContextCompactLockTest 覆盖）
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        // 默认无图片描述（图片链路本身由 AiChatImageDescServiceTest 覆盖）
        lenient().when(imageDescService.loadImageContext(any(), any()))
                .thenReturn(AiChatImageDescService.ImageContext.empty());
        service = spy(new AiContextCompactService(
                conversationMapper, messageMapper, objectMapper, redisTemplate, imageDescService));
    }

    private AiConversation conversation(Long id, Long tenantId) {
        return AiConversation.builder().id(id).tenantId(tenantId).build();
    }

    private AiMessage message(Long id) {
        return AiMessage.builder().id(id).role("user").content("查我的合同").status("complete").build();
    }

    /** 造 count 条消息（id 从 11 起），用于凑够最小压缩批次。 */
    private List<AiMessage> messages(int count) {
        return IntStream.range(0, count).mapToObj(i -> message(11L + i)).toList();
    }

    @Test
    @DisplayName("会话不存在时不压缩")
    void missingConversation_skips() {
        when(conversationMapper.selectById(1L)).thenReturn(null);

        service.compactAsync(1L, 1L, null);

        verify(messageMapper, never()).selectCompactable(any(), any(), any(), any(), anyInt());
        verify(conversationMapper, never()).updateSummaryIfUnchanged(any(), any(), any(), any());
    }

    @Test
    @DisplayName("租户不匹配时不压缩（防跨租户写摘要）")
    void tenantMismatch_skips() {
        when(conversationMapper.selectById(1L)).thenReturn(conversation(1L, 99L));

        service.compactAsync(1L, 1L, null);

        verify(messageMapper, never()).selectCompactable(any(), any(), any(), any(), anyInt());
        verify(conversationMapper, never()).updateSummaryIfUnchanged(any(), any(), any(), any());
    }

    @Test
    @DisplayName("没有可压的老消息时不写回（短会话走这里）")
    void noCompactableMessages_skips() {
        when(conversationMapper.selectById(1L)).thenReturn(conversation(1L, 1L));
        when(messageMapper.selectCompactable(eq(1L), eq(1L), isNull(), any(), anyInt())).thenReturn(List.of());

        service.compactAsync(1L, 1L, null);

        verify(conversationMapper, never()).updateSummaryIfUnchanged(any(), any(), any(), any());
    }

    @Test
    @DisplayName("条数不够但内容够多时照压（长回答积压比寒暄积压危害大）")
    void fewMessagesButLongContent_compacts() throws Exception {
        when(conversationMapper.selectById(1L)).thenReturn(conversation(1L, 1L));
        // 3 条 × 800 字 ≈ 2600 token，超过 MIN_BATCH_TOKENS(2000)
        List<AiMessage> longOnes = IntStream.range(0, 3)
                .mapToObj(i -> AiMessage.builder().id(21L + i).role("assistant")
                        .content("字".repeat(800)).status("complete").build())
                .toList();
        when(messageMapper.selectCompactable(eq(1L), eq(1L), isNull(), any(), anyInt())).thenReturn(longOnes);
        doReturn("用户查了合同。").when(service).callCompact(any(), any());
        when(conversationMapper.updateSummaryIfUnchanged(any(), any(), any(), any())).thenReturn(1);

        service.compactAsync(1L, 1L, null);

        verify(service).callCompact(any(), any());
    }

    @Test
    @DisplayName("攒不够最小批次时不压（省一次大模型调用）")
    void belowMinBatch_skips() {
        when(conversationMapper.selectById(1L)).thenReturn(conversation(1L, 1L));
        when(messageMapper.selectCompactable(eq(1L), eq(1L), isNull(), any(), anyInt())).thenReturn(messages(5));

        service.compactAsync(1L, 1L, null);

        verify(conversationMapper, never()).updateSummaryIfUnchanged(any(), any(), any(), any());
    }

    @Test
    @DisplayName("写回走 CAS：带上读到的旧覆盖点，摘要与覆盖点一次推进")
    void compacts_writesSummaryViaCas() throws Exception {
        AiConversation existing = conversation(1L, 1L);
        existing.setSummaryUpToMsgId(99L);
        when(conversationMapper.selectById(1L)).thenReturn(existing);
        when(messageMapper.selectCompactable(eq(1L), eq(1L), eq(99L), any(), anyInt())).thenReturn(messages(6));
        doReturn("用户查了合同，确认钢材合同已签署。").when(service).callCompact(any(), any());
        when(conversationMapper.updateSummaryIfUnchanged(any(), any(), any(), any())).thenReturn(1);

        service.compactAsync(1L, 1L, null);

        // 覆盖点推进到本批最后一条（messages(6) 的 id 从 11 起 → 16），旧值 99 原样带上做 CAS
        verify(conversationMapper).updateSummaryIfUnchanged(
                1L, "用户查了合同，确认钢材合同已签署。", 16L, 99L);
    }

    @Test
    @DisplayName("CAS 影响 0 行（被并发压缩抢先）时静默放弃，不抛异常也不覆盖摘要")
    void compacts_casConflict_givesUpQuietly() throws Exception {
        AiConversation existing = conversation(1L, 1L);
        existing.setSummaryUpToMsgId(99L);
        when(conversationMapper.selectById(1L)).thenReturn(existing);
        when(messageMapper.selectCompactable(eq(1L), eq(1L), eq(99L), any(), anyInt())).thenReturn(messages(6));
        doReturn("新摘要").when(service).callCompact(any(), any());
        when(conversationMapper.updateSummaryIfUnchanged(any(), any(), any(), any())).thenReturn(0);

        assertThatCode(() -> service.compactAsync(1L, 1L, null)).doesNotThrowAnyException();

        verify(conversationMapper).updateSummaryIfUnchanged(1L, "新摘要", 16L, 99L);
    }

    @Test
    @DisplayName("摘要覆盖点与窗口起点都传给查询：既不重复压同一批，也不压到窗口内的原文")
    void passesWatermarkAndWindowStart() {
        AiConversation existing = conversation(1L, 1L);
        existing.setSummaryUpToMsgId(99L);
        when(conversationMapper.selectById(1L)).thenReturn(existing);
        when(messageMapper.selectCompactable(eq(1L), eq(1L), eq(99L), eq(500L), anyInt())).thenReturn(List.of());

        service.compactAsync(1L, 1L, 500L);

        verify(messageMapper).selectCompactable(1L, 1L, 99L, 500L, 40);
    }

    @Test
    @DisplayName("压缩返回空摘要时不写回，保留旧摘要")
    void blankSummary_keepsOld() throws Exception {
        when(conversationMapper.selectById(1L)).thenReturn(conversation(1L, 1L));
        when(messageMapper.selectCompactable(eq(1L), eq(1L), isNull(), any(), anyInt()))
                .thenReturn(messages(6));
        doReturn("  ").when(service).callCompact(any(), any());

        service.compactAsync(1L, 1L, null);

        verify(conversationMapper, never()).updateSummaryIfUnchanged(any(), any(), any(), any());
    }

    @Test
    @DisplayName("压缩抛异常不冒泡（旁路失败不能拖垮主对话）")
    void exceptionDoesNotPropagate() throws Exception {
        when(conversationMapper.selectById(1L)).thenReturn(conversation(1L, 1L));
        when(messageMapper.selectCompactable(eq(1L), eq(1L), isNull(), any(), anyInt()))
                .thenReturn(messages(6));
        doThrow(new RuntimeException("python 挂了")).when(service).callCompact(any(), any());

        assertThatCode(() -> service.compactAsync(1L, 1L, null)).doesNotThrowAnyException();
        verify(conversationMapper, never()).updateSummaryIfUnchanged(any(), any(), any(), any());
    }
}
