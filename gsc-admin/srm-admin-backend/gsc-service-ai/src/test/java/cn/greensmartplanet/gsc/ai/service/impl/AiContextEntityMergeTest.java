package cn.greensmartplanet.gsc.ai.service.impl;

import cn.greensmartplanet.gsc.ai.entity.AiConversation;
import cn.greensmartplanet.gsc.ai.mapper.AiConversationMapper;
import cn.greensmartplanet.gsc.ai.mapper.AiMessageImageMapper;
import cn.greensmartplanet.gsc.ai.mapper.AiMessageMapper;
import cn.greensmartplanet.gsc.ai.mapper.AiToolCallMapper;
import cn.greensmartplanet.gsc.ai.service.AiChatImageService;
import cn.greensmartplanet.gsc.ai.support.AiChatImageDescService;
import cn.greensmartplanet.gsc.ai.support.AiContextCompactService;
import cn.greensmartplanet.gsc.ai.support.AiToolTokenService;
import cn.greensmartplanet.gsc.framework.oss.OssService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 回归：context_entities 必须是 LRU 合并，不能整列覆盖。
 * <p>
 * 原实现每次把上下文替换为「本次工具结果的前 5 条」，同一轮里后一个工具会把前一个的结果冲掉
 * （先 list_contracts 再查交付单，合同上下文就没了），跨轮也无法累积。
 */
@ExtendWith(MockitoExtension.class)
class AiContextEntityMergeTest {

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

    /**
     * LambdaUpdateWrapper 的列名解析依赖 MyBatis-Plus 的 TableInfo 缓存，
     * 纯单测没有 Spring 上下文，需手动注册 AiConversation 的元信息。
     */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), AiConversation.class);
    }

    @Test
    @DisplayName("本次工具结果排在队首，旧上下文保持在后")
    void merge_incomingFirst() {
        List<Map<String, Object>> merged = service.mergeContextEntities(
                List.of(entity("contract", "1", "合同A")),
                List.of(entity("delivery", "9", "交付单X")));

        assertThat(merged).extracting(m -> m.get("title"))
                .containsExactly("交付单X", "合同A");
    }

    @Test
    @DisplayName("同一对象重复出现只留一条，且移到队首（LRU）")
    void merge_dedupAndPromote() {
        List<Map<String, Object>> merged = service.mergeContextEntities(
                List.of(entity("contract", "1", "合同A"), entity("contract", "2", "合同B")),
                List.of(entity("contract", "2", "合同B-新标题")));

        assertThat(merged).hasSize(2);
        assertThat(merged.get(0))
                .containsEntry("objectId", "2")
                .containsEntry("title", "合同B-新标题");
    }

    @Test
    @DisplayName("objectType 不同、objectId 相同不算同一对象")
    void merge_differentTypeSameId_keptSeparately() {
        List<Map<String, Object>> merged = service.mergeContextEntities(
                List.of(entity("contract", "1", "合同A")),
                List.of(entity("delivery", "1", "交付单X")));

        assertThat(merged).hasSize(2);
    }

    @Test
    @DisplayName("超过上限丢队尾，保留最近的 10 个")
    void merge_capsAtLimit() {
        List<Map<String, Object>> existing = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            existing.add(entity("contract", String.valueOf(i), "合同" + i));
        }

        List<Map<String, Object>> merged = service.mergeContextEntities(
                existing, List.of(entity("delivery", "99", "最新的交付单")));

        assertThat(merged).hasSize(10);
        assertThat(merged.get(0)).containsEntry("title", "最新的交付单");
    }

    @Test
    @DisplayName("updateContextEntities 先读现有上下文再落库（不是整列覆盖）")
    void updateContextEntities_readsThenWrites() throws Exception {
        AiConversation existing = new AiConversation();
        existing.setContextEntities("[]");
        when(conversationMapper.selectByIdAndTenantUser(10L, 1L, 7L)).thenReturn(existing);
        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(new ArrayList<>());
        when(objectMapper.writeValueAsString(any())).thenReturn("[{\"objectId\":\"9\"}]");

        service.updateContextEntities(10L, 1L, 7L, entitiesNode());

        verify(conversationMapper).selectByIdAndTenantUser(10L, 1L, 7L);   // 读了旧上下文
        verify(conversationMapper).update(isNull(), any());                // 再写回合并结果
    }

    @Test
    @DisplayName("超过 TTL 的旧实体被丢弃（防隔天残留误导代词消解）")
    void merge_dropsExpired() {
        List<Map<String, Object>> merged = service.mergeContextEntities(
                List.of(staleEntity("contract", "1", "昨天的合同")),
                List.of(entity("delivery", "9", "交付单X")));

        assertThat(merged).extracting(m -> m.get("title")).containsExactly("交付单X");
    }

    @Test
    @DisplayName("TTL 内的旧实体保留")
    void merge_keepsFresh() {
        List<Map<String, Object>> merged = service.mergeContextEntities(
                List.of(entity("contract", "1", "刚查的合同")),
                List.of(entity("delivery", "9", "交付单X")));

        assertThat(merged).extracting(m -> m.get("title"))
                .containsExactly("交付单X", "刚查的合同");
    }

    @Test
    @DisplayName("没有 at 字段的实体视为过期（老数据/外部写入，宁可丢不误用）")
    void merge_missingTimestamp_dropped() {
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("objectType", "contract");
        legacy.put("objectId", "1");
        legacy.put("title", "无时间戳的老数据");

        List<Map<String, Object>> merged = service.mergeContextEntities(
                List.of(legacy), List.of(entity("delivery", "9", "交付单X")));

        assertThat(merged).extracting(m -> m.get("title")).containsExactly("交付单X");
    }

    private static Map<String, Object> entity(String type, String id, String title) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("objectType", type);
        m.put("objectId", id);
        m.put("title", title);
        m.put("at", System.currentTimeMillis());   // 新鲜实体：TTL 内
        return m;
    }

    /** 构造一个已超过 TTL 的旧实体 */
    private static Map<String, Object> staleEntity(String type, String id, String title) {
        Map<String, Object> m = entity(type, id, title);
        m.put("at", System.currentTimeMillis() - 3 * 60 * 60 * 1000L);   // 3 小时前（TTL 2 小时）
        return m;
    }

    /** 构造一个含单个对象的 entities 数组节点 */
    private static com.fasterxml.jackson.databind.JsonNode entitiesNode() {
        try {
            return new ObjectMapper().readTree(
                    "[{\"objectType\":\"delivery\",\"objectId\":\"9\",\"title\":\"交付单X\"}]");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
