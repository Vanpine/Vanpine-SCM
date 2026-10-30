package cn.greensmartplanet.gsc.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AI 工具二次确认请求体 — 前端在确认按钮点击后提交
 * <p>
 * 只带 proposalId：真正的操作内容存在 Redis 提案里，由后端取出后按归属校验再落库，
 * 前端无法篡改待执行的操作内容（防中间人改备注/改目标）。
 *
 * @author gsc
 */
@Data
public class AiToolConfirmRequest {

    /** 提案ID（Java 生成，前端从 confirm_required 事件拿到后原样回传） */
    @NotBlank(message = "提案ID不能为空")
    private String proposalId;
}
