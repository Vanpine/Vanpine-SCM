package cn.greensmartplanet.gsc.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 消息-图片关联实体 — 对应 ai_message_images 表
 * <p>
 * 消息与图片资产多对多，支持"重新生成"时按原消息回查图片重新现签。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_message_images")
public class AiMessageImage {

    /** 主键（雪花） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 消息ID（ai_messages.id） */
    private Long messageId;

    /** 图片ID（ai_chat_images.id） */
    private Long imageId;

    /** 租户ID */
    private Long tenantId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;
}
