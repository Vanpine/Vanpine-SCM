package cn.greensmartplanet.gsc.ai.vo;

import lombok.Data;

/**
 * 对话图片 VO — 上传接口返回 + 历史消息回显
 *
 * @author gsc
 */
@Data
public class ChatImageVO {

    /** 图片ID（雪花，字符串化避免 JS 精度丢失） */
    private String id;

    /** 原始文件名（展示用） */
    private String fileName;

    /** 文件类型 */
    private String contentType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 短效预览地址（现签，不落库） */
    private String previewUrl;
}
