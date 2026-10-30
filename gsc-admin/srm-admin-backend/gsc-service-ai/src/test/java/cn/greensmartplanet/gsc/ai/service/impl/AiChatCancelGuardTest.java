package cn.greensmartplanet.gsc.ai.service.impl;

import cn.greensmartplanet.gsc.ai.dto.ChatCancelDTO;
import cn.greensmartplanet.gsc.ai.entity.AiConversation;
import cn.greensmartplanet.gsc.ai.entity.AiMessage;
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
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 停止生成的删除守卫测试。
 * <p>
 * {@code cancelChat} 先 SELECT 找 streaming 占位、再 DELETE——两步不是原子的。
 * 若流恰好在中间完成（CAS 成 complete 并写入正文），无守卫的 DELETE 会把一条
 * 已写好的完整回答物理删掉，用户丢失回答。
 */
@ExtendWith(MockitoExtension.class)
class AiChatCancelGuardTest {

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

    @InjectMocks
    private AiChatServiceImpl service;

    private static final Long CONV = 1L;
    private static final Long TENANT = 1L;
    private static final Long USER = 2L;
    private static final Long MSG = 9L;

    private ChatCancelDTO dto(String content, boolean regenerate) {
        ChatCancelDTO dto = new ChatCancelDTO();
        dto.setConversationId(CONV);
        dto.setContent(content);
        dto.setRegenerate(regenerate);
        return dto;
    }

    private void stubStreaming() {
        when(conversationMapper.selectByIdAndTenantUser(CONV, TENANT, USER))
                .thenReturn(AiConversation.builder().id(CONV).tenantId(TENANT).build());
        when(messageMapper.selectLatestStreaming(CONV, TENANT))
                .thenReturn(AiMessage.builder().id(MSG).role("assistant").status("streaming").build());
    }

    @Test
    @DisplayName("删占位走带 status 守卫的方法（防误删已完成的回答）")
    void deletePlaceholder_usesGuardedDelete() {
        stubStreaming();

        service.cancelChat(dto(null, false), TENANT, USER);

        verify(messageMapper).deleteStreamingByIdAndConversation(MSG, CONV, TENANT);
        verify(messageMapper, never()).deleteByIdAndConversation(any(), any(), any());
    }

    @Test
    @DisplayName("重生成删占位同样走守卫方法")
    void regenerate_usesGuardedDelete() {
        stubStreaming();

        service.cancelChat(dto("半段", true), TENANT, USER);

        verify(messageMapper).deleteStreamingByIdAndConversation(MSG, CONV, TENANT);
        verify(messageMapper, never()).deleteByIdAndConversation(any(), any(), any());
    }

    @Test
    @DisplayName("已有正文时走 CAS 落库，不删（守卫方法也不该被调用）")
    void withContent_usesCasInsteadOfDelete() {
        stubStreaming();
        // dto.citations 为 null → resolveCancelCitationsJson 提前返回，不触发序列化
        when(messageMapper.finalizeStreaming(eq(MSG), eq(CONV), eq(TENANT), anyString(),
                isNull(), isNull(), eq("cancelled"))).thenReturn(1);

        service.cancelChat(dto("已经吐出来的半段回答", false), TENANT, USER);

        verify(messageMapper, never()).deleteStreamingByIdAndConversation(any(), any(), any());
        verify(messageMapper).finalizeStreaming(eq(MSG), eq(CONV), eq(TENANT), anyString(),
                isNull(), isNull(), eq("cancelled"));
    }
}
