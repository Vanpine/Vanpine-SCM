package cn.greensmartplanet.gsc.ai.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 工具调用审计实体（对应 ai_tool_calls 表）
 * <p>
 * 与 ai_messages.tool_calls_json 分工：本表是「审计」，可聚合统计（最慢工具、失败率、
 * 敏感工具调用人）且独立留存；json 列是「回显」，随消息同生命周期供前端展示轨迹。
 * <p>
 * 只落库、不参与业务查询，故无 update_time / is_deleted（一次性追加写，不做逻辑删）。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_tool_calls")
public class AiToolCall {

    /** 主键（雪花） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属会话ID（ai_conversations.id） */
    private Long conversationId;

    /** 本轮 assistant 消息ID（ai_messages.id） */
    private Long messageId;

    /** 租户ID */
    private Long tenantId;

    /** 发起用户ID */
    private Long userId;

    /** 工具名（如 search_suppliers） */
    private String toolName;

    /** 调用参数（模型生成的 arguments，JSON 字符串） */
    private String argumentsJson;

    /** 状态：success / failed */
    private String status;

    /** 失败原因（成功时为 null） */
    private String errorMessage;

    /** 耗时（毫秒） */
    private Integer costTimeMs;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
