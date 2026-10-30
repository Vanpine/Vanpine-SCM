package cn.greensmartplanet.gsc.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 知识库父块实体（对应 ai_doc_parent_chunks 表）
 * <p>
 * parent-child 切分的父块：不参与向量检索，仅按 parent_key 回查，
 * 检索命中子块后取父块全文喂 LLM，保证「检索准 + 上下文全」。
 * 由 Python 解析后随 parse 响应回传，Java 落库。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_doc_parent_chunks")
public class AiDocParentChunk {

    /** 主键（雪花） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 所属文档ID（ai_documents.id） */
    private Long documentId;

    /** 文档内父块确定性 key（UUID5），子块 payload.parent_id 指向它 */
    private String parentKey;

    /** 父块全文（回填给 LLM） */
    private String content;

    /** 章节路径（JSON 数组字符串） */
    private String headingPath;

    /** 父块 token 估算数 */
    private Integer tokenCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;
}
