package cn.greensmartplanet.gsc.ai.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档 VO — 文档列表展示
 *
 * @author gsc
 */
@Data
public class DocumentVO {

    /** 文档ID */
    private Long id;

    /** 原始文件名 */
    private String fileName;

    /** 文件类型：txt / md / pdf / docx / xlsx / xls */
    private String fileType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 分块数量 */
    private Integer chunkCount;

    /** 状态：processing / ready / failed */
    private String status;

    /** 失败原因（failed 时） */
    private String errorMessage;

    /** 创建时间 */
    private LocalDateTime createTime;
}
