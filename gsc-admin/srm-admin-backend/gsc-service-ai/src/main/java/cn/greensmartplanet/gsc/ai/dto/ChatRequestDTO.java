package cn.greensmartplanet.gsc.ai.dto;

import lombok.Data;

import java.util.List;

/**
 * 流式对话请求体
 *
 * @author gsc
 */
@Data
public class ChatRequestDTO {

    /** 会话ID（可空：首次提问时后端自动建会话） */
    private Long conversationId;

    /**
     * 本次提问内容。
     * 纯图片提问时可为空，此时 imageIds 必须非空（校验在 Service 层，因二者互为补充）。
     */
    private String content;

    /** 本轮提问附带的图片ID（最多 4 张） */
    private List<String> imageIds;

    /**
     * 是否重新生成上一轮助手回答。
     * true 时必须带 conversationId：不重复插入 user 消息，覆盖上一轮 assistant。
     */
    private Boolean regenerate;
}
