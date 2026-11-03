package cn.greensmartplanet.gsc.ai.controller;

import cn.greensmartplanet.gsc.ai.dto.FeedbackRequestDTO;
import cn.greensmartplanet.gsc.ai.service.AiConversationService;
import cn.greensmartplanet.gsc.ai.vo.FeedbackStatsVO;
import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.framework.audit.AuditLog;
import cn.greensmartplanet.gsc.framework.security.JwtUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 消息反馈控制器 — 👍/👎 收集与统计
 * <p>
 * 反馈是回答质量的唯一回流信号：显式（点赞/点踩）+ 隐式（status=cancelled 中途停止）。
 * 租户隔离由 Service 层 SQL 保证。
 *
 * @author gsc
 */
@RestController
@RequestMapping("/api/admin/ai/feedback")
@RequiredArgsConstructor
@Tag(name = "AI 消息反馈", description = "回答质量反馈与统计")
public class AiFeedbackController {

    private final AiConversationService conversationService;

    /** 提交消息反馈（👍/👎 + 可选原因；仅 assistant 消息，重复提交即覆盖） */
    @Operation(summary = "提交消息反馈")
    @AuditLog(module = "AI 对话机器人", operation = "UPDATE", desc = "提交消息反馈", targetType = "AiMessage")
    @PostMapping("/messages/{messageId}")
    public R<Void> feedback(@PathVariable Long messageId,
                            @Valid @RequestBody FeedbackRequestDTO dto,
                            @AuthenticationPrincipal JwtUserDetails user) {
        conversationService.submitFeedback(messageId, user.getTenantId(), dto.getFeedback(), dto.getReason());
        return R.ok();
    }

    /** 反馈统计（按租户：点赞数/点踩数/中途停止数，供质量看板） */
    @Operation(summary = "反馈统计")
    @GetMapping("/stats")
    public R<FeedbackStatsVO> stats(@AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(conversationService.feedbackStats(user.getTenantId()));
    }
}
