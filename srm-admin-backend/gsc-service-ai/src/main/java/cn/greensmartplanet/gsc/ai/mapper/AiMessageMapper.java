package cn.greensmartplanet.gsc.ai.mapper;

import cn.greensmartplanet.gsc.ai.entity.AiMessage;
import cn.greensmartplanet.gsc.ai.vo.FeedbackStatsVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 消息 Mapper
 * <p>
 * 消息 append-only（无逻辑删除），按会话查历史，租户隔离。
 *
 * @author gsc
 */
public interface AiMessageMapper extends BaseMapper<AiMessage> {

    /**
     * 按会话查全部历史消息（create_time 升序，租户过滤）。
     *
     * @param conversationId 会话ID
     * @param tenantId       租户ID
     * @return 消息列表
     */
    List<AiMessage> selectByConversation(@Param("conversationId") Long conversationId,
                                         @Param("tenantId") Long tenantId);

    /**
     * 按会话查最近 N 条消息（用于组装 LLM 上下文）
     *
     * @param conversationId 会话ID
     * @param tenantId       租户ID
     * @param limit          条数上限
     * @return create_time 升序的最近消息
     */
    List<AiMessage> selectRecentByConversation(@Param("conversationId") Long conversationId,
                                               @Param("tenantId") Long tenantId,
                                               @Param("limit") int limit);

    /**
     * 按角色取该会话最新一条消息。
     * SQL：tenant_id + conversation_id + role，ORDER BY create_time DESC LIMIT 1。
     *
     * @param conversationId 会话ID
     * @param tenantId       租户ID
     * @param role           user / assistant
     * @return 最新一条，没有则 null
     */
    AiMessage selectLatestByRole(@Param("conversationId") Long conversationId,
                                 @Param("tenantId") Long tenantId,
                                 @Param("role") String role);

    /**
     * 物理删除会话下全部消息（删会话时同步清理，隐私要求不留残留）
     *
     * @param conversationId 会话ID
     * @param tenantId       租户ID
     * @return 删除行数
     */
    int deleteByConversation(@Param("conversationId") Long conversationId,
                             @Param("tenantId") Long tenantId);

    /**
     * 物理删除指定消息（本表无 is_deleted）。
     * <p>
     * ⚠️ 不带状态守卫，只用于「重生成时删上一轮已完成的 assistant」。
     * 删「生成中占位」请用 {@link #deleteStreamingByIdAndConversation}——否则流在
     * SELECT 与 DELETE 之间完成时，会把一条已写好的完整回答删掉。
     *
     * @param id             消息ID
     * @param conversationId 会话ID
     * @param tenantId       租户ID
     * @return 删除行数
     */
    int deleteByIdAndConversation(@Param("id") Long id,
                                  @Param("conversationId") Long conversationId,
                                  @Param("tenantId") Long tenantId);

    /**
     * 物理删除「仍在生成中」的占位（带 {@code status = 'streaming'} 守卫）。
     * <p>
     * 停止生成 / 流收尾时删空气泡用。加守卫的原因：先 SELECT 再 DELETE 不是原子的，
     * 若流恰好在此期间 CAS 成 complete 并写入正文，无守卫的 DELETE 会把已完成的回答删掉。
     * 守卫命中 0 行说明消息已被别的路径落库，调用方据此判断「不用删」。
     *
     * @param id             消息ID
     * @param conversationId 会话ID
     * @param tenantId       租户ID
     * @return 删除行数；0 表示该消息已不是 streaming（已被收尾）
     */
    int deleteStreamingByIdAndConversation(@Param("id") Long id,
                                           @Param("conversationId") Long conversationId,
                                           @Param("tenantId") Long tenantId);

    /**
     * 按主键 + 租户查一条（流式循环轮询是否已被停止接口改写）。
     *
     * @param id       消息ID
     * @param tenantId 租户ID
     * @return 消息，没有或不属于该租户则 null
     */
    AiMessage selectByIdAndTenant(@Param("id") Long id, @Param("tenantId") Long tenantId);

    /**
     * 取该会话当前 streaming 助手消息（同一会话同时只应有一条进行中的回答）。
     *
     * @param conversationId 会话ID
     * @param tenantId       租户ID
     * @return 进行中的 assistant，没有则 null
     */
    AiMessage selectLatestStreaming(@Param("conversationId") Long conversationId,
                                    @Param("tenantId") Long tenantId);

    /**
     * 清理会话里残留的 streaming 占位（上次异常退出未收尾）。
     *
     * @param conversationId 会话ID
     * @param tenantId       租户ID
     * @return 删除行数
     */
    int deleteStreamingByConversation(@Param("conversationId") Long conversationId,
                                      @Param("tenantId") Long tenantId);

    /**
     * 清掉超时未收尾的 streaming（进程挂掉残留），进行中的占位不动，避免踩另一页签。
     *
     * @param conversationId 会话ID
     * @param tenantId       租户ID
     * @param before         早于该时间的占位才删
     * @return 删除行数
     */
    int deleteStaleStreamingByConversation(@Param("conversationId") Long conversationId,
                                           @Param("tenantId") Long tenantId,
                                           @Param("before") LocalDateTime before);

    /**
     * 生成中途把已推到的引用来源写到占位行，停止时即使前端没带 citations 也能落库。
     *
     * @param id             消息ID
     * @param tenantId       租户ID
     * @param citationsJson  引用来源 JSON
     * @return 更新行数
     */
    int updateStreamingCitations(@Param("id") Long id,
                                 @Param("tenantId") Long tenantId,
                                 @Param("citationsJson") String citationsJson);

    /**
     * CAS 收尾 streaming：仅 status=streaming 时写成终态，防止停止接口与流结束互相覆盖。
     *
     * @param id             消息ID
     * @param conversationId 会话ID
     * @param tenantId       租户ID
     * @param content        正文
     * @param citationsJson  引用来源 JSON，可空
     * @param toolCallsJson  工具调用轨迹 JSON，可空
     * @param status         complete / cancelled / error
     * @return 更新行数，0 表示已被停止接口先落库
     */
    int finalizeStreaming(@Param("id") Long id,
                          @Param("conversationId") Long conversationId,
                          @Param("tenantId") Long tenantId,
                          @Param("content") String content,
                          @Param("citationsJson") String citationsJson,
                          @Param("toolCallsJson") String toolCallsJson,
                          @Param("status") String status);

    /**
     * 覆盖写占位气泡文案（写操作确认/取消落库后，把「已发起操作」占位改成最终文案）。
     * 只按 id+tenant 定向更新单条，不影响其他消息。
     *
     * @param id       消息ID
     * @param tenantId 租户ID（防越权覆盖他人消息）
     * @param content  最终文案
     * @return 更新行数
     */
    int updateContentById(@Param("id") Long id,
                          @Param("tenantId") Long tenantId,
                          @Param("content") String content);

    /**
     * 写用户反馈（仅 assistant 消息，tenant 过滤防越权；可覆盖已有反馈）。
     *
     * @param id             消息ID
     * @param tenantId       租户ID
     * @param feedback       like / dislike
     * @param feedbackReason 点踩原因（like 时传 null）
     * @return 更新行数（0 表示消息不存在/非本租户/非 assistant）
     */
    int updateFeedback(@Param("id") Long id,
                       @Param("tenantId") Long tenantId,
                       @Param("feedback") String feedback,
                       @Param("feedbackReason") String feedbackReason);

    /**
     * 反馈统计（按租户）：显式反馈 + 行为信号（cancelled 计数），一次查询。
     *
     * @param tenantId 租户ID
     * @return 统计 VO（无数据时各计数字段为 null，Service 兜底为 0）
     */
    FeedbackStatsVO selectFeedbackStats(@Param("tenantId") Long tenantId);

    /**
     * 取可压缩的老消息（升序）：摘要覆盖点之后、滑动窗口起点之前。
     * <p>
     * 只取 user + 完整 assistant（半截的 cancelled/error 回答不压，避免把中断内容固化进摘要）；
     * afterId 为空表示首次压缩（摘要还没覆盖任何消息）。
     * <p>
     * 上界用 beforeId（本轮滑动窗口里最早那条的 id）而不是固定条数：窗口是按 token 预算浮动的，
     * 固定条数与窗口对不上——预算大时窗口内的消息会被重复压进摘要。
     *
     * @param conversationId 会话ID
     * @param tenantId       租户ID
     * @param afterId        摘要已覆盖到的消息ID，可为 null（从头压）
     * @param beforeId       滑动窗口起点消息ID——窗口内的原文不参与压缩，可为 null（窗口为空）
     * @param limit          单次最多取多少条（调用方按压缩批次上限传）
     * @return 待压缩消息（create_time 升序，最老的在前）
     */
    List<AiMessage> selectCompactable(@Param("conversationId") Long conversationId,
                                      @Param("tenantId") Long tenantId,
                                      @Param("afterId") Long afterId,
                                      @Param("beforeId") Long beforeId,
                                      @Param("limit") int limit);
}
