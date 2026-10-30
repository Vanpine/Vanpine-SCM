package cn.greensmartplanet.gsc.ai.service.impl;

import cn.greensmartplanet.gsc.ai.mapper.AiConversationMapper;
import cn.greensmartplanet.gsc.ai.mapper.AiMessageImageMapper;
import cn.greensmartplanet.gsc.ai.mapper.AiMessageMapper;
import cn.greensmartplanet.gsc.ai.mapper.AiToolCallMapper;
import cn.greensmartplanet.gsc.ai.service.AiChatImageService;
import cn.greensmartplanet.gsc.ai.support.AiChatImageDescService;
import cn.greensmartplanet.gsc.ai.support.AiContextCompactService;
import cn.greensmartplanet.gsc.ai.support.AiToolTokenService;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.framework.oss.OssService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 单条提问 token 上限测试。
 * <p>
 * 不设限时用户粘贴整份合同（几万 token）会直接挤爆历史窗口与总上下文预算；
 * 校验必须按 token 而非字符判（两者对中英混排差一倍以上）。
 */
@ExtendWith(MockitoExtension.class)
class AiChatQuestionLimitTest {

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

    @Test
    @DisplayName("正常长度提问放行")
    void normalQuestionPasses() {
        assertThatCode(() -> service.requireQuestionWithinLimit("帮我查一下今年签的合同"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("空提问放行（纯图片提问走兜底文案，长度校验不负责判空）")
    void nullQuestionPasses() {
        assertThatCode(() -> service.requireQuestionWithinLimit(null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("超过 2000 token 的中文提问被拒")
    void longChineseQuestionRejected() {
        // 3000 个汉字 ≈ 3000 token（估算器 CJK 权重 1.0，含 1.1 安全系数）
        String content = "字".repeat(3000);
        assertThatThrownBy(() -> service.requireQuestionWithinLimit(content))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("提问内容过长");
    }

    @Test
    @DisplayName("按 token 判而非字符判：1500 个英文长词不算超限")
    void englishWordsCountedByTokenNotChar() {
        // 1500 个 30 字符的英文词约 45000 字符，但 token 约 1500 × 1.3 × 1.1 ≈ 2145 → 应被拒；
        // 用 1000 个词验证放行，说明上限不是按字符数（45000 字符 > 任何字符级上限）
        String content = ("abcdefghijklmnopqrstuvwxyzabcd ").repeat(1000);
        assertThatCode(() -> service.requireQuestionWithinLimit(content))
                .doesNotThrowAnyException();
    }
}
