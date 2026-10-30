package cn.greensmartplanet.gsc.ai.service.impl;

import cn.greensmartplanet.gsc.ai.entity.AiConversation;
import cn.greensmartplanet.gsc.ai.entity.AiMessage;
import cn.greensmartplanet.gsc.ai.mapper.AiConversationMapper;
import cn.greensmartplanet.gsc.ai.mapper.AiMessageMapper;
import cn.greensmartplanet.gsc.ai.service.AiConversationService;
import cn.greensmartplanet.gsc.ai.service.AiChatImageService;
import cn.greensmartplanet.gsc.ai.vo.ChatImageVO;
import cn.greensmartplanet.gsc.ai.vo.CitationVO;
import cn.greensmartplanet.gsc.ai.vo.ConversationVO;
import cn.greensmartplanet.gsc.ai.vo.FeedbackStatsVO;
import cn.greensmartplanet.gsc.ai.vo.MessageVO;
import cn.greensmartplanet.gsc.ai.vo.ToolCallVO;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * AI 会话服务实现
 * <p>
 * 多租户隔离：所有查询经 mapper 手写 SQL 过滤 tenant_id；删除/详情先校验归属。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiConversationServiceImpl implements AiConversationService {

    /**
     * 会话默认标题（新建会话未带 title 时）
     */
    private static final String DEFAULT_TITLE = "新对话";
    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final AiChatImageService chatImageService;
    private final ObjectMapper objectMapper;

    /**
     * 查询当前用户会话列表。
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @return 会话 VO 列表（update_time 倒序）
     */
    @Override
    public List<ConversationVO> listConversations(Long tenantId, Long userId) {
        // 数据库层同时过滤 tenant_id + user_id，保证同租户不同用户互不可见
        return conversationMapper.selectByTenantUser(tenantId, userId).stream()
                .map(this::toVO)
                .toList();
    }

    /**
     * 新建会话并返回展示对象。
     *
     * @param tenantId 租户 ID
     * @param userId   归属用户 ID
     * @param title    标题（空则使用默认标题）
     * @return 新建后的会话 VO
     */
    @Override
    public ConversationVO createConversation(Long tenantId, Long userId, String title) {
        // 标题先去空格；无有效标题时使用默认值，避免前端展示空白
        AiConversation entity = AiConversation.builder()
                .tenantId(tenantId)
                .userId(userId)
                .title(title == null || title.isBlank() ? DEFAULT_TITLE : title.trim())
                .status("active")
                .build();
        // 雪花 ID 由 MyBatis-Plus 回填到 entity.id
        conversationMapper.insert(entity);
        return toVO(entity);
    }

    /**
     * 删除会话及其消息。
     *
     * @param id       会话 ID
     * @param tenantId 租户 ID
     * @param userId   归属用户 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteConversation(Long id, Long tenantId, Long userId) {
        // 归属校验：租户 + 用户，防越权删除他人会话
        AiConversation existing = conversationMapper.selectByIdAndTenantUser(id, tenantId, userId);
        if (existing == null) {
            throw new BizException(ErrorCode.AI_CONVERSATION_NOT_FOUND);
        }
        // 同事务：逻辑删会话 + 物理删其下全部消息（隐私要求不留残留）
        conversationMapper.deleteById(id);
        messageMapper.deleteByConversation(id, tenantId);
        // 事务由 @Transactional 保证：任一删除失败则整体回滚
    }

    /**
     * 查询会话历史消息。
     *
     * @param conversationId 会话 ID
     * @param tenantId       租户 ID
     * @param userId         归属用户 ID
     * @return 消息 VO 列表（create_time 升序）
     */
    @Override
    public List<MessageVO> getMessages(Long conversationId, Long tenantId, Long userId) {
        // 归属校验：先确认会话属于该租户 + 用户，防越权读取他人对话
        AiConversation existing = conversationMapper.selectByIdAndTenantUser(conversationId, tenantId, userId);
        if (existing == null) {
            throw new BizException(ErrorCode.AI_CONVERSATION_NOT_FOUND);
        }
        // 会话归属通过后再按 conversation_id + tenant_id 查消息，防止越权读取
        List<AiMessage> messages = messageMapper.selectByConversation(conversationId, tenantId);
        // 批量带出图片（一次查询 + 一次现签，消 N+1；无图片消息返回空 Map）
        Map<Long, List<ChatImageVO>> imageMap = chatImageService.listByMessageIds(
                messages.stream().map(AiMessage::getId).toList(), tenantId);
        return messages.stream()
                // 进行中的占位不回给前端，刷新时只看到已收尾的 complete/cancelled/error
                .filter(m -> !"streaming".equals(m.getStatus()))
                .map(m -> {
                    MessageVO vo = toVO(m);
                    vo.setImages(imageMap.getOrDefault(m.getId(), List.of()));
                    return vo;
                })
                .toList();
    }

    /**
     * 提交/覆盖一条消息反馈。
     * <p>
     * 只允许对 assistant 消息反馈；SQL 层带 tenant 过滤 + role 限制，更新 0 行即视为
     * 「消息不存在 / 非本租户 / 非 assistant」，统一抛 AI_MESSAGE_NOT_FOUND（不泄露存在性）。
     */
    @Override
    public void submitFeedback(Long messageId, Long tenantId, String feedback, String reason) {
        if (!"like".equals(feedback) && !"dislike".equals(feedback)) {
            throw new BizException(ErrorCode.AI_FEEDBACK_INVALID);
        }
        // 只有点踩才存原因；点赞时清空，避免改主意后残留旧原因
        String effectiveReason = "dislike".equals(feedback) ? reason : null;
        int updated = messageMapper.updateFeedback(messageId, tenantId, feedback, effectiveReason);
        if (updated == 0) {
            throw new BizException(ErrorCode.AI_MESSAGE_NOT_FOUND);
        }
        log.info("AI 消息反馈已记录: messageId={}, feedback={}, hasReason={}",
                messageId, feedback, effectiveReason != null);
    }

    /**
     * 反馈统计（按租户）。
     */
    @Override
    public FeedbackStatsVO feedbackStats(Long tenantId) {
        FeedbackStatsVO stats = messageMapper.selectFeedbackStats(tenantId);
        // 无消息时 SUM 返回 null，兜底为 0，前端不用判空
        if (stats == null) {
            stats = new FeedbackStatsVO();
        }
        stats.setLikeCount(nullToZero(stats.getLikeCount()));
        stats.setDislikeCount(nullToZero(stats.getDislikeCount()));
        stats.setFeedbackCount(nullToZero(stats.getFeedbackCount()));
        stats.setCancelledCount(nullToZero(stats.getCancelledCount()));
        stats.setAnswerCount(nullToZero(stats.getAnswerCount()));
        return stats;
    }

    /**
     * null → 0（SUM 无匹配行时返回 null）。
     */
    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 实体 → 会话 VO
     */
    private ConversationVO toVO(AiConversation entity) {
        ConversationVO vo = new ConversationVO();
        vo.setId(entity.getId());
        vo.setTitle(entity.getTitle());
        vo.setStatus(entity.getStatus());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    /**
     * 实体 → 消息 VO（解析 citations_json 为引用来源列表，带出反馈状态）
     */
    private MessageVO toVO(AiMessage entity) {
        MessageVO vo = new MessageVO();
        vo.setId(entity.getId());
        vo.setRole(entity.getRole());
        vo.setContent(entity.getContent());
        vo.setStatus(entity.getStatus());
        vo.setFeedback(entity.getFeedback());
        vo.setFeedbackReason(entity.getFeedbackReason());
        vo.setCreateTime(entity.getCreateTime());
        vo.setCitations(parseCitations(entity.getCitationsJson()));
        vo.setToolCalls(parseToolCalls(entity.getToolCallsJson()));
        return vo;
    }

    /**
     * 解析工具调用轨迹 JSON，异常/为空时返回空列表（不阻断消息展示）
     */
    private List<ToolCallVO> parseToolCalls(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ToolCallVO>>() {
            });
        } catch (Exception e) {
            log.warn("工具调用轨迹 JSON 解析失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 解析引用来源 JSON，异常/为空时返回空列表（不阻断消息展示）
     */
    private List<CitationVO> parseCitations(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<CitationVO>>() {
            });
        } catch (Exception e) {
            log.warn("引用来源 JSON 解析失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
