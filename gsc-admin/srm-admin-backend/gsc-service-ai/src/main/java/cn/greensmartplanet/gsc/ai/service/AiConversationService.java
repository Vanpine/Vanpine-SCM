package cn.greensmartplanet.gsc.ai.service;

import cn.greensmartplanet.gsc.ai.vo.ConversationVO;
import cn.greensmartplanet.gsc.ai.vo.FeedbackStatsVO;
import cn.greensmartplanet.gsc.ai.vo.MessageVO;

import java.util.List;

/**
 * AI 会话服务接口 — 会话列表 / 新建 / 删除 / 历史消息
 *
 * @author gsc
 */
public interface AiConversationService {

    /**
     * 查当前租户下某用户的会话列表（update_time 倒序）
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @return 会话 VO 列表（仅 active 且未删除）
     */
    List<ConversationVO> listConversations(Long tenantId, Long userId);

    /**
     * 新建会话（title 为空时给默认值），返回会话 VO
     *
     * @param tenantId 租户 ID
     * @param userId   归属用户 ID
     * @param title    会话标题（可空）
     * @return 新建后的会话 VO
     */
    ConversationVO createConversation(Long tenantId, Long userId, String title);

    /**
     * 逻辑删除会话（校验租户 + 用户归属，防越权）
     *
     * @param id       会话 ID
     * @param tenantId 租户 ID
     * @param userId   归属用户 ID
     * @throws cn.greensmartplanet.gsc.common.exception.BizException 会话不存在或不属于当前用户
     */
    void deleteConversation(Long id, Long tenantId, Long userId);

    /**
     * 查某会话的历史消息（升序，含引用来源解析；校验租户 + 用户归属）
     *
     * @param conversationId 会话 ID
     * @param tenantId       租户 ID
     * @param userId         归属用户 ID
     * @return 消息 VO 列表（按创建时间升序）
     * @throws cn.greensmartplanet.gsc.common.exception.BizException 会话不存在或不属于当前用户
     */
    List<MessageVO> getMessages(Long conversationId, Long tenantId, Long userId);

    /**
     * 提交/覆盖一条消息反馈（仅 assistant 消息可反馈，租户隔离防越权）。
     *
     * @param messageId 消息 ID
     * @param tenantId  租户 ID
     * @param feedback  like / dislike
     * @param reason    点踩原因（like 时忽略）
     * @throws cn.greensmartplanet.gsc.common.exception.BizException 消息不存在 / 非本租户 / 反馈类型非法
     */
    void submitFeedback(Long messageId, Long tenantId, String feedback, String reason);

    /**
     * 反馈统计（按租户）：点赞率、点踩数、中途停止数等，供质量看板。
     *
     * @param tenantId 租户 ID
     * @return 统计 VO
     */
    FeedbackStatsVO feedbackStats(Long tenantId);
}
