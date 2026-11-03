package cn.greensmartplanet.gsc.ai.service.impl;

import cn.greensmartplanet.gsc.ai.dto.ImageDescriptionDTO;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 历史窗口按 token 预算裁剪测试（阶段四）。
 * <p>
 * 规则：从最近往回累加，凑够预算就停；发生裁剪即表示有老消息掉出窗口，需触发压缩兜底。
 */
@ExtendWith(MockitoExtension.class)
class AiChatServiceImplHistoryWindowTest {

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
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @InjectMocks
    private AiChatServiceImpl service;

    @BeforeEach
    void setBudget() {
        ReflectionTestUtils.setField(service, "historyTokenBudget", 100);
        // 默认无图；图片相关用例自行覆盖
        lenient().when(imageDescService.loadImageContext(any(), any()))
                .thenReturn(AiChatImageDescService.ImageContext.empty());
    }

    private AiMessage msg(long id, String role, String content) {
        return AiMessage.builder().id(id).role(role).content(content).status("complete").build();
    }

    private void stub(List<AiMessage> rows) {
        when(messageMapper.selectRecentByConversation(1L, 1L, 50)).thenReturn(rows);
    }

    @Test
    @DisplayName("纯图片提问（content 为空但带图）要进窗口，否则图片信息完全不可见")
    void imageOnlyMessage_keptInWindow() {
        AiMessage imageOnly = msg(1, "user", "");
        stub(List.of(imageOnly, msg(2, "assistant", "已识别")));
        when(imageDescService.loadImageContext(any(), any()))
                .thenReturn(new AiChatImageDescService.ImageContext(
                        java.util.Set.of(1L),
                        java.util.Map.of(1L, List.of(ImageDescriptionDTO.builder()
                                .index(1).imageId(9001L).description("采购合同扫描件，编号 HT001").build()))));

        AiChatServiceImpl.HistoryResult result = service.buildHistory(1L, 1L, null);

        assertThat(result.messages()).hasSize(2);
        Map<String, Object> first = result.messages().get(0);
        assertThat(first.get("content")).isEqualTo("");
        assertThat(first.get("imageDescriptions")).isNotNull();
    }

    @Test
    @DisplayName("无内容且无图的消息仍被剔除（不误伤原过滤）")
    void emptyMessageWithoutImage_dropped() {
        stub(List.of(msg(1, "user", ""), msg(2, "assistant", "已识别")));

        AiChatServiceImpl.HistoryResult result = service.buildHistory(1L, 1L, null);

        assertThat(result.messages()).hasSize(1);
        assertThat(result.messages().get(0).get("role")).isEqualTo("assistant");
    }

    @Test
    @DisplayName("预算内：全部保留，不触发压缩")
    void withinBudget_keepsAll() {
        stub(List.of(msg(1, "user", "字".repeat(30)), msg(2, "assistant", "字".repeat(30))));

        AiChatServiceImpl.HistoryResult result = service.buildHistory(1L, 1L, null);

        assertThat(result.messages()).hasSize(2);
        assertThat(result.trimmed()).isFalse();
    }

    @Test
    @DisplayName("超预算：丢最早的、留最近的，并标记裁剪")
    void overBudget_dropsOldest() {
        stub(List.of(
                msg(1, "user", "字".repeat(80)),
                msg(2, "assistant", "字".repeat(80)),
                msg(3, "user", "字".repeat(80))));

        AiChatServiceImpl.HistoryResult result = service.buildHistory(1L, 1L, null);

        assertThat(result.messages()).hasSize(1);
        assertThat(result.trimmed()).isTrue();
    }

    @Test
    @DisplayName("标准场景：预算装得下 2 条装不下 3 条")
    void budgetFitsTwoNotThree() {
        stub(List.of(
                msg(1, "user", "字".repeat(40)),
                msg(2, "assistant", "字".repeat(40)),
                msg(3, "user", "字".repeat(40))));

        AiChatServiceImpl.HistoryResult result = service.buildHistory(1L, 1L, null);

        assertThat(result.messages()).hasSize(2);
        assertThat(result.trimmed()).isTrue();
    }

    @Test
    @DisplayName("单条就超预算：至少保留最近一条，不清空历史")
    void singleOverBudget_keepsLatest() {
        stub(List.of(msg(1, "user", "字".repeat(500))));

        AiChatServiceImpl.HistoryResult result = service.buildHistory(1L, 1L, null);

        assertThat(result.messages()).hasSize(1);
        assertThat(result.trimmed()).isFalse();
    }

    @Test
    @DisplayName("候选取满 SQL 上限：即使全部装进预算也要标记裁剪（否则更老的消息静默丢失）")
    void fetchLimitReached_marksTrimmed() {
        // 50 条短消息共 50 字符，预算 100 装得下 → 只按「kept < candidates」判断会是 false；
        // 但 SQL 只取了最近 50 条，第 50 条之前的老消息既没进窗口、也没进摘要
        List<AiMessage> rows = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            rows.add(msg(i, "user", "字"));
        }
        stub(rows);

        AiChatServiceImpl.HistoryResult result = service.buildHistory(1L, 1L, null);

        assertThat(result.messages()).hasSize(50);
        assertThat(result.trimmed())
                .as("候选取满 FETCH_LIMIT 说明可能还有更老的没取到，必须触发压缩兜底")
                .isTrue();
    }

    @Test
    @DisplayName("返回窗口起点（最早那条的 id），供压缩做上界，避免压到窗口内的原文")
    void returnsWindowStartMessageId() {
        stub(List.of(
                msg(11, "user", "字".repeat(40)),
                msg(12, "assistant", "字".repeat(40)),
                msg(13, "user", "字".repeat(40))));

        AiChatServiceImpl.HistoryResult result = service.buildHistory(1L, 1L, null);

        // 预算 100 只装得下后两条（12、13），最早一条 11 掉出窗口
        assertThat(result.messages()).hasSize(2);
        assertThat(result.windowStartMessageId()).isEqualTo(12L);
    }

    @Test
    @DisplayName("窗口为空时窗口起点为 null（压缩不设上界）")
    void emptyWindow_nullWindowStart() {
        stub(List.of());

        AiChatServiceImpl.HistoryResult result = service.buildHistory(1L, 1L, null);

        assertThat(result.messages()).isEmpty();
        assertThat(result.windowStartMessageId()).isNull();
    }

    @Test
    @DisplayName("裁剪后仍按时间升序（最近的排在最后）")
    void keepsChronologicalOrder() {
        stub(List.of(msg(1, "user", "第一条"), msg(2, "assistant", "第二条")));

        AiChatServiceImpl.HistoryResult result = service.buildHistory(1L, 1L, null);

        assertThat(result.messages()).extracting(m -> m.get("content"))
                .containsExactly("第一条", "第二条");
    }

    @Test
    @DisplayName("半截回答（cancelled）与空内容不进历史")
    void filtersIncompleteAndBlank() {
        AiMessage cut = AiMessage.builder().id(2L).role("assistant").content("半截").status("cancelled").build();
        AiMessage blank = AiMessage.builder().id(3L).role("user").content("   ").status("complete").build();
        stub(List.of(msg(1, "user", "你好"), cut, blank));

        AiChatServiceImpl.HistoryResult result = service.buildHistory(1L, 1L, null);

        assertThat(result.messages()).hasSize(1);
        assertThat(result.messages().get(0).get("content")).isEqualTo("你好");
    }

    @Test
    @DisplayName("重生成时排除被否的旧 assistant，避免模型复述")
    void excludesRegeneratedAssistant() {
        stub(List.of(msg(1, "user", "你好"), msg(2, "assistant", "旧答案")));

        AiChatServiceImpl.HistoryResult result = service.buildHistory(1L, 1L, 2L);

        assertThat(result.messages()).hasSize(1);
        assertThat(result.messages().get(0).get("content")).isEqualTo("你好");
    }

    // ==================== assistant 长回答截断（预算按截断后长度算） ====================

    @Test
    @DisplayName("短回答原样保留")
    void trim_shortUntouched() {
        assertThat(service.trimAssistantHistory("共 3 份合同。")).isEqualTo("共 3 份合同。");
    }

    @Test
    @DisplayName("长回答：保留表格之前的结论句，表格整块丢弃")
    void trim_dropsTableBlock() {
        String content = "上轮 3 条中第 2 条是 **pcb采集**：\n\n"
                + "| 字段 | 内容 |\n|---|---|\n"
                + "| 状态 | 已取消 |\n| 品类 | PCB线路板 |\n| 数量 | 500 件 |\n"
                + "| 期望交付日期 | 2026-08-15 |\n| 报价截止 | 2026-08-07 00:00 |\n"
                + "| 付款条件 | 款到发货 |\n\n"
                + "该询盘已取消、报价截止也已过，不能延长期限；如需继续采购，需重新发布询盘。";
        assertThat(content.length()).as("用例本身要超 120 字符，否则走不到截断分支").isGreaterThan(120);

        String out = service.trimAssistantHistory(content);

        assertThat(out).contains("pcb采集");
        assertThat(out).doesNotContain("|");
        assertThat(out).doesNotContain("500");
    }

    @Test
    @DisplayName("回答一上来就是表格、没有结论句时用占位文案")
    void trim_tableFirst_placeholder() {
        String content = "| 字段 | 内容 |\n|---|---|\n| 状态 | 已取消 |\n".repeat(20);

        assertThat(service.trimAssistantHistory(content)).isEqualTo("（上一轮回复了一个表格，内容已省略）");
    }

    @Test
    @DisplayName("纯文字长回答：按段落累加，不超限就多带几段")
    void trim_accumulatesParagraphs() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append("第").append(i).append("段").append("字".repeat(30)).append("\n\n");
        }

        String out = service.trimAssistantHistory(sb.toString());

        assertThat(out).contains("第0段");
        assertThat(out).endsWith("…（历史答案已截断）");
    }

    @Test
    @DisplayName("截断结果恒 ≤120 字符，保证 Python 侧同名截断是空操作（不会二次截断）")
    void trim_neverExceedsLimit() {
        assertThat(service.trimAssistantHistory("结".repeat(500))).hasSizeLessThanOrEqualTo(120);
        assertThat(service.trimAssistantHistory("无空行超长段落".repeat(100))).hasSizeLessThanOrEqualTo(120);
    }

    @Test
    @DisplayName("预算按截断后的长度计：长回答不再白占预算")
    void buildHistory_countsTrimmedLength() {
        // 预算 100：assistant 原文 500 字符，截断后 ≤120 但仍超预算 → 只留最近一条
        stub(List.of(
                msg(1, "assistant", "字".repeat(500)),
                msg(2, "user", "追问")));

        AiChatServiceImpl.HistoryResult result = service.buildHistory(1L, 1L, null);

        // 追问（2 字）装得下；截断后的 assistant 仍超预算被裁掉 —— 关键是它没按 500 字符把窗口撑爆
        assertThat(result.messages()).hasSize(1);
        assertThat(result.messages().get(0).get("content")).isEqualTo("追问");
        assertThat(result.trimmed()).isTrue();
    }
}
