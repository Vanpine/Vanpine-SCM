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
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 链路接缝测试：两个方法各自单测都对，不代表「接起来」是对的。
 * <p>
 * ① 算完窗口（buildHistory）→ 触发压缩（compactAsync）：参数会不会传漏；
 * ② Java 组装请求体 → Python 读字段：字段名写错 Python 不报错、只静默失效。
 */
@ExtendWith(MockitoExtension.class)
class AiChatSeamTest {

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

    // ==================== 接缝①：trimmed → compactAsync ====================

    @Test
    @DisplayName("trimmed=true 时把窗口起点原样交给压缩（参数传漏这里会红）")
    void trimmed_triggersCompactionWithWindowStart() {
        boolean fired = service.triggerCompactionIfTrimmed(1L, 2L, true, 500L, 12);

        assertThat(fired).isTrue();
        verify(contextCompactService).compactAsync(1L, 2L, 500L);
    }

    @Test
    @DisplayName("trimmed=false 时不触发压缩")
    void notTrimmed_skipsCompaction() {
        boolean fired = service.triggerCompactionIfTrimmed(1L, 2L, false, 500L, 12);

        assertThat(fired).isFalse();
        verify(contextCompactService, never()).compactAsync(any(), any(), any());
    }

    // ==================== 接缝②：Java 请求体字段名 ====================

    @Test
    @DisplayName("请求体用蛇形字段名，与 Python ChatRequest 对齐")
    void requestBody_usesSnakeCaseKeys() {
        Map<String, Object> body = service.buildChatRequestBody(
                2L, 7L, 99L, "第 3 条合同的交付单呢",
                List.of(Map.of("role", "user", "content", "列出合同")),
                List.of("https://oss.example.com/a.jpg"),
                "[{\"objectType\":\"contract\",\"objectId\":\"1\",\"title\":\"合同A\"}]",
                "用户之前查过合同");

        assertThat(body)
                .containsKeys("question", "tenant_id", "history", "images",
                        "context_entities", "history_summary", "tool_token")
                .doesNotContainKeys("contextEntities", "historySummary", "tenantId", "toolToken");
    }

    @Test
    @DisplayName("字段集显式锁定：增删字段必须同步 Python 侧 ChatRequest")
    void requestBody_fieldSetIsExplicit() {
        Map<String, Object> body = service.buildChatRequestBody(
                2L, 7L, 99L, "问题", List.of(), List.of("url"), "entities", "summary");

        // 对应 Python tests/test_java_contract.py 的 test_chat_request_field_set_is_explicit。
        // 两边各自锁定字段集：任一侧增删字段，它自己的测试先红，逼开发者想到另一边。
        assertThat(body).containsOnlyKeys(
                "question", "tenant_id", "history", "images",
                "context_entities", "history_summary", "tool_token");
    }

    @Test
    @DisplayName("图片 URL / 上下文实体 / 摘要为空时不下发对应字段")
    void requestBody_omitsBlankOptionalFields() {
        Map<String, Object> body = service.buildChatRequestBody(
                2L, 7L, 99L, "你好", List.of(), List.of(), "  ", null);

        assertThat(body).doesNotContainKeys("images", "context_entities", "history_summary");
    }
}
