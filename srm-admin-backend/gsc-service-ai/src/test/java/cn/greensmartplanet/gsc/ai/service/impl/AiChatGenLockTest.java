package cn.greensmartplanet.gsc.ai.service.impl;

import cn.greensmartplanet.gsc.ai.mapper.AiConversationMapper;
import cn.greensmartplanet.gsc.ai.mapper.AiMessageImageMapper;
import cn.greensmartplanet.gsc.ai.mapper.AiMessageMapper;
import cn.greensmartplanet.gsc.ai.mapper.AiToolCallMapper;
import cn.greensmartplanet.gsc.ai.service.AiChatImageService;
import cn.greensmartplanet.gsc.ai.support.AiChatImageDescService;
import cn.greensmartplanet.gsc.ai.support.AiContextCompactService;
import cn.greensmartplanet.gsc.ai.support.AiToolTokenService;
import cn.greensmartplanet.gsc.framework.oss.OssService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 回归：会话级生成锁。
 * 原实现只靠 selectLatestStreaming 查库（先查后插），从查库到第 9 步插 streaming 占位之间
 * 隔着图片签名(HTTP)/历史组装/异步压缩/落 user 消息，并发下两个请求都能通过检查，
 * 同一会话产生两条并行回答。改用 Redis 原子占位兜住这个窗口。
 */
@ExtendWith(MockitoExtension.class)
class AiChatGenLockTest {

    private static final Long CONVERSATION_ID = 100L;
    private static final String LOCK_KEY = "ai:chat:gen:" + CONVERSATION_ID;

    @Mock
    private AiConversationMapper conversationMapper;
    @Mock
    private AiMessageMapper messageMapper;
    @Mock
    private AiMessageImageMapper messageImageMapper;
    @Mock
    private AiToolCallMapper toolCallMapper;
    @Mock
    private AiChatImageService chatImageService;
    @Mock
    private AiChatImageDescService imageDescService;
    @Mock
    private OssService ossService;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private AiToolTokenService aiToolTokenService;
    @Mock
    private AiContextCompactService contextCompactService;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private AiChatServiceImpl service;

    @Test
    @DisplayName("抢到锁返回 TRUE")
    void tryAcquire_acquired_returnsTrue() {
        stubSetIfAbsent(true);

        assertThat(service.tryAcquireGenLock(CONVERSATION_ID)).isTrue();
    }

    @Test
    @DisplayName("已被别的请求占用返回 FALSE")
    void tryAcquire_held_returnsFalse() {
        stubSetIfAbsent(false);

        assertThat(service.tryAcquireGenLock(CONVERSATION_ID)).isFalse();
    }

    @Test
    @DisplayName("Redis 不可用返回 null，调用方降级为查库判断")
    void tryAcquire_redisDown_returnsNull() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        assertThat(service.tryAcquireGenLock(CONVERSATION_ID)).isNull();
    }

    @Test
    @DisplayName("持锁时释放：删除锁 key")
    void release_held_deletesKey() {
        service.releaseGenLock(CONVERSATION_ID, true);

        verify(stringRedisTemplate).delete(LOCK_KEY);
    }

    @Test
    @DisplayName("未持锁不释放：避免误删别人的锁")
    void release_notHeld_skips() {
        service.releaseGenLock(CONVERSATION_ID, false);

        verify(stringRedisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("会话ID为空不释放")
    void release_nullConversation_skips() {
        service.releaseGenLock(null, true);

        verify(stringRedisTemplate, never()).delete(anyString());
    }

    private void stubSetIfAbsent(boolean acquired) {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq(LOCK_KEY), anyString(), any(Duration.class))).thenReturn(acquired);
    }
}
