package cn.greensmartplanet.gsc.ai.service;

import cn.greensmartplanet.gsc.ai.dto.ChatCancelDTO;
import cn.greensmartplanet.gsc.ai.dto.ChatRequestDTO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 对话流式服务接口 — 流式代理 Python RAG 服务
 *
 * @author gsc
 */
public interface AiChatService {

    /**
     * 发起流式对话：落库用户消息 → 调 Python 流式生成 → 逐 chunk 转发 SseEmitter → 落库助手消息。
     * regenerate=true 时不重复插入 user，覆盖上一轮 assistant。
     *
     * @param dto      前端请求（conversationId + content）
     * @param tenantId 当前租户
     * @param userId   当前用户
     * @param emitter  SSE 输出（由 Controller 创建并返回给前端）
     * @throws IllegalArgumentException 参数校验失败时由框架统一处理
     */
    void streamChat(ChatRequestDTO dto, Long tenantId, Long userId, SseEmitter emitter);

    /**
     * 停止生成：把进行中的 streaming 占位收成 cancelled（或重生成时删掉占位）。
     * 流结束落库用 CAS，已 cancelled 的不会再被写成 complete。
     *
     * @param dto      会话ID + 半段正文 + 是否重生成 + 已展示的引用来源
     * @param tenantId 当前租户
     * @param userId   当前用户
     */
    void cancelChat(ChatCancelDTO dto, Long tenantId, Long userId);

    /**
     * 写操作确认/取消落库后，把本条 assistant 气泡的「已发起操作，请确认执行。」占位
     * 覆盖成最终文案。assistantMessageId 为空时跳过（直连验证等无前端消息的路径）。
     *
     * @param assistantMessageId 流式阶段落库的 assistant 消息ID
     * @param tenantId           当前租户
     * @param finalText          最终展示文案
     */
    void updateConfirmResult(Long assistantMessageId, Long tenantId, String finalText);
}
