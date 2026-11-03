package cn.greensmartplanet.gsc.ai.controller;

import cn.greensmartplanet.gsc.ai.dto.CreateConversationDTO;
import cn.greensmartplanet.gsc.ai.service.AiConversationService;
import cn.greensmartplanet.gsc.ai.vo.ConversationVO;
import cn.greensmartplanet.gsc.ai.vo.MessageVO;
import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.framework.audit.AuditLog;
import cn.greensmartplanet.gsc.framework.security.JwtUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 会话控制器
 * <p>
 * 会话列表 / 新建 / 删除 / 历史消息。写操作打 @AuditLog。
 *
 * @author gsc
 */
@RestController
@RequestMapping("/api/admin/ai/conversations")
@RequiredArgsConstructor
@Tag(name = "AI 对话机器人", description = "会话管理与知识库问答")
public class AiConversationController {

    private final AiConversationService conversationService;

    /** 查当前用户会话列表（update_time 倒序） */
    @Operation(summary = "会话列表")
    @GetMapping
    public R<List<ConversationVO>> list(@AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(conversationService.listConversations(user.getTenantId(), user.getUserId()));
    }

    /** 新建会话（title 可空，空则默认"新对话"） */
    @Operation(summary = "新建会话")
    @AuditLog(module = "AI 对话机器人", operation = "CREATE", desc = "新建会话", targetType = "AiConversation")
    @PostMapping
    public R<ConversationVO> create(@RequestBody(required = false) CreateConversationDTO dto,
                                    @AuthenticationPrincipal JwtUserDetails user) {
        String title = dto != null ? dto.getTitle() : null;
        return R.ok(conversationService.createConversation(user.getTenantId(), user.getUserId(), title));
    }

    /** 逻辑删除会话（校验租户 + 用户归属） */
    @Operation(summary = "删除会话")
    @AuditLog(module = "AI 对话机器人", operation = "DELETE", desc = "删除会话", targetType = "AiConversation")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id, @AuthenticationPrincipal JwtUserDetails user) {
        conversationService.deleteConversation(id, user.getTenantId(), user.getUserId());
        return R.ok();
    }

    /** 查某会话的历史消息（升序，含引用来源；校验租户 + 用户归属） */
    @Operation(summary = "会话历史消息")
    @GetMapping("/{id}/messages")
    public R<List<MessageVO>> messages(@PathVariable Long id, @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(conversationService.getMessages(id, user.getTenantId(), user.getUserId()));
    }
}
