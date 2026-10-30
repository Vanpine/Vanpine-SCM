package cn.greensmartplanet.gsc.ai.vo;

import lombok.Data;

/**
 * 引用来源 VO — 一条知识库引用（回答末尾展示）
 *
 * @author gsc
 */
@Data
public class CitationVO {

    /** 来源文档ID */
    private Long documentId;

    /** 源文件名 */
    private String fileName;

    /** 分块序号 */
    private Integer chunkIndex;

    /** 章节路径（如「合同 > 第三条 付款方式」），结构化切片后用于定位引用到具体章节 */
    private String sectionPath;

    /** 分块内容预览 */
    private String contentPreview;
}
