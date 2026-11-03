package cn.greensmartplanet.gsc.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 对话图片资产实体 — 对应 ai_chat_images 表
 * <p>
 * 只落 OSS 对象路径（oss_key），不落签名 URL：签名 10 分钟即失效，
 * 展示/送模型时按 oss_key 现签，与 ai_documents 同策略。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_chat_images")
public class AiChatImage {

    /** 主键（雪花） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 上传用户ID */
    private Long uploaderUserId;

    /** OSS 对象路径（不存签名 URL） */
    private String ossKey;

    /** 原始文件名（仅展示用，不参与 OSS key 拼接） */
    private String fileName;

    /** 文件类型（服务端按文件头判定，不信任前端声明） */
    private String contentType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 状态：ready */
    private String status;

    /**
     * 图片内容描述：首次作为历史图进入上下文时，由多模态模型生成。
     * <p>
     * 为什么需要它：图片 token 成本是同体积文字的 10 倍（一张 ≈ 800~1600 token，
     * 而历史窗口预算只有 3000），所以历史轮次一律不带原图。但用户追问「刚才那张图里的
     * 合同号」时模型看不到——描述把图片内容压成一段文字（≈100 token），让历史轮次仍可指代。
     * <p>
     * 三态语义：{@code null}=尚未生成（下次使用时会补生成）；空串=已生成但无有效内容
     * （不再重试，防坏图反复烧 token）；非空=正常描述。
     */
    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;
}
