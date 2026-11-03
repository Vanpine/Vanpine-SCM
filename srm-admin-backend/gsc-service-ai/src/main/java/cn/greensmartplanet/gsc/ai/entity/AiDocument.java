package cn.greensmartplanet.gsc.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 知识库文档实体 — 上传到知识库的原始文档（对应 ai_documents 表）
 * <p>
 * 原始文件落 OSS，库里只存 object key（oss_key）；需要访问时按 key 现签短效 URL。
 * 由 Python 服务下载切分向量化后，回写 chunk_count 与 status（processing / ready / failed）。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_documents")
public class AiDocument {

    /** 主键（雪花） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 上传用户ID */
    private Long uploaderUserId;

    /** 原始文件名 */
    private String fileName;

    /** 文件类型：txt / md / pdf / docx / xlsx / xls */
    private String fileType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** OSS 对象路径（object key，形如 ai-doc/20260911/3f9a1c2d/手册.pdf）
     *  <p>访问时据此现签短效 URL，不持久化签名 URL（签名会过期且无法续期）。 */
    private String ossKey;

    /** 分块数量 */
    private Integer chunkCount;

    /** 状态：processing / ready / failed */
    private String status;

    /** 失败原因 */
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;
}
