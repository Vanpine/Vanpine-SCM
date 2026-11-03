package cn.greensmartplanet.gsc.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具结果中的业务对象 — 随工具响应回传 Python，最终写入 ai_conversations.context_entities
 * <p>
 * Python 把本对象原样放进 tool_result 事件，Java 侧 AiChatServiceImpl.updateContextEntities
 * 按 conversationId 覆盖落库；下一轮用户说「它 / 第一条」时，Python 的 query_rewriter
 * 用 title 把指代还原成具体业务对象，避免模型靠历史文本猜错。
 * <p>
 * 字段只保留下游真正消费的三项（updateContextEntities 只取这三项，
 * query_rewriter 用 objectType 兜中文名、用 title 指称），多余字段会在落库时被丢弃。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextEntityDTO {

    /** 对象类型：supplier / contract / inquiry / delivery（对应 query_rewriter 的中文名映射） */
    private String objectType;

    /** 业务主键（雪花ID转字符串，防 JSON 精度丢失） */
    private String objectId;

    /** 展示名：供应商=企业名，合同/询盘=标题，交付单=交付单号 */
    private String title;
}
