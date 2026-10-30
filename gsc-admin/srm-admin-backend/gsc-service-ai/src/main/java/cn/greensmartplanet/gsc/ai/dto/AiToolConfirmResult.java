package cn.greensmartplanet.gsc.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 写操作确认回执 — 确认/取消落库后返回给前端的最终文案
 * <p>
 * finalText 是这条 assistant 气泡最终应显示的文字（确认=「已完成操作…」、取消=「已取消…」），
 * assistantMessageId 用于后端把流式阶段兜底的「已发起操作，请确认执行。」占位覆盖成最终文案。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiToolConfirmResult {

    /** 落库后的最终展示文案 */
    private String finalText;

    /** 本条 assistant 消息ID（覆盖占位用；直连验证等无前端消息的路径为 null） */
    private Long assistantMessageId;
}
