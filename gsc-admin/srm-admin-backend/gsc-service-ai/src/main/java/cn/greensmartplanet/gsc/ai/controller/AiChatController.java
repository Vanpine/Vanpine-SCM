package cn.greensmartplanet.gsc.ai.controller;

import cn.greensmartplanet.gsc.ai.dto.ChatCancelDTO;
import cn.greensmartplanet.gsc.ai.dto.ChatRequestDTO;
import cn.greensmartplanet.gsc.ai.service.AiChatImageService;
import cn.greensmartplanet.gsc.ai.service.AiChatService;
import cn.greensmartplanet.gsc.ai.vo.ChatImageVO;
import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.framework.audit.AuditLog;
import cn.greensmartplanet.gsc.framework.security.JwtUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 对话流式控制器
 * <p>
 * 返回 SseEmitter，业务在异步线程（@Async）里读 Python SSE 并逐 chunk 转发给前端。
 * 高频对话接口不记 @AuditLog。
 *
 * @author gsc
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/ai")
@RequiredArgsConstructor
@Tag(name = "AI 对话机器人", description = "会话管理与知识库问答")
public class AiChatController {

    private final AiChatService chatService;
    private final AiChatImageService chatImageService;

    /** 发起流式对话（SSE）：conversationId 可空，空则后端自动建会话 */
    @Operation(summary = "流式对话")
    @PostMapping("/chat")
    public SseEmitter chat(@Valid @RequestBody ChatRequestDTO dto,
                           @AuthenticationPrincipal JwtUserDetails user) {
        // 120s 超时，与 Python astream 超时 + 网关响应超时对齐
        SseEmitter emitter = new SseEmitter(120_000L);
        try {
            // 异步执行：controller 立即返回 emitter，Spring 提交响应后开始流式推送
            chatService.streamChat(dto, user.getTenantId(), user.getUserId(), emitter);
        } catch (TaskRejectedException e) {
            // 流式线程池满（queue=0 + AbortPolicy）：明确回「繁忙」。
            // 不能让任务落到请求线程内联跑——一个流最长 120s，几个就把 tomcat 线程占满。
            log.warn("流式线程池已满，拒绝新对话: tenantId={}, userId={}",
                    user.getTenantId(), user.getUserId());
            sendBusyAndComplete(emitter);
        }
        return emitter;
    }

    /**
     * 线程池满时给前端一个明确提示并收流（格式与 AiChatServiceImpl.sendEvent 一致）。
     *
     * @param emitter 已创建的 SSE 发射器
     */
    private void sendBusyAndComplete(SseEmitter emitter) {
        try {
            emitter.send("{\"type\":\"error\",\"message\":\"当前对话人数较多，请稍后重试\"}");
        } catch (Exception e) {
            log.warn("发送繁忙提示失败: {}", e.getMessage());
        }
        emitter.complete();
    }

    /**
     * 停止生成：显式收口，不依赖浏览器 abort 能否穿透网关。
     * 关页 / F5 也会打这条（keepalive），必须先于流结束落库。
     */
    @AuditLog(module = "AI对话", operation = "UPDATE", desc = "停止生成", targetType = "AiMessage")
    @Operation(summary = "停止生成")
    @PostMapping("/chat/cancel")
    public R<Void> cancel(@Valid @RequestBody ChatCancelDTO dto,
                          @AuthenticationPrincipal JwtUserDetails user) {
        chatService.cancelChat(dto, user.getTenantId(), user.getUserId());
        return R.ok();
    }

    /** 上传对话图片：返回 imageId 与短效预览地址，发送消息时用 imageIds 引用 */
    @AuditLog(module = "AI对话", operation = "CREATE", desc = "上传对话图片", targetType = "AiChatImage")
    @Operation(summary = "上传对话图片")
    @PostMapping("/chat/images")
    public R<ChatImageVO> uploadImage(@RequestParam("file") MultipartFile file,
                                      @AuthenticationPrincipal JwtUserDetails user) {
        return R.ok(chatImageService.upload(file, user.getTenantId(), user.getUserId()));
    }
}
