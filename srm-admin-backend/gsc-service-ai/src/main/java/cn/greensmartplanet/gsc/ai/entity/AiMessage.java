package cn.greensmartplanet.gsc.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 消息实体 — 会话中的一条消息（对应 ai_messages 表）
 * <p>
 * role: user / assistant / tool；assistant 消息可携带引用来源（citations_json）
 * 与工具调用轨迹（tool_calls_json，二期启用）。
 * <p>
 * 注意：本表无 is_deleted / update_time，消息 append-only，不做逻辑删除。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_messages")
public class AiMessage {

    /** 主键（雪花） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属会话ID */
    private Long conversationId;

    /** 租户ID */
    private Long tenantId;

    /** 用户ID（assistant/tool 消息为 null） */
    private Long userId;

    /** 角色：user / assistant / tool */
    private String role;

    /** 消息内容（富文本/markdown） */
    private String content;

    /** 引用来源 JSON（assistant 消息，存储 JSON 字符串，读写时序列化） */
    private String citationsJson;

    /** 工具调用轨迹 JSON（assistant 消息，二期启用） */
    private String toolCallsJson;

    /** 状态：streaming / complete / cancelled / error */
    private String status;

    /** 用户反馈：like / dislike，null 表示未反馈（仅 assistant 消息可反馈） */
    private String feedback;

    /** 点踩原因（答错/漏召/编造/答非所问等，仅 dislike 时有值） */
    private String feedbackReason;

    /** 反馈时间 */
    private LocalDateTime feedbackTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
