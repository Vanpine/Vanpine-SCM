package cn.greensmartplanet.gsc.platform.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 尽调附件材料（多值）— 一个尽调可上传多个附件，每行一个。
 * <p>
 * 历史设计把多个附件用逗号拼进 due_diligence.attachment_urls 单列，
 * 既无法单独管理（增删改某一项要重写整串），也没法存每项的文件名。
 * 拆表后每行独立存 oss_key + file_name，语义清晰。
 * <p>
 * 注意：本表无 is_deleted / update_time —— 附件增删是物理操作（整体替换式保存）。
 *
 * <p>⚠️ 迁移期桥接（微服务拆分）：本文件归属供应商域，platform 内为过渡副本。</p>
 *
 * @author gsc
 */
@Data
@TableName("due_diligence_attachment")
public class DueDiligenceAttachment {

    /** 主键（雪花） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联入驻申请ID（与 due_diligence.application_id 对应） */
    private Long applicationId;

    /** OSS 对象路径（访问时现签短效 URL） */
    private String ossKey;

    /** 原始文件名（展示用，避免显示 OSS 里的 UUID 路径） */
    private String fileName;

    /** 排序（保持用户上传顺序） */
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
