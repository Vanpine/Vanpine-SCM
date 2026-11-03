package cn.greensmartplanet.gsc.ai.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 停止生成请求体。
 * <p>
 * content 为前端已展示的半段正文；citations 为已推到界面的引用来源。
 * 后端按此落库 cancelled，避免异步线程随后把完整回答写成 complete。
 *
 * @author gsc
 */
@Data
public class ChatCancelDTO {

    /** 会话ID（雪花；前端以字符串传入，Jackson 转 Long） */
    private Long conversationId;

    /** 停止时界面上已展示的半段回答；空则不留空气泡 */
    private String content;

    /**
     * 是否正在重新生成。
     * true 时删除 streaming 占位、保留旧完整回答，与前端拉回历史一致。
     */
    private Boolean regenerate;

    /**
     * 前端已展示的引用来源。
     * 用 Map 承接，避免雪花 documentId 被当成 Number 丢精度；空则用占位行上已写入的 citations_json。
     */
    private List<Map<String, Object>> citations;
}
