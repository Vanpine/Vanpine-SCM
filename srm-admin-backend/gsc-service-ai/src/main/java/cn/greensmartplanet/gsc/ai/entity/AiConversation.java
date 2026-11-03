package cn.greensmartplanet.gsc.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 会话实体 — 一次对话机器人会话（对应 ai_conversations 表）
 * <p>
 * 会话归属租户 + 用户，title 为会话标题（首条消息前 20 字自动生成）。
 * status: active / archived。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_conversations")
public class AiConversation {

    /** 主键（雪花） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 创建该会话的用户ID */
    private Long userId;

    /** 会话标题 */
    private String title;

    /** 状态：active / archived */
    private String status;

    /**
     * 最近业务对象上下文（JSON 字符串，形如
     * [{"objectType":"contract","objectId":"123","title":"2026年度钢材采购合同","status":"signed"}]）。
     * <p>
     * 供 AI 指代消解用（用户追问「第一条」「刚才那个」「他」时恢复具体对象），
     * 仅内部理解，不展示给用户。空则无上下文。
     */
    private String contextEntities;

    /**
     * 早前对话摘要 — 滑动窗口之外的老对话压成的一段话。
     * <p>
     * 上下文超阈值时异步生成：把「旧摘要 + 新变老的消息」合并成一份新摘要。
     * 为空表示还没有摘要（会话还短）。见 summaryUpToMsgId。
     */
    private String summary;

    /**
     * 摘要已覆盖到哪条消息ID — 该消息及之前的都已进 summary，不再喂原文。
     * <p>
     * 没有它每次压缩都要从头压一遍，所以压缩后必须同步更新。
     */
    private Long summaryUpToMsgId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;
}
